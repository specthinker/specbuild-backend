# Specthinker Backend — Frontend Integration Spec

This is a complete, machine-readable contract for the backend. The frontend
should treat this as the source of truth for HTTP wiring.

- **Base URL (dev)**: `http://localhost:8080`
- **Base path**: all endpoints are under `/api/v1`
- **Content type**: `application/json; charset=utf-8` (UTF-8)
- **Auth**: none
- **CORS**: `Access-Control-Allow-Origin: *` for all `/api/**` routes, methods `GET POST PUT DELETE OPTIONS`, all headers. No credentials.
- **Time format**: ISO 8601 strings, e.g. `2026-06-05T11:09:07.208Z`. Always UTC, always millisecond precision.

---

## 1. Data model

### 1.1 `Sections` object (sent and received inside every spec payload)

A spec has exactly these seven string fields, in this order. Every field is
**required** in the JSON object (you may send `""` for empty ones), and the
backend preserves the order.

| JSON key | Type | Section name shown to user |
| --- | --- | --- |
| `goal` | string | "Goal" |
| `scope` | string | "Scope" |
| `files` | string | "Files" |
| `rules` | string | "Rules" |
| `acceptanceCriteria` | string | "Acceptance Criteria" |
| `verification` | string | "Verification" |
| `output` | string | "Output" |

### 1.2 `Spec` object (the full record returned by the backend)

```jsonc
{
  "id": "9bafc2ea-63ea-4ad3-b002-6f7ef436c203",   // string, UUID v4
  "title": "My Spec",                              // string, non-blank
  "sections": { /* Sections object, see 1.1 */ },
  "createdAt": "2026-06-05T11:09:07.208Z",          // string, ISO 8601
  "updatedAt": "2026-06-05T11:09:07.208Z",          // string, ISO 8601
  "version": 1                                     // integer, see 1.3
}
```

### 1.3 `version` and optimistic locking

- A new spec is created with `version` 0 in the request (omit it) and the
  server returns `version: 1` after the first save.
- Every successful `PUT` increments `version` by 1. The server compares the
  `version` in the `PUT` body to the stored version; if they differ, the
  request fails with HTTP 409 and `error: "spec_version_mismatch"`.
- The frontend should store the last-seen `version` per spec and send it back
  on every `PUT`. If the PUT fails with 409, refetch and re-apply or warn
  the user about a concurrent edit.

### 1.4 `SpecSummary` object (returned by `GET /api/v1/specs` only)

```jsonc
{
  "id": "9bafc2ea-63ea-4ad3-b002-6f7ef436c203",
  "title": "My Spec",
  "createdAt": "2026-06-05T11:09:07.208Z",
  "updatedAt": "2026-06-05T11:09:07.208Z",
  "version": 1
}
```

The list endpoint does **not** include `sections` to keep payloads small.

---

## 2. Error shape

Every non-2xx response from `/api/**` returns this shape. `details` is
optional and only present when relevant.

```jsonc
{
  "error": "polish_unavailable",                   // string, machine-readable code
  "message": "AI polish is temporarily down.",     // string, human-readable
  "details": { /* optional, error-specific */ },  // object | null
  "timestamp": "2026-06-05T11:09:36.199Z"          // string, ISO 8601
}
```

Known `error` codes the frontend should handle:

| HTTP | `error` | When |
| --- | --- | --- |
| 400 | `bad_request` | Malformed JSON, missing body, bad query param (e.g. unknown `format`) |
| 400 | `validation_failed` | A required field is missing or blank (e.g. blank `title`) |
| 404 | `spec_not_found` | `GET`/`PUT`/`DELETE` on an id that doesn't exist |
| 409 | `spec_version_mismatch` | `PUT` `version` does not match the stored `version` |
| 429 | `quota_exceeded` | Daily AI polish quota for this `clientId` is used up. See 5.2. |
| 503 | `polish_unavailable` | All three LLM providers failed. See 5.1. |
| 500 | `internal_error` | Catch-all. Includes a `timestamp` but no internals. |

`details` is currently used as:

- `quota_exceeded` → `{ "used": 51, "limit": 50 }`
- `polish_unavailable` → `{ "providers": ["deepseek-direct: Provider deepseek-direct returned 401 (auth)", "openrouter-deepseek: ...", "openrouter-free: ..."] }`

`quota_exceeded` also sets the response headers `Retry-After` (seconds) and
`Resets-At` (Unix epoch millis of the next UTC midnight).

---

## 3. Endpoints — Specs CRUD

### 3.1 `GET /api/v1/specs` — list

List every spec, newest `updatedAt` first. No pagination (the dataset is
small). No request body.

- **Response 200**: `SpecSummary[]`
- **Example**:
  ```
  GET http://localhost:8080/api/v1/specs
  → 200 [{ "id": "...", "title": "...", "createdAt": "...", "updatedAt": "...", "version": 1 }]
  ```

### 3.2 `POST /api/v1/specs` — create

- **Request body** (`CreateSpecRequest`):
  ```jsonc
  {
    "title": "My Spec",        // string, required, non-blank
    "sections": {               // Sections object, see 1.1
      "goal": "...",
      "scope": "...",
      "files": "...",
      "rules": "...",
      "acceptanceCriteria": "...",
      "verification": "...",
      "output": "..."
    }
  }
  ```
  The frontend may omit the `sections` field and an all-empty `Sections`
  object is used as default. The frontend may also omit any of the seven
  fields inside `sections`; they default to `""`.
- **Response 201**: full `Spec` object (see 1.2). The server assigns `id`,
  `createdAt`, `updatedAt`, and `version` (returns 1).
- **Errors**: 400 if `title` is blank or missing, or the body is malformed.

### 3.3 `GET /api/v1/specs/{id}` — fetch one

- **Path param**: `id` (string, UUID)
- **Response 200**: full `Spec` object
- **Errors**: 404 `spec_not_found`

### 3.4 `PUT /api/v1/specs/{id}` — update

- **Path param**: `id`
- **Request body** (`UpdateSpecRequest`):
  ```jsonc
  {
    "title": "My Spec v2",            // string, required, non-blank
    "sections": { /* Sections */ },    // required
    "version": 1                       // integer, required, must match stored version
  }
  ```
- **Response 200**: full updated `Spec` object, with `version` incremented to 2
- **Errors**:
  - 400 if `title` is blank or `version` is missing
  - 404 `spec_not_found`
  - 409 `spec_version_mismatch` if `version` does not match the stored value (concurrent edit)

### 3.5 `DELETE /api/v1/specs/{id}` — delete

- **Path param**: `id`
- **Response 204**: empty body
- **Errors**: 404 `spec_not_found`

---

## 4. Endpoints — Render

Both render endpoints take a `format` query parameter and return the
formatted spec as a raw text body (not JSON).

`format` accepts (case-insensitive, all aliases):

- Markdown: `markdown`, `md` (default if omitted)
- Plain text: `text`, `txt`, `plain`
- HTML: `html`, `htm`

Unknown values → 400 `bad_request`.

The `Content-Type` response header is set to match:

- `text/markdown;charset=UTF-8`
- `text/plain;charset=UTF-8`
- `text/html;charset=UTF-8`

The `Content-Disposition: inline` header is also set so the browser opens
the spec inline rather than downloading it.

### 4.1 `GET /api/v1/specs/{id}/render?format=...`

Render a saved spec.

- **Path param**: `id`
- **Query**: `format` (default `markdown`)
- **Response 200**: raw text body in the requested format
- **Errors**: 404 `spec_not_found` if `id` doesn't exist; 400 if `format` is unknown

### 4.2 `POST /api/v1/specs/render?format=...`

Render an unsaved spec. Useful for "Copy as Markdown" before the user
clicks Save.

- **Request body** (`RenderRequest`):
  ```jsonc
  {
    "title": "My Spec",        // string, required, non-blank
    "sections": { /* Sections */ }
  }
  ```
- **Query**: `format` (default `markdown`)
- **Response 200**: raw text body
- **Errors**: 400 if `title` is blank

### 4.3 Render output shapes

Empty sections are omitted (no empty `## Scope` heading). The seven
sections are always rendered in this order: `Goal, Scope, Files, Rules,
Acceptance Criteria, Verification, Output`.

- **Markdown**: `# <title>` then `## <Section>` per non-empty section, body verbatim.
- **Plain text**: `<TITLE>` in caps with `===` underline, then `<SECTION>` in caps with `---` underline per non-empty section.
- **HTML**: A complete HTML5 document with `<!DOCTYPE html>`, embedded dark-mode CSS that flips to light on `prefers-color-scheme: light`, and `<h2>` per section. HTML special characters in the title and section bodies are escaped.

---

## 5. Endpoints — AI polish

### 5.1 `POST /api/v1/llm/polish`

Polish a spec by sending it through a chain of three LLM providers. The
server picks the first one that responds with HTTP 200, in this fixed
order: Deepseek direct → OpenRouter Deepseek → OpenRouter free. The
system prompt instructs the model to rewrite the spec to be clearer and
tighter while keeping the user's voice and any concrete facts.

- **Request body** (`PolishRequest`):
  ```jsonc
  {
    "title": "My Spec",        // string, required, non-blank
    "sections": { /* Sections */ },
    "clientId": "uuid-or-string-from-localStorage"  // string | null, optional but recommended
  }
  ```
- **Response 200** (`PolishResponse`):
  ```jsonc
  {
    "content": "<polished spec in markdown, with seven ## sections>",  // string
    "provider": "deepseek-direct",                                       // string, which provider answered
    "quota": {
      "used": 1,                            // long, current count
      "limit": 50,                          // long, configured daily limit
      "resetsAtEpochMillis": 1780704000000  // long, UTC midnight of next day
    }
  }
  ```
  **Response headers** on success:
  - `X-Llm-Provider: deepseek-direct`
  - `X-Quota-Used: 1`
  - `X-Quota-Limit: 50`
  - `X-Quota-Resets-At: 1780704000000`
- **Errors**:
  - 400 `bad_request` / `validation_failed` if `title` is blank or body is malformed
  - 429 `quota_exceeded` — see 5.2
  - 503 `polish_unavailable` — every provider failed. The `details.providers` array lists the error from each one so you can show a "we tried X, Y, Z and all failed" message.

### 5.2 Quota tracking (and how to handle 429)

- The default daily limit is **50 polish calls per `clientId` per UTC day**.
- The backend consumes a token **before** the call and **refunds** it if every
  provider fails. So a 503 response does **not** consume quota.
- `clientId` is anything the frontend chooses. A common pattern is to
  generate a random UUID on first load, store it in `localStorage`, and
  send it on every polish request. If omitted or blank, the backend uses
  the literal string `"anonymous"`, so all anonymous callers share one
  bucket.
- On 429 `quota_exceeded`:
  - `details.used` and `details.limit` are the current numbers.
  - `Retry-After` header is the number of seconds until the next UTC midnight.
  - `Resets-At` header is the same value in Unix epoch milliseconds.
  - The frontend should display "AI polish quota reached, resets in N hours"
    and disable the polish button until the reset time.

### 5.3 `POST /api/v1/llm/quota` — read current quota

- **Request body** (optional): `{"clientId": "alice"}`. If omitted, the
  backend uses `"anonymous"`.
- **Response 200** (`QuotaState`):
  ```jsonc
  {
    "used": 0,                            // long
    "limit": 50,                          // long
    "resetsAtEpochMillis": 1780704000000  // long
  }
  ```
- This endpoint is unauthenticated and does not consume quota. Use it to
  show "3 of 50 polishes used today" in the UI.

---

## 6. Endpoints — Misc

### 6.1 `GET /actuator/health`

- **Response 200**: `{"status":"UP"}`
- Use this for liveness checks.

---

## 7. Quick reference (what to wire first)

Minimum viable integration:

1. **Build the form** with seven textareas keyed by `goal, scope, files, rules, acceptanceCriteria, verification, output` and a `title` input.
2. **"Copy as Markdown/Text/HTML"** button (no save needed) → `POST /api/v1/specs/render?format=md|txt|html` with `{title, sections}`. Use `navigator.clipboard.writeText(response)`.
3. **"Save"** button → `POST /api/v1/specs` to create, then store the returned `id` and `version` in client state.
4. **"Update"** (after a load) → `PUT /api/v1/specs/{id}` with the stored `version`; on 409 refetch and warn.
5. **"List"** screen → `GET /api/v1/specs` for sidebar; `GET /api/v1/specs/{id}` to open one.
6. **"Polish with AI"** button → generate a UUID in `localStorage` as `clientId`; call `POST /api/v1/llm/polish`; on 200 replace the form values by parsing the `content` back into the seven sections; on 429 show the quota message; on 503 show a "polish temporarily unavailable" message.
7. **Quota indicator** (optional) → on app boot, call `POST /api/v1/llm/quota` with the stored `clientId` to show "3 of 50 used".

---

## 8. Local dev CORS + base URL

- Dev backend: `http://localhost:8080`
- Dev frontend (Vite): whatever port the frontend lives on (e.g. `http://localhost:5173`)
- CORS is open for any origin, so the frontend can call the backend
  directly with no proxy. In production, point the frontend at the
  backend's public URL.
