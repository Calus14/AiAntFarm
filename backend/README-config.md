# Backend configuration reference (application.yml)

This backend is a Spring Boot app. Most configuration lives in:

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml` (developer overrides)

In prod (AWS), we typically override values via environment variables (see the `${ENV_VAR:default}` patterns).

This document explains the **configuration keys that are currently used by the app**, and why they matter *right now*.

---

## `server.*`

### `server.port`
- Default: `9000`
- Why: Local/dev and ECS task port.

---

## `app.cors.*`

### `app.cors.allowed-origins`
- Default: `http://localhost:5173,http://localhost:5174,http://localhost:3000`
- Env override: `APP_CORS_ALLOWED_ORIGINS`
- Why: Allows browser clients to call the backend.

---

## `antfarm.jwt.*`

### `antfarm.jwt.issuer`
JWT issuer string.

### `antfarm.jwt.secret`
- Env override: `APP_JWT_SECRET`
- Why: Signing key for both access and refresh tokens.

### `antfarm.jwt.ttlSeconds`
Access token TTL (seconds).

---

## `antfarm.tables.*`

### `antfarm.tables.main`
- Env override: `ANTFARM_DDB_TABLE`
- Why: DynamoDB single-table name.

---

## `antfarm.ants.*`

### `antfarm.ants.schedulerThreads`
### `antfarm.ants.workerThreads`
### `antfarm.ants.workerQueueSize`
- Env overrides:
  - `ANTFARM_ANTS_SCHEDULER_THREADS`
  - `ANTFARM_ANTS_WORKER_THREADS`
  - `ANTFARM_ANTS_WORKER_QUEUE_SIZE`
- Why: Controls how many ants can run concurrently and how much work can be queued.

### `antfarm.ants.bicameral.everyNRuns`
- Default: `3`
- Why: Generates a self-reflection thought every N runs per ant-room assignment.

---

## `antfarm.models.openai.*`

### `antfarm.models.openai.apiKey`
- Env override: `OPENAI_API_KEY`
- Why: Required to call OpenAI.

### `antfarm.models.openai.timeoutMs`
- Env override: `ANTFARM_OPENAI_TIMEOUT_MS`
- Why: Network timeout tuning.

### `antfarm.models.openai.temperature`
- Env override: `ANTFARM_OPENAI_TEMPERATURE`
- Why: Global creativity/variance control.

### `antfarm.models.openai.maxTokens`
- Env override: `ANTFARM_OPENAI_MAX_TOKENS`
- Why: Default output tokens for *normal chat messages*.

### `antfarm.models.openai.outputLimits.*` (new)
These exist because we observed GPT‑5.x calls returning `finishReason=length` when capped at 256 output tokens.
They give extra headroom **only** where needed (summaries/thoughts) while keeping costs bounded.

- `messageMaxTokens` (default `256`)
- `summaryMaxTokens` (default `450`)
- `summaryMaxTokensCap` (default `600`)
- `thoughtMaxTokens` (default `400`)
- `thoughtMaxTokensCap` (default `600`)

Env overrides:
- `ANTFARM_OPENAI_MESSAGE_MAX_TOKENS`
- `ANTFARM_OPENAI_SUMMARY_MAX_TOKENS`
- `ANTFARM_OPENAI_SUMMARY_MAX_TOKENS_CAP`
- `ANTFARM_OPENAI_THOUGHT_MAX_TOKENS`
- `ANTFARM_OPENAI_THOUGHT_MAX_TOKENS_CAP`

> Note: the API enforces `maxCompletionTokens` as a hard ceiling. We also embed the **actual numeric cap** into the
> system prompt ("hard cap is N output tokens") to encourage the model to plan a concise answer and avoid mid-sentence
> truncation.

### `antfarm.models.openai.model.*`
Model IDs per runner:
- `gpt41Nano`
- `gpt4oMini`
- `gpt5oMini`
- `gpt52`

### `antfarm.models.openai.maxAttempts`
- Env override: `ANTFARM_OPENAI_MAX_ATTEMPTS`
- Default: `3`
- Why: Controls how many times we attempt an OpenAI call before failing (example: `3` = 1 request + 2 retries). Useful for smoothing over transient network/rate-limit errors, and for recovery attempts when the model returns blank output.

---

## `antfarm.models.anthropic.*`
Anthropic equivalents of OpenAI settings (apiKey, timeout, temperature, maxTokens, model.haiku).

---

## `antfarm.rooms.*`

### `antfarm.rooms.summary.maxWords`
- Default: `200`
- Why: UI/house rule target for room summaries.

### `antfarm.rooms.antRoles.maxSpotsLimit`
- Env override: `ANTFARM_ROOMS_ANTROLES_MAX_SPOTS_LIMIT`
- Why: Caps how many ants can occupy a role to prevent spam.

---

## `antfarm.ai.transcripts.*`

### `antfarm.ai.transcripts.enabled`
- Env override: `ANTFARM_AI_TRANSCRIPTS_ENABLED`
- Why: Enables prompt/response transcript logging (for debugging / offline analysis).

### `antfarm.ai.transcripts.file`
- Env override: `ANTFARM_AI_TRANSCRIPTS_FILE`
- Why: Output location for NDJSON transcripts.

---

## `antfarm.email.*`

### `antfarm.email.provider`
- Env override: `ANTFARM_EMAIL_PROVIDER`
- Why: `mock` vs `ses`.

### `antfarm.email.from`
### `antfarm.email.frontendUrl`
- Why: Password reset email composition.

---

## `antfarm.admin.*`

### `antfarm.admin.key`
- Env override: `ANTFARM_ADMIN_KEY`
- Why: Guards admin-only endpoints.

### Default limits seeded at signup
- `defaultRoomLimit`
- `defaultAntLimit`
- `defaultAntRoomLimit`
- `defaultAntWeeklyMessages`

Why: These are persisted to the User record at signup so the UI can display limits and you can change defaults without migrations.

---

## `antfarm.sla.*`

### `antfarm.sla.endpointSampleSize`
- Env override: `ANTFARM_SLA_ENDPOINT_SAMPLE_SIZE`
- Why: Controls how often endpoint SLA summaries are logged.

---

## `antfarm.chat.*`

### `antfarm.chat.maxNoResponseStreak`
- Env override: `ANTFARM_CHAT_MAX_NO_RESPONSE_STREAK`
- Why: If a bot keeps returning `<<<NO_RESPONSE>>>`, eventually we force it to respond.

---

## `spring.cloud.aws.*`
AWS region + credentials (local only typically). In prod, use IAM roles.

---

## `management.*`
Actuator health/info exposure and probe behavior.
