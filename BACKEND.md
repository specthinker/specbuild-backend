# Specthinker — Backend

A Kotlin/Spring Boot backend for [Specthinker](./README.md) — a small site for writing
clear software specs. The backend stores specs, renders them to Markdown / plain text /
HTML, and offers an optional AI "polish" pass that improves a draft spec.

The frontend (a Vite static site) calls this service over `/api/v1`. It posts a spec
with seven sections, and this service round-trips CRUD, renders, and LLM-polish calls.

## Stack

- Kotlin 2.1 + JVM 17
- Spring Boot 3.5.6 (web, validation, actuator, data-jdbc)
- Spring Data JDBC + SQLite (xerial `sqlite-jdbc`)
- kotlinx.serialization for LLM wire payloads
- `java.net.http.HttpClient` for outbound LLM calls
- JUnit 5 + Spring MockMvc for tests

## Spec model

A spec has a `title` and exactly **seven** sections, in this order:

1. **Goal** — what are we building, why, smallest successful outcome
2. **Scope** — what's included, excluded, assumed
3. **Files** — may change / must not change / may create
4. **Rules** — languages, libraries, patterns, hard constraints
5. **Acceptance Criteria** — happy path, error path, edge cases, definition of done
6. **Verification** — tests, manual checks, required evidence
7. **Output** — deliverables, summary, remaining risks / open questions

The seven fields are stored as a single JSON blob (`sections_json TEXT`) inside the
`specs` table. `Sections` is `@Serializable` and is read/written via
`JdbcCustomConversions` reading/writing converters.

## API

All endpoints live under `/api/v1`. The CORS layer is open by default
(`allowedOrigins: "*"`); tighten with `specthinker.cors.allowed-origins` in
`application.yml`.

| Method | Path | Body / Query | Description |
| --- | --- | --- | --- |
| `GET`    | `/api/v1/specs`                 | — | List specs, newest first |
| `POST`   | `/api/v1/specs`                 | `CreateSpecRequest` | Create a spec |
| `GET`    | `/api/v1/specs/{id}`            | — | Fetch a spec |
| `PUT`    | `/api/v1/specs/{id}`            | `UpdateSpecRequest` (must include `version`) | Update a spec with optimistic locking |
| `DELETE` | `/api/v1/specs/{id}`            | — | Delete a spec |
| `GET`    | `/api/v1/specs/{id}/render`     | `?format=markdown\|text\|html` | Render a saved spec |
| `POST`   | `/api/v1/specs/render`          | `RenderRequest` + `?format=...` | Render an unsaved spec |
| `POST`   | `/api/v1/llm/polish`            | `PolishRequest` | Polish a spec via LLM (fallback chain) |
| `POST`   | `/api/v1/llm/quota`             | `{"clientId": "..."}` | Read current quota usage |
| `GET`    | `/actuator/health`              | — | Health check |

`format` accepts `md`/`markdown`, `text`/`txt`/`plain`, or `html`/`htm`.

### Error shape

```json
{ "error": "polish_unavailable", "message": "AI polish is temporarily down.",
  "details": { "providers": ["deepseek-direct: ...", "openrouter-deepseek: ..."] },
  "timestamp": "2026-06-05T11:09:36Z" }
```

`details` is only present when the error carries extra context.

### Quota and polish

`POST /api/v1/llm/polish`:

- The `clientId` in the body is used for quota tracking. Missing / blank → `"anonymous"`.
- Quota is **consumed before** the call and **refunded** if every provider fails.
- A successful call returns 200 with `PolishResponse { content, provider, quota }` and
  headers `X-Llm-Provider`, `X-Quota-Used`, `X-Quota-Limit`, `X-Quota-Resets-At`.
- Quota exceeded → `429` with `Retry-After` and `Resets-At` headers.
- All three providers failed → `503` with `error: "polish_unavailable"`.

## LLM providers

`LlmProvider` is a tiny interface:

```kotlin
interface LlmProvider {
    val name: String
    suspend fun complete(systemPrompt: String, userPrompt: String): String
}
```

Three implementations live in `com.specthinker.llm.providers` and share a single
`HttpClient` bean (set up with `connect-timeout` and reused — never per request):

| Bean name | URL | Headers | Default model |
| --- | --- | --- | --- |
| `DeepseekProvider`           | `https://api.deepseek.com/v1`     | `Authorization` | `deepseek-chat` |
| `OpenrouterDeepseekProvider` | `https://openrouter.ai/api/v1`    | `Authorization`, `HTTP-Referer`, `X-Title` | `deepseek/deepseek-chat-v3.1:free` |
| `OpenrouterFreeProvider`     | `https://openrouter.ai/api/v1`    | `Authorization`, `HTTP-Referer`, `X-Title` | `meta-llama/llama-3.1-8b-instruct:free` |

The fallback chain in `LlmService` iterates them in that order:

- `401` / `403` → `AuthException` → next provider
- `429` → `RateLimitException` → next provider
- `5xx`, network, timeout, anything else → next provider
- every provider failed → `AllProvidersFailedException` → HTTP 503

## Configuration

`src/main/resources/application.yml` ships sensible defaults. Override with
environment variables or a `application-{profile}.yml`.

| Property | Default | Notes |
| --- | --- | --- |
| `server.port`                              | `8080` | |
| `spring.datasource.url`                    | `jdbc:sqlite:./data/specthinker.db` | SQLite file. Use `:memory:` for tests. |
| `specthinker.cors.allowed-origins`         | `*` | Comma-separated list. |
| `specthinker.llm.connect-timeout`          | `10` | Seconds. |
| `specthinker.llm.request-timeout`          | `45` | Seconds. |
| `specthinker.llm.quota.enabled`            | `true` | Set `false` to disable quota. |
| `specthinker.llm.quota.per-client-per-day` | `50` | Per `clientId` (UTC day). |
| `specthinker.llm.providers.<name>.enabled` | `true` | Per-provider. |
| `specthinker.llm.providers.<name>.api-key` | (none) | Set via env: `DEEPSEEK_API_KEY`, `OPENROUTER_API_KEY`. |
| `specthinker.llm.providers.<name>.base-url`| provider-specific | |
| `specthinker.llm.providers.<name>.model`   | provider-specific | |

## Run

Requirements: JDK 17 and Gradle 8.10+ (or 9.x). On macOS:

```bash
brew install openjdk@17 gradle
```

From this directory:

```bash
export DEEPSEEK_API_KEY=...
export OPENROUTER_API_KEY=...
./gradlew bootRun
```

Then test:

```bash
curl -s -X POST http://localhost:8080/api/v1/specs \
  -H 'Content-Type: application/json' \
  -d '{"title":"My Spec","sections":{"goal":"g","scope":"s","files":"f","rules":"r","acceptanceCriteria":"a","verification":"v","output":"o"}}'
```

## Test

```bash
./gradlew test
```

The test suite covers:

- Sections serialization (round-trip, defaults, ordering)
- Markdown / Text / HTML renderers
- `Format.parse` aliases and rejection
- LLM request body shape and chat response parsing
- `LlmService` fallback chain (success / fallback / auth / 429 / all-fail / quota)
- `QuotaService` (consume / refund / limit / day rollover)
- End-to-end `SpecController` CRUD + render lifecycle via MockMvc

## Layout

```
src/main/kotlin/com/specthinker/
├── SpecthinkerApplication.kt
├── config/
│   ├── CorsConfig.kt
│   ├── CorsProperties.kt
│   ├── HttpClientConfig.kt
│   └── PersistenceConfig.kt
├── spec/
│   ├── Format.kt
│   ├── Renderer.kt
│   ├── RendererConfig.kt
│   ├── Sections.kt
│   ├── Spec.kt
│   ├── SpecController.kt
│   ├── SpecDtos.kt
│   ├── SpecExceptions.kt
│   ├── SpecRepository.kt
│   └── SpecService.kt
├── llm/
│   ├── ChatModels.kt
│   ├── LlmController.kt
│   ├── LlmDtos.kt
│   ├── LlmExceptions.kt
│   ├── LlmProperties.kt
│   ├── LlmProvider.kt
│   ├── LlmService.kt
│   ├── QuotaService.kt
│   └── providers/
│       ├── LlmProviders.kt
│       └── OpenAiCompatibleProvider.kt
└── web/
    └── GlobalExceptionHandler.kt
```
