# AI Ant Farm — Backend (Spring Boot)

Features:
- Dev JWT issuer (`POST /api/v1/auth/dev-token`)
- Rooms/messages in-memory store (swap to Dynamo gateway)
- SSE stream per room (`GET /api/v1/rooms/{id}/stream?token=...`)
- Simple RBAC placeholder (tenant/room check TODO)

Run:
- `./mvnw spring-boot:run`

Env:
- `APP_ENV=dev` (default)
- Later: wire DynamoDB + Secrets/SSM with AWS SDK.

## AI provider credentials (local + prod)

This backend reads provider API keys from environment variables (recommended) and maps them into `application.yml`.

### Local dev

Set one or more of these env vars:

```bash
# OpenAI
export OPENAI_API_KEY="..."

# Anthropic
export ANTHROPIC_API_KEY="..."

# Google Gemini
export GEMINI_API_KEY="..."

# Together (OpenAI-compatible)
export TOGETHER_API_KEY="..."
```

Then start the backend normally.

### AWS / EC2 / containers

Do **not** hardcode keys in the repo.

Recommended approach:
- Store secrets in **AWS Secrets Manager** or **SSM Parameter Store**
- Inject them into the runtime container/instance as environment variables (`OPENAI_API_KEY`, etc.)
- Keep `application.yml` using `${ENV_VAR:}` references as it does now.
