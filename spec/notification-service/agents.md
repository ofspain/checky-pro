# agents.md — Notification Service

Standing, durable rules for `services/notification`. This file is authoritative for this service.
A feature spec never restates these; it references this file and records only what is specific to the
feature. If a spec must override a rule here, it says so explicitly in its §4a (LOCKED).

## Platform rules (identical across all Themistra services)

**Language & build**
- Java 21, Spring Boot 3.5.4 (inherited from the root `/pom.xml` parent). No other JVM language.
- Maven multi-module (ADR-0002). Build: `mvn -pl services/notification verify`. The module is
  registered in the root `<modules>`. Convention sharing happens through the parent POM.

**Configuration**
- Flat `application.properties` only — never YAML.
- Config is bound to validated `@ConfigurationProperties` records; startup FAILS on missing/invalid
  values in non-local profiles. Profiles: `local`, `dev`, `staging`, `prod`. Local dev runs against
  Docker Compose (Postgres + Kafka) and a capturing fake transport — no real email is sent in CI.

**Persistence & schema**
- PostgreSQL only, one logical schema per service (this service owns `notifications`). No cross-schema
  queries.
- Flyway, DDL-only migrations. A merged migration is immutable; new work is a new `V<n>__...` file.
- JPA for simple find/save; a stored proc / native query only for complex or reporting reads.
- No money is handled here; any amount shown is rendered from the event's decimal-string value as-is —
  never parsed into a floating-point type. No `java.util.Date`; use `java.time` with an injectable `Clock`.
- Internal PKs are `bigint identity`; external identifiers are UUIDs.

**Package layout & boundaries**
- Package-by-feature under `com.themistra.notification`. Each module owns its entities, repositories,
  services, and API. ArchUnit enforces `api → application → domain` within a module and forbids
  cross-module entity imports. Shared plumbing lives only in `common` — no `core`/`util` dumping ground.

**Events & messaging**
- Kafka (AWS MSK). This service is primarily a **consumer**. Consumers are idempotent and dedupe on the
  source event key (Kafka is at-least-once) — duplicate delivery must never double-send.
- Event schemas live in `contracts/events/`, are versioned, and evolve backward-compatibly only.
  Deserialization models are generated from `contracts/` — never hand-written. A schema mismatch fails a
  contract test, not a production delivery.
- If this service ever publishes (e.g. delivery-status events), it does so through the **outbox** in the
  same transaction as the state change. Services depend only on `libs/` and `contracts/` — never on
  another service's source.

**Security**
- Zero trust: the in-app read/stream API validates a JWT as an OAuth2 resource server against the Auth
  Service JWKS and scopes results to the caller's `sub`. The public-endpoint set is an exhaustive,
  CI-enforced allowlist (`PublicEndpoints`: actuator only).
- Errors are RFC 9457 `application/problem+json` — no stack traces, no internal detail.
- Secrets (email-transport credentials, DB creds): injected by External Secrets Operator; none committed;
  gitleaks gate in CI; no AWS SDK secret-retrieval in application code.

**Observability**
- OpenTelemetry traces, Micrometer→Prometheus metrics, structured JSON logs with `trace_id`. Never log
  tokens, secrets, reset-token values, full API keys, or PII.

**Testing**
- Unit (plain JUnit, fixed `Clock`, capturing fake transport) → ArchUnit + contract → integration
  (Testcontainers: Postgres + Kafka). Contract tests validate consumed payloads against `contracts/`.

**Deployment**
- Multi-stage Docker → distroless JRE 21, non-root, read-only rootfs. EKS ≥ 2 replicas in one consumer
  group (idempotency makes rebalance redelivery safe); multi-replica scheduled jobs are ShedLock-guarded.
  Infra is AWS CDK (TypeScript).

**Process**
- Trunk-based; `main` always deployable. Material design changes require an ADR in `docs/adr/`.
- Non-custodial: this service holds no user funds and no private keys.

## Service-specific standing rules (durable, cross-feature)

- **Consume-only, no domain state.** This service reacts to events and renders what they carry; it
  initiates no invoices, accounts, or receipts, and makes **no synchronous cross-service call on the
  delivery path** (no long synchronous chains). The only local read is the recipient-contact projection,
  which is itself fed by consumed auth events.
- **Idempotent by event key is non-negotiable.** Every consumer dedupes; replaying any event twice yields
  exactly one delivery.
- **The delivery log is dispute-grade and append-only.** Every attempt on every channel is recorded with
  recipient, channel, source event key, template version, and outcome. A retry adds a new row; it never
  overwrites a prior attempt. This is the evidence for "was the merchant notified?".
- **No secrets or tokens in messages or logs** — including reset-token values beyond the single intended
  one-time link. Assertion-tested.
- **Channels sit behind one interface.** Email + in-app ship at launch; HMAC-signed merchant webhooks and
  mobile push slot in later without touching the delivery orchestrator.
- **Preference resolution has a safe default:** honour per-category channel opt-outs; absent a preference,
  apply the documented default — never "send on every channel", never fail. SECURITY-category email
  cannot be disabled. Retries are bounded and terminate in a dead-letter outcome, never an infinite loop.
- **Templates are versioned**, and the version used is recorded per delivery so a dispute can reconstruct
  exactly what the recipient was shown.

## Reusable procedures — reference, don't inline

Load the relevant Skill rather than restating: `idempotency`, `code-review`. Feature specs are authored
with the `spec-authoring` skill.
