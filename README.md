# Checky Pro (Themistra)

AI-powered trust layer for blockchain commerce. Phase 1: stablecoin payment verification —
wallet monitoring, quorum-verified transactions, tamper-proof receipts, invoicing, and notifications.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the system design and
[docs/service-languages.pdf](docs/service-languages.pdf) for language decisions per service.

## Repository layout

| Path | Contents |
|---|---|
| `services/` | Backend microservices (Java 21 / Spring Boot); `crypto/sidecars/` holds TS chain adapters |
| `libs/` | Shared libraries — `java/` (outbox, kafka-core, security-core), `ts/` (generated API client) |
| `contracts/` | **Source of truth**: Kafka event schemas (`events/`) and OpenAPI specs (`api/`); all models are generated from here |
| `frontend/` | React + TypeScript, mobile-first PWA |
| `infra/` | AWS CDK app (TypeScript) — stacks split by blast radius |
| `docs/` | Architecture docs (HTML draft → PDF artifact) and ADRs in `docs/adr/` |

## Ground rules

- Services depend only on `libs/` and `contracts/` — never on another service's source.
- Every Kafka consumer is idempotent; every event is published through the outbox.
- Trunk-based development: short-lived branches into `main`; `main` is always deployable.
- Changes to `services/crypto/`, `contracts/`, or security stacks require CODEOWNERS review.
- Material design changes require an ADR in `docs/adr/`.
