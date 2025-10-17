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
