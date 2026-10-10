# agents.md — Intelligence Service

Standing, durable rules for `services/intelligence`. This file is authoritative for this service.
A feature spec never restates these; it references this file and records only what is specific to the
feature. If a spec must override a rule here, it says so explicitly in its §4a (LOCKED).

## Platform rules (identical across all Themistra services)

**Language & build**
- Java 21, Spring Boot 3.5.4 (inherited from the root `/pom.xml` parent). No other JVM language.
- Maven multi-module (ADR-0002). Build: `mvn -pl services/intelligence verify`. The module is
  registered in the root `<modules>`. Convention sharing happens through the parent POM.

**Configuration**
- Flat `application.properties` only — never YAML.
- Config is bound to validated `@ConfigurationProperties` records; startup FAILS on missing/invalid
  values in non-local profiles. Profiles: `local`, `dev`, `staging`, `prod`. Local dev runs against
  Docker Compose (Postgres + Kafka); the external vision/forensics API calls (L-locked, §4a) use a
  capturing fake client in tests and CI — no real third-party call happens outside `prod`/`staging`.

**Persistence & schema**
- PostgreSQL only, one logical schema per service (this service owns `intelligence`). No
  cross-schema queries.
- Flyway, DDL-only migrations. A merged migration is immutable; new work is a new `V<n>__...` file.
- JPA for simple find/save; a stored proc / native query only for complex or reporting reads.
- Monetary/token amounts that pass through from a `chain.tx.*` event are rendered as-is (decimal
  string) — never parsed into a floating-point type, never re-derived. No `java.util.Date`; use
  `java.time` with an injectable `Clock`.
- Internal PKs are `bigint identity`; external identifiers are UUIDs.

**Package layout & boundaries**
- Package-by-feature under `com.themistra.intelligence`. Each module owns its entities,
  repositories, services, and API. ArchUnit enforces `api → application → domain` within a module
  and forbids cross-module entity imports. Shared plumbing lives only in `common` — no
  `core`/`util` dumping ground.

**Events & messaging**
- Kafka (AWS MSK) for this service's own published events (none consumed at launch — see
  design.md §4c for why `chain.tx.*` consumption was replaced with a direct, polled lookup call).
  This service is a **caller** of crypto-service's own internal, on-demand transaction-lookup
  endpoint (`internal.crypto:write` scope, `spec/crypto-service` task 30) — never a direct database
  read against another service's schema.
- Event schemas live in `contracts/events/`, are versioned, and evolve backward-compatibly only.
  Deserialization models are generated from `contracts/` — never hand-written. A schema mismatch
  fails a contract test, not a production delivery.
- If this service publishes its own domain events (e.g. `intelligence.evidence.*`), it does so
  through the **outbox** in the same transaction as the state change, mirroring every other
  service's identical pattern. Services depend only on `libs/` and `contracts/` — never on another
  service's source.

**Security**
- Zero trust: every non-public endpoint validates a JWT as an OAuth2 resource server against the
  Auth Service JWKS. The public-endpoint set is an exhaustive, CI-enforced allowlist
  (`PublicEndpoints`: actuator only — there is no public, unauthenticated surface on this service).
- Errors are RFC 9457 `application/problem+json` — no stack traces, no internal detail.
- Secrets (the vision/forensics API key(s), DB creds): injected by External Secrets Operator; none
  committed; gitleaks gate in CI. The external API key is read only by the one client wrapper that
  calls it (§4a) — no other class in this service holds it.
- Uploaded evidence files are the most sensitive asset this service handles short of a secret
  itself (financial screenshots, receipts, personal chat excerpts) — never logged, never included
  in an error response body, access-controlled to the uploading account only until a future
  dispute-service grants a reviewer access (out of this service's own scope).

**Observability**
- OpenTelemetry traces, Micrometer→Prometheus metrics, structured JSON logs with `trace_id`. Never
  log evidence file contents/bytes, the vision/forensics API key, tokens, or PII extracted from
  evidence (a merchant name or wallet address extracted from a screenshot is domain data, not a
  secret, and may appear in structured fields — but the raw uploaded bytes and any raw API-response
  payload containing them never go to a log line).

**Testing**
- Unit (plain JUnit, fixed `Clock`, a capturing fake for the external vision/forensics client and
  for crypto-service's own lookup-endpoint client) → ArchUnit + contract → integration
  (Testcontainers: Postgres + Kafka; this service's own published events only — it consumes no
  Kafka topic at launch, design.md §4c). Contract tests validate this service's own call against
  crypto-service's `GET /internal/v1/transactions/{chain}/{txHash}` response shape (design.md §4c)
  and its own published `intelligence.evidence.correlation_updated` payload against
  `contracts/events/intelligence/`.

**Deployment**
- Multi-stage Docker → distroless JRE 21, non-root, read-only rootfs. EKS ≥ 2 replicas. Infra is
  AWS CDK (TypeScript).

**Process**
- Trunk-based; `main` always deployable. Material design changes require an ADR in `docs/adr/`.
- Non-custodial: this service holds no user funds and no private keys. It holds no custody of
  on-chain assets and makes no transaction-initiating call of any kind.

## Service-specific standing rules (durable, cross-feature)

- **This service is the evidence platform, not the dispute platform.** It interprets, scores, and
  correlates evidence; it does not decide outcomes, adjudicate claims, or own a dispute workflow —
  that is Dispute Service's own future scope (ARCHITECTURE.md §9), a new consumer of this service's
  own events, not a module here.
- **Evidence anchors to a transaction hash plus the uploading account — never to an invoice.**
  Payment-service's own invoice concept does not exist yet; this service's own scope must never
  depend on it. A future dispute-service may later join evidence to an invoice by whatever
  correlation key payment-service eventually exposes — that join lives there, not here.
- **No synchronous external call blocks a user-facing request longer than necessary to accept the
  upload.** Vision/forensics-API calls, and any poll of crypto-service's lookup endpoint, happen
  asynchronously after the upload/submission is accepted and stored — the response never waits on
  a third-party round trip.
- **The raw evidence file is immutable once stored (WORM, S3 Object Lock compliance mode) and is
  never re-derived from a later analysis.** Analysis results (extracted fields, integrity scores,
  correlation links) are a separate, versioned record alongside the immutable original — if the
  interpretation model/vendor changes later, the original evidence is untouched and re-analyzable.
- **A model/vendor response is evidence about evidence, not ground truth.** Every AI-extracted
  field and every integrity score carries a confidence value and the model/vendor identifier and
  version that produced it. Nothing downstream treats an extracted field as verified fact — only
  as a structured observation a human or Dispute Service weighs alongside the real on-chain facts
  collected from crypto-service's own quorum-checked lookup endpoint (design.md §4c).
- **On-chain facts always win over extracted/interpreted facts.** Where a collected on-chain fact
  and an AI-extracted field disagree (e.g. a screenshot claims a different amount than the chain
  shows), the on-chain fact is recorded as authoritative and the disagreement itself is surfaced as a
  correlation finding — never silently reconciled in either direction.
- **Idempotent by upload and by poll milestone.** Re-uploading byte-identical evidence, and a
  repeated poll of crypto-service's lookup endpoint returning the same milestone/outcome, each
  yield exactly one stored/updated record, never a duplicate (design.md L8).

## Reusable procedures — reference, don't inline

Load the relevant Skill rather than restating: `idempotency`, `code-review`. Feature specs are
authored with the `spec-authoring` skill.
