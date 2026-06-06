# Spec — Auth + Stripe Webhook for SpecBuild Backend

## Goal
Add real cross-device identity to the SpecBuild backend so a person's paid
status follows them between laptops, browsers, and incognito windows. Today
the frontend stores `plan` in `localStorage` and the backend tracks quota by
an anonymous `clientId` UUID. If a user pays on laptop A and signs into the
site on laptop B, the backend has no way to know it's the same person.

This spec adds:
1. Two sign-in methods: **Google OAuth** and **email magic links**.
2. A signed HttpOnly session cookie shared between the static frontend on
   `https://specthinker.github.io` (or wherever it's hosted) and the backend
   on `https://specbuild-backend.onrender.com`.
3. A **Stripe webhook** so the backend learns who actually paid the moment
   Stripe says so, instead of trusting `#success` in the URL.
4. **Quota tied to user identity** when signed in, with seamless fallback to
   the existing anonymous `clientId` flow when not.

The frontend half is already implemented in `specbuild-web`
(commits introduce `src/lib/auth.tsx`, `src/components/SignInModal.tsx`, and
edits to `src/lib/api.ts` and `src/App.tsx`). The backend needs to expose
the endpoints below and wire up the underlying services.

## Non-goals (for v1)
- No password sign-in. Two methods only: Google OAuth and email magic link.
- No account merging UI. If a user signs in once with Google and later with
  the same email via magic link, we merge them server-side on the email
  match (Google profile email == magic-link email).
- No two-factor. No password reset flow (no passwords).
- No org / team accounts. One user, one plan.
- No Stripe Customer Portal integration for v1. Cancel/manage continues to
  happen on Stripe's hosted pages via the existing customer email link.

## Tech additions (delta from `SPEC-backend.md`)
- `org.springframework.boot:spring-boot-starter-mail` **only if** you choose
  SMTP. Recommended: use **Resend** via plain HTTP from the JDK HttpClient,
  no extra dep. Their free tier (3k emails/month) is plenty for v1.
- `com.google.api-client:google-api-client` is **not** needed. OAuth code
  exchange is two HTTP calls; do them with the JDK HttpClient to avoid
  pulling in a 10MB transitive tree.
- `com.stripe:stripe-java:25.x` for verifying webhook signatures. (Don't
  hand-roll HMAC verification when Stripe's lib does it correctly.)

## Frontend contract — what the frontend will call

All endpoints live under `/api/v1`. All session-bearing requests include
`credentials: 'include'` so the browser sends/receives the session cookie
cross-origin. CORS config below must reflect that.

### `GET /api/v1/auth/me`
Returns the current signed-in user, or 401 if there is no valid session.

**Success (200):**
```json
{
  "userId": "usr_8f3a91c4d2e7",
  "email": "alex@example.com",
  "plan": "basic",
  "isAnonymous": false,
  "quota": { "used": 4, "limit": 30, "resetsAtEpochMillis": 1717545600000 }
}
```

**No session (401):**
```json
{ "error": "no_session", "message": "Not signed in." }
```

Notes:
- `plan` ∈ `"free" | "basic" | "pro" | "lifetime"`.
- `isAnonymous` is reserved for a future flow where the backend issues a
  session for unauthenticated users too. For now it'll always be `false`
  when this endpoint returns 200.
- `quota` reflects the **user's** quota when signed in. If you'd rather
  always fetch quota from `POST /llm/quota`, return `null` here — the
  frontend handles both.

### `POST /api/v1/auth/email/request`
Sends a magic sign-in link to the given email. Always returns 202, even if
the email doesn't match any existing user — this prevents email enumeration.

**Request:**
```json
{
  "email": "alex@example.com",
  "redirect": "https://specthinker.github.io/"
}
```

**Response (202):** `{}` (empty body)

**Server-side flow:**
1. Validate `email` format. If invalid, still return 202 (silent no-op).
2. Validate `redirect` against an **allowlist** of origins (read from env
   var `ALLOWED_REDIRECT_ORIGINS`, comma-separated). If not allowed,
   substitute the default frontend URL. Never honor an arbitrary redirect.
3. Generate `token = base64url(secureRandom(32))`. Store in
   `magic_link_tokens`:
   - `token_hash = sha256(token)` (never store the raw token)
   - `email = <normalized email>`
   - `redirect_url = <validated redirect>`
   - `expires_at = now + 15 minutes`
   - `used_at = null`
4. Email the user a link to
   `${BACKEND_URL}/api/v1/auth/email/verify?token=${token}`.
5. Use the email template in **Email template** below.

### `GET /api/v1/auth/email/verify?token=...`
Consumes a magic-link token, sets the session cookie, and 302-redirects to
the token's stored `redirect_url` with `?signed_in=1` appended.

**Flow:**
1. Look up the token by `sha256(token)`.
2. Reject (302 to `redirect_url + "?signed_in=0&error=invalid_token"`) if:
   - Not found
   - `expires_at < now`
   - `used_at != null`
3. Mark `used_at = now` in the same transaction that creates/loads the user.
4. Upsert user by email:
   - If a user with this email exists, use them.
   - Else create `users` row with `id = "usr_" + hex(8)`, `email = <normalized>`,
     `plan = "free"` (will be upgraded by the Stripe webhook later).
5. Issue a session (see **Session cookie** below) and set it.
6. 302 to `${redirect_url}?signed_in=1` (preserve any existing query params).

### `GET /api/v1/auth/google/start?redirect=...`
Kicks off Google OAuth. Stores a short-lived state record server-side
(don't trust client to round-trip state), then 302s to Google's auth URL.

**Flow:**
1. Validate `redirect` against `ALLOWED_REDIRECT_ORIGINS` (same as above).
2. Generate `state = base64url(secureRandom(16))` and `pkce_verifier`,
   `pkce_challenge`.
3. Insert into `oauth_state_tokens`:
   - `state_hash = sha256(state)`
   - `pkce_verifier`
   - `redirect_url`
   - `expires_at = now + 10 minutes`
4. 302 to
   `https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${BACKEND_URL}/api/v1/auth/google/callback&scope=openid%20email%20profile&state=${state}&code_challenge=${pkce_challenge}&code_challenge_method=S256&prompt=select_account`

### `GET /api/v1/auth/google/callback?code=...&state=...`
Completes Google OAuth, sets the session cookie, redirects to the original
`redirect_url + "?signed_in=1"`.

**Flow:**
1. Look up `state` by `sha256(state)`. Reject if missing/expired/used.
2. POST to `https://oauth2.googleapis.com/token` with:
   - `code`, `client_id`, `client_secret`, `redirect_uri`,
     `code_verifier`, `grant_type=authorization_code`
3. Decode the returned `id_token` (it's a JWT). For v1, you may trust the
   signature without verification **only because** we're talking to Google
   directly over TLS and the code came from a state we issued. Production
   should verify against Google's JWKS; defer to v1.1 if time-boxed.
4. Extract `sub` (Google user ID), `email`, `email_verified`. Reject if
   `email_verified != true`.
5. Upsert user:
   - First try lookup by `oauth_accounts.provider='google' AND
     provider_subject = sub`.
   - If not found, look up by `users.email = <email>` and link the Google
     account to that user (insert into `oauth_accounts`).
   - If still not found, create a new `users` row + `oauth_accounts` row.
6. Issue session, 302 to `${redirect_url}?signed_in=1`.

### `POST /api/v1/auth/logout`
Clears the session.

**Response (204):** no body. Always returns 204 even if no session.

**Side effects:**
- Deletes the session row from `sessions`.
- Sets `Set-Cookie: session=; Max-Age=0; ...` to clear the cookie.

### `POST /api/v1/stripe/webhook`
Receives Stripe events. The single event we care about for v1 is
`checkout.session.completed`. (You may also want to handle
`customer.subscription.deleted` to downgrade, but it's optional for v1.)

**Verification:**
Use Stripe's library:
```kotlin
val event = Webhook.constructEvent(rawBody, signatureHeader, STRIPE_WEBHOOK_SECRET)
```
Reject with 400 if verification fails. Stripe will retry; that's fine.

**Handler for `checkout.session.completed`:**
1. Pull `session.client_reference_id` (the frontend sends our `userId`
   here when the user is signed in).
2. Pull `session.customer_details.email` as fallback.
3. Resolve the user:
   - If `client_reference_id` matches a `users.id`, use that user.
   - Else look up by email. Create the user if missing — this covers
     "user paid before signing in" so the next time they sign in with that
     email, they inherit the paid plan.
4. Determine the plan from `session.amount_total` or
   `session.line_items[0].price.id`. Hardcode the price-ID → plan map in
   `Quotas.kt`:
   ```kotlin
   val PRICE_TO_PLAN = mapOf(
       "price_1ABCxyz..." to "basic",   // $5/mo
       "price_1DEFxyz..." to "pro",     // $15/mo (when added)
       "price_1GHIxyz..." to "lifetime" // $100 one-off (when added)
   )
   ```
5. Update `users`: set `plan = <new>`, `plan_set_at = now`,
   `stripe_customer_id = session.customer`.
6. **Reset their quota window** so they immediately get the upgraded limit:
   `period_start = now`, `specs_used = 0`, `polish_used = 0`.
7. Return 200 `{ "received": true }`.

**Idempotency:** Stripe may deliver the same event twice. Store
`processed_stripe_events(event_id)` and short-circuit on duplicate IDs.

## Session cookie

- **Name:** `session`
- **Value:** `${sessionId}.${hmacSha256(SESSION_SECRET, sessionId)}` (URL-safe b64)
- **Storage:** `sessions` table — see schema below. Cookie carries only the
  ID; the user lookup is server-side.
- **Attributes:**
  ```
  Set-Cookie: session=...; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=31536000
  ```
  - `SameSite=None` is **required** because the frontend and backend are on
    different origins (`github.io` vs `onrender.com`).
  - `SameSite=None` requires `Secure`, which requires HTTPS. Render
    provides HTTPS by default; the frontend must also be HTTPS. Local dev
    workaround: set `COOKIE_SAMESITE=Lax` and `COOKIE_SECURE=false` via env.
- **Tampering:** verify the HMAC on every request. Mismatch → treat as no
  session, do not auto-clear (lets users debug).

## CORS

Update CORS config so credentialed cross-origin requests work:

```kotlin
@Bean
fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Retry-After", "Resets-At")
            .allowCredentials(true)
            .maxAge(3600)
    }
}
```

`allowedOrigins` comes from `ALLOWED_ORIGINS` env var, comma-separated.
**Do not** use `*` — it's incompatible with `allowCredentials=true`.

## Database schema additions

Append to `schema.sql`:

```sql
-- Replace the v1 `users` table from SPEC-backend.md with this extended one.
-- (Add columns via ALTER if you've already deployed v1.)
CREATE TABLE IF NOT EXISTS users (
    id                   TEXT PRIMARY KEY,           -- "usr_" + hex(8)
    email                TEXT UNIQUE,                -- nullable: anon-only users have none
    plan                 TEXT NOT NULL DEFAULT 'free',
    plan_set_at          TEXT NOT NULL,
    period_start         TEXT NOT NULL,
    specs_used           INTEGER NOT NULL DEFAULT 0,
    polish_used          INTEGER NOT NULL DEFAULT 0,
    stripe_customer_id   TEXT UNIQUE,
    created_at           TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_stripe ON users(stripe_customer_id);

CREATE TABLE IF NOT EXISTS oauth_accounts (
    provider           TEXT NOT NULL,                -- 'google'
    provider_subject   TEXT NOT NULL,                -- Google `sub`
    user_id            TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at         TEXT NOT NULL,
    PRIMARY KEY (provider, provider_subject)
);

CREATE INDEX IF NOT EXISTS idx_oauth_user ON oauth_accounts(user_id);

CREATE TABLE IF NOT EXISTS sessions (
    id            TEXT PRIMARY KEY,                  -- random
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TEXT NOT NULL,
    last_seen_at  TEXT NOT NULL,
    expires_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_exp  ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS magic_link_tokens (
    token_hash    TEXT PRIMARY KEY,                  -- sha256(token), hex
    email         TEXT NOT NULL,
    redirect_url  TEXT NOT NULL,
    expires_at    TEXT NOT NULL,
    used_at       TEXT
);

CREATE INDEX IF NOT EXISTS idx_magic_exp ON magic_link_tokens(expires_at);

CREATE TABLE IF NOT EXISTS oauth_state_tokens (
    state_hash     TEXT PRIMARY KEY,                 -- sha256(state), hex
    pkce_verifier  TEXT NOT NULL,
    redirect_url   TEXT NOT NULL,
    expires_at     TEXT NOT NULL,
    used_at        TEXT
);

CREATE INDEX IF NOT EXISTS idx_state_exp ON oauth_state_tokens(expires_at);

CREATE TABLE IF NOT EXISTS processed_stripe_events (
    event_id    TEXT PRIMARY KEY,
    received_at TEXT NOT NULL
);
```

Background cleanup job (every hour, or lazy-on-write):
- `DELETE FROM magic_link_tokens WHERE expires_at < now();`
- `DELETE FROM oauth_state_tokens WHERE expires_at < now();`
- `DELETE FROM sessions WHERE expires_at < now();`
- `DELETE FROM processed_stripe_events WHERE received_at < now() - 30 days;`

## Quota tying (signed-in vs anonymous)

`POST /api/v1/llm/polish` and `POST /api/v1/llm/quota` already exist. The
frontend keeps sending `clientId` in the body. You should:

1. **If a valid session cookie is present**, use the session's `user.id` as
   the quota subject. Ignore the `clientId` in the body for counting
   purposes (but log it for debugging).
2. **If no session**, fall back to the existing `clientId`-keyed quota.
3. Anonymous quotas can keep using a separate table (`anonymous_quota` or
   reuse `users` with `email = NULL`). Either works; keep what's already
   in place.

When an anonymous user signs in for the first time, **merge** their
anonymous spec/polish counts into their new user row:

```kotlin
@Transactional
fun mergeAnonymousIntoUser(clientId: String, userId: String) {
    val anon = anonymousQuotaRepo.findByClientId(clientId) ?: return
    val user = userRepo.findById(userId).orElseThrow()
    user.specsUsed = max(user.specsUsed, anon.specsUsed)
    user.polishUsed = max(user.polishUsed, anon.polishUsed)
    userRepo.save(user)
    anonymousQuotaRepo.delete(anon)
}
```

Call this from `verifyEmailToken` and `googleCallback` after issuing the
session. The frontend sends the `clientId` cookie/value already; if it's
in the magic-link request body, persist it on the token row and read it
back here.

(Frontend tip: when sending `POST /auth/email/request`, also include the
current `clientId` so the backend can stash it on the token row and merge
on verify. The frontend can be updated later; not required for the
endpoint to work.)

## Email sender (Resend, recommended)

Sign up at resend.com, create an API key, then:

```kotlin
class ResendMailer(private val apiKey: String, private val from: String) {
    fun sendMagicLink(to: String, link: String) {
        val body = """
          {
            "from": "$from",
            "to": ["$to"],
            "subject": "Your Spec Builder sign-in link",
            "html": "<p>Click to sign in to Spec Builder:</p>
                     <p><a href=\"$link\">Sign in</a></p>
                     <p>This link expires in 15 minutes. If you didn't ask
                     for it, ignore this email.</p>",
            "text": "Sign in to Spec Builder: $link\\n\\nExpires in 15 minutes."
          }
        """.trimIndent()
        val res = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build(),
            BodyHandlers.ofString()
        )
        if (res.statusCode() !in 200..299) {
            throw RuntimeException("Resend failed: ${res.statusCode()} ${res.body()}")
        }
    }
}
```

For dev without Resend: log the link to stdout instead. Wire it behind a
`MailService` interface so you can swap implementations.

## New environment variables

```bash
# Required
SESSION_SECRET=<32+ byte hex from `openssl rand -hex 32`>
ALLOWED_ORIGINS=https://specthinker.github.io,http://localhost:5173
ALLOWED_REDIRECT_ORIGINS=https://specthinker.github.io,http://localhost:5173
BACKEND_URL=https://specbuild-backend.onrender.com  # used to build callback URLs

# Google OAuth (create at https://console.cloud.google.com/apis/credentials)
GOOGLE_CLIENT_ID=<...>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<...>

# Email (Resend)
RESEND_API_KEY=re_...
MAIL_FROM="Spec Builder <noreply@your-verified-domain.com>"

# Stripe webhook (from Stripe dashboard → Webhooks)
STRIPE_WEBHOOK_SECRET=whsec_...

# Cookie config (defaults for production; override for local dev)
COOKIE_SAMESITE=None       # set to "Lax" for local dev over http
COOKIE_SECURE=true         # set to "false" for local dev over http
```

Set the redirect URI in the Google Cloud Console to:
`https://specbuild-backend.onrender.com/api/v1/auth/google/callback`

Set the Stripe webhook endpoint to:
`https://specbuild-backend.onrender.com/api/v1/stripe/webhook`
and subscribe to `checkout.session.completed` (and optionally
`customer.subscription.deleted`).

## Updated file structure (additions)

```
specbuild-backend/src/main/kotlin/com/specbuild/backend/
├── auth/
│   ├── SessionService.kt            # (existing) extend to back DB-stored sessions
│   ├── SessionFilter.kt             # (existing) verify cookie, attach user
│   ├── MagicLinkService.kt          # NEW
│   ├── GoogleOAuthService.kt        # NEW
│   └── MailService.kt               # NEW (interface + Resend impl)
├── api/
│   ├── AuthController.kt            # NEW: /auth/me, /email/request, /email/verify,
│   │                                #      /google/start, /google/callback, /logout
│   └── StripeWebhookController.kt   # NEW: POST /stripe/webhook
└── db/
    └── (new repos for sessions, oauth_accounts, magic_link_tokens,
         oauth_state_tokens, processed_stripe_events)
```

## Acceptance criteria

### Auth
- `GET /auth/me` returns 401 with no cookie, 200 with a valid signed cookie.
- `POST /auth/email/request` with a valid email returns 202 and the email
  arrives at the inbox within a few seconds.
- Clicking the link in the email lands the browser on
  `{redirect}?signed_in=1` with a `session` cookie set on the backend domain.
- The token is single-use: clicking the same link a second time redirects
  with `?signed_in=0&error=invalid_token`.
- `GET /auth/google/start` redirects to `accounts.google.com`.
- Completing the Google flow lands the browser on `{redirect}?signed_in=1`
  with the cookie set.
- The same `users.id` is reused when the same email signs in via either
  Google or magic link.
- `POST /auth/logout` clears the cookie and returns 204.

### Stripe webhook
- Hitting `/stripe/webhook` with an invalid signature returns 400.
- A valid `checkout.session.completed` event with a known
  `client_reference_id` upgrades that user's plan and resets their quota.
- Replaying the same event ID is a no-op (idempotency check fires).
- A `checkout.session.completed` event with no `client_reference_id` but a
  known `customer_details.email` upgrades the user with that email.

### Cross-origin behavior
- A request from `https://specthinker.github.io` with `credentials:
  'include'` succeeds and the response includes
  `Access-Control-Allow-Credentials: true` and
  `Access-Control-Allow-Origin: https://specthinker.github.io` (not `*`).
- The `session` cookie sets and persists when the user reloads or opens a
  new tab.

### Quota
- An anonymous user with 1 polish used who then signs in via magic link
  has that 1 polish reflected in their user-level quota.

## How to invoke me in the backend directory

When you start a new chat with me inside `specbuild-backend/`, paste:

> "Implement the auth + Stripe webhook changes per the spec at
> /Users/salam/specthinker-web/SPEC-auth.md. This is an extension to the
> existing v1 backend at SPEC-backend.md in that same directory. Build the
> new endpoints, DB tables, session cookie infrastructure, Google OAuth
> flow, magic-link email flow (via Resend), and Stripe webhook handler.
> Update CORS for credentialed cross-origin. When done, give me the new
> env vars I need to set on Render, the redirect URI to register in
> Google Cloud Console, and the webhook URL to register in the Stripe
> dashboard."

## Open questions

1. **Email domain for `MAIL_FROM`** — Resend requires a verified domain.
   Do you own one (e.g. `specbuild.dev`)? If not, the simplest path is to
   use `onboarding@resend.dev` for testing — emails will deliver but
   look unprofessional in inboxes.
2. **Google OAuth consent screen** — needs a privacy-policy URL and a
   terms-of-service URL for production. For dev you can keep it in
   "Testing" mode (only listed test users can sign in). Defaulting to
   Testing mode unless you say otherwise.
3. **Anonymous-to-user merge** — the spec above merges quotas on sign-in.
   Do you also want to migrate saved specs from anonymous → user? Not
   strictly needed; defaulting to no.
4. **Session expiry** — defaulting to 365 days idle expiry (cookie
   `Max-Age=31536000`, refreshed on each request). Tighter? Looser?
5. **Pro / Lifetime plans** — frontend currently only shows Basic. When
   you add Pro/Lifetime, give me the Stripe price IDs so I can populate
   `PRICE_TO_PLAN`. Not blocking for this spec.
