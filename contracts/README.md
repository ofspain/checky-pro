# Contracts — single source of truth

- `events/` — Kafka event schemas (JSON Schema/Avro), one file per topic version.
  Topic naming: `<domain>.<entity>.<event>` (e.g. `payments.receipt.issued`). Backward-compatible evolution only.
- `api/` — OpenAPI specs per service.

Java models and the TypeScript client (`libs/ts/api-client`) are **generated** from this
directory in CI — never hand-written. Services never depend on each other's source; they
meet here. Changes to this directory trigger CI for every consumer and require CODEOWNERS review.
