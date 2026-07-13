# Shared Libraries

- `java/outbox` — transactional outbox + Kafka relay used by every service (no dual writes). **Not yet extracted** (services/auth/docs/architecture/auth-decisions.md D-018): the auth service implements it locally in `com.themistra.auth.events` until a second service needs the same mechanism, at which point that package moves here verbatim.
- `java/kafka-core` — consumer idempotency (dedupe on event key), serde, tracing.
- `java/security-core` — OAuth2 resource-server config, JWT validation against Auth JWKS.
- `ts/api-client` — TypeScript client generated from `contracts/api`, consumed by `frontend/`.

Rule: libraries hold cross-cutting plumbing only — never business/domain logic.
