# Deployment runbook

This file is the **only** doc you (or a deploy agent) need to ship the
backend to Render. Every step below is a single command or a single click.
No manual JSON, no guessing env var names.

## 0. Prereqs (one-time)

- A GitHub account that can create / push to repos in the `specthinker` org.
- A Render account (https://dashboard.render.com).
- API keys:
  - `DEEPSEEK_API_KEY` from https://platform.deepseek.com
  - `OPENROUTER_API_KEY` from https://openrouter.ai/keys

> Note: this backend has **no auth and no sessions**, so the
> `SESSION_SECRET` env var in `render.yaml` is currently **unused** by the
> app. It's set on Render as a forward-compat placeholder in case you add
> Spring Security / cookies later. Render will generate the value
> automatically (`generateValue: true`).

## 1. Push the repo to GitHub

From this directory:

```bash
git init
git add -A
git commit -m "Initial commit: Specthinker backend"
git branch -M main
git remote add origin git@github.com:specthinker/specbuild-backend.git
git push -u origin main
```

If the remote is HTTPS instead of SSH:

```bash
git remote add origin https://github.com/specthinker/specbuild-backend.git
git push -u origin main
```

## 2. Create the Render service

Two ways, pick one.

### Option A — Blueprint (recommended, declarative)

1. Go to https://dashboard.render.com/blueprints.
2. Click **New Blueprint Instance**.
3. Connect the `specthinker/specbuild-backend` repo.
4. Render reads `render.yaml` and creates the Web Service + persistent disk
   with the env vars already wired.
5. Set the **secret** env vars in the dashboard after creation:
   - `DEEPSEEK_API_KEY` = your real key
   - `OPENROUTER_API_KEY` = your real key

### Option B — Manual web service

1. Go to https://dashboard.render.com/create?type=web.
2. Connect the `specthinker/specbuild-backend` repo.
3. Set:
   - **Runtime**: `Docker`
   - **Region**: `Oregon` (or any)
   - **Plan**: `Free`
   - **Health check path**: `/actuator/health`
4. Add the env vars from the table below.
5. Click **Advanced** → **Add Disk**:
   - Name: `specbuild-data`
   - Mount path: `/var/data`
   - Size: `1 GB`
6. Click **Create Web Service**.

## 3. Env vars the service needs

| Key | Value | Secret? |
| --- | --- | --- |
| `JAVA_VERSION` | `17` | no |
| `ALLOWED_ORIGIN` | `https://specthinker.github.io` | no |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:/var/data/specbuild.db` | no |
| `DEEPSEEK_API_KEY` | *your key* | **yes** |
| `OPENROUTER_API_KEY` | *your key* | **yes** |
| `SESSION_SECRET` | *Render auto-generates* | yes |
| `PORT` | (Render sets this automatically) | n/a |

`PORT` is injected by Render. The Dockerfile reads it and passes it to
Spring via `-Dserver.port=${PORT}`.

`ALLOWED_ORIGIN` is the only allowed CORS origin. For local dev you can
override it to `*` or to `http://localhost:5173` in a Render env var.

`SPRING_DATASOURCE_URL` points at the persistent disk so the SQLite file
survives redeploys. The disk is mounted at `/var/data` and the schema
auto-creates `specbuild.db` on first boot.

## 4. First deploy

1. Trigger a deploy (Blueprint does this automatically; manual service
   starts it on creation).
2. Watch the logs in the Render dashboard.
3. Look for `Started SpecthinkerApplicationKt in N.N seconds`. If you
   don't see it, paste the build log and we'll debug.
4. First build takes **3-5 minutes** (Docker pulls the JDK base, Gradle
   resolves deps, builds the jar).

## 5. Smoke test

Replace `specbuild-backend` with the service name Render assigned
(usually `specbuild-backend` on the free tier — URL is
`https://specbuild-backend.onrender.com`).

```bash
# Liveness
curl https://specbuild-backend.onrender.com/actuator/health
# → {"status":"UP"}

# Ad-hoc render (no DB needed)
curl -X POST "https://specbuild-backend.onrender.com/api/v1/specs/render?format=md" \
  -H "Content-Type: application/json" \
  -d '{"title":"Smoke test","sections":{"goal":"hi","scope":"","files":"","rules":"","acceptanceCriteria":"","verification":"","output":""}}'
# → # Smoke test\n\n## Goal\n\nhi

# Health with the persisted DB
curl -X POST https://specbuild-backend.onrender.com/api/v1/specs \
  -H "Content-Type: application/json" \
  -d '{"title":"Persisted","sections":{"goal":"g","scope":"","files":"","rules":"","acceptanceCriteria":"","verification":"","output":""}}'
# → 201 with id and version:1

# Polish (only works with real DEEPSEEK_API_KEY / OPENROUTER_API_KEY)
curl -X POST https://specbuild-backend.onrender.com/api/v1/llm/polish \
  -H "Content-Type: application/json" \
  -d '{"title":"T","sections":{"goal":"G","scope":"","files":"","rules":"","acceptanceCriteria":"","verification":"","output":""},"clientId":"smoke-test"}'
# → 200 { content, provider, quota } on success
# → 503 { error:"polish_unavailable", details:{providers:[...]} } if all providers fail
```

## 6. Wire the frontend

### Local dev

In the frontend repo (`~/specthinker-web` or wherever it lives):

```bash
echo 'VITE_API_URL=https://specbuild-backend.onrender.com' > .env
npm run dev
```

The Vite frontend should read `import.meta.env.VITE_API_URL` and use it as
the API base.

### Production (GitHub Pages)

1. In the frontend repo: **Settings → Secrets and variables → Actions**
2. New repository secret:
   - Name: `VITE_API_URL`
   - Value: `https://specbuild-backend.onrender.com`
3. Update `.github/workflows/pages.yml` so the build step exports it:

   ```yaml
   - name: Build
     run: npm run build
     env:
       VITE_API_URL: ${{ secrets.VITE_API_URL }}
   ```

4. Push. The next GitHub Pages deploy will bake the URL into the bundle.

## 7. Gotchas (so the first deploy doesn't surprise you)

- **Free tier sleeps after 15 min idle.** First request after a sleep
  takes 30-60 s to wake up. This is normal; the user will see a slow
  "Polish" click and then it works. If you want it gone, upgrade to the
  $7/mo "Starter" plan which doesn't sleep.

- **Persistent disk is read-write inside the container but is wiped on
  service deletion.** A redeploy does not wipe the disk. To reset, click
  **Manual Deploy → Clear build cache & deploy**.

- **Build context is large.** The first build pulls a JDK image
  (~500 MB) and the Gradle dependency cache (~200 MB). Subsequent
  builds reuse the cache and finish in ~1-2 min.

- **Tests are skipped in the Docker build** (`-x test`). They run in CI
  or locally via `./gradlew test` before pushing.

- **CORS is single-origin** by default in this setup. The YAML uses
  `ALLOWED_ORIGIN` (singular). If you ever need multiple, switch the
  env var value to a comma-separated list and update
  `CorsConfig.parseOrigins` to use that env name. (Current code already
  accepts comma-separated values; you only need to align the env var
  name.)

- **No persistent disk = no saved specs across redeploys.** The disk
  config in `render.yaml` is the fix. Removing the `disk:` block puts
  the SQLite file in `/tmp` (or wherever the container scratch dir
  points), which is ephemeral.
