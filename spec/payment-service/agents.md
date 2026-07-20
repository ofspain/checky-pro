# agents.md — Payment & Verification Service

Standing, durable rules for `services/payment`. This file is authoritative for this service.
A feature spec never restates these; it references this file and records only what is specific to
the feature. If a spec must override a rule here, it says so explicitly in its §4a (LOCKED).

## Platform rules (identical across all Themistra services)

**Language & build**
- Java 21, Spring Boot 3.5.4 (inherited from the root `/pom.xml` parent). No other JVM language.
- Maven multi-module (ADR-0002). Build: `mvn -pl services/payment verify`. The module is registered
  in the root `<modules>`. Convention sharing happens through the parent POM, not per-module config.

**Configuration**
- Flat `application.properties` only — never YAML.
- Config is bound to validated `@ConfigurationProperties` records; startup FAILS on missing/invalid
  values in non-local profiles (no silent defaults). Profiles: `local`, `dev`, `staging`, `prod`.
  Local dev runs against Docker Compose (Postgres + Kafka); no shared dev database.

**Persistence & schema**
- PostgreSQL only, one logical schema per service (this service owns `payments`). No cross-schema
  queries, ever.
- Flyway, DDL-only migrations. A merged migration is immutable; new work is a new `V<n>__...` file.
- JPA for simple find/save; a stored proc / native query only for complex or reporting reads.
- Money is `NUMERIC` / `BigDecimal` — never `float`/`double`/`Double`/`Float`. On the wire, monetary
  values are decimal strings, never JSON numbers. Token identity is `<chain, contractAddress>`, never
  a symbol. Internal PKs are `bigint identity`; external identifiers are UUIDs and never leak internal PKs.
- No `java.util.Date`; use `java.time` with an injectable `Clock`.

**Package layout & boundaries**
- Package-by-feature under `com.themistra.payment`. Each module owns its entities, repositories,
  services, and API. ArchUnit enforces `api → application → domain` within a module and forbids
  cross-module entity imports. Shared plumbing lives only in `common` — no `core`/`util` dumping ground.

**Events & messaging**
- Kafka (AWS MSK). Every state change other services care about is published through the **outbox** in
  the same transaction as the DB write; a relay publishes to Kafka. No direct producer call from domain code.
- Topic naming `<domain>.<entity>.<event>`. Event schemas live in `contracts/events/`, are versioned,
  and evolve backward-compatibly only. Models are generated from `contracts/` — never hand-written.
- Consumers are idempotent and dedupe on the event key (Kafka is at-least-once).
- Services depend only on `libs/` and `contracts/` — never on another service's source.

**Security**
- Zero trust: every non-public endpoint validates a JWT as an OAuth2 resource server against the Auth
  Service JWKS. The service never assumes the edge authenticated the caller. The public-endpoint set is
  an exhaustive, CI-enforced allowlist (`PublicEndpoints`).
- Errors are RFC 9457 `application/problem+json` with a stable `type` catalogue — no stack traces, no
  internal detail, no enumeration hints.
- Secrets: injected by External Secrets Operator; none committed; gitleaks gate in CI; no AWS SDK
  secret-retrieval in application code.

**Observability**
- OpenTelemetry traces, Micrometer→Prometheus metrics, structured JSON logs with `trace_id`. Never log
  tokens, secrets, or PII.

**Testing**
- Unit (plain JUnit, fixed `Clock`, no Spring context) → ArchUnit + contract → integration
  (Testcontainers: Postgres + Kafka). Contract tests validate payloads against `contracts/`.

**Deployment**
- Multi-stage Docker → distroless JRE 21, non-root, read-only rootfs. EKS ≥ 2 replicas; multi-replica
  scheduled jobs are ShedLock-guarded. Infra is AWS CDK (TypeScript) — no console-created resources.

**Process**
- Trunk-based: short-lived branches into `main`; `main` is always deployable. Material design changes
  require an ADR in `docs/adr/`.
- Non-custodial: this service holds no user funds and no private keys.

## Service-specific standing rules (durable, cross-feature)

- **This service never touches a blockchain or KMS.** All chain facts arrive as `chain.tx.*` Kafka
  events; the receipt signature is obtained by calling the Crypto Service `POST /attest`. No RPC
  endpoint, no chain client (web3j/Tron), no KMS signing client is imported — ArchUnit bans them.
- **This is the core domain and the system of record.** The two platform failure modes — false
  attestation, and a credible claim that records were altered — are owned here.
- **A receipt is issued only from `FINALIZED`.** Never from `SEEN` or `CONFIRMING`. `ATTESTED` is set
  only after the signature is obtained, the receipt is stored in S3, and the ledger entry is appended.
- **Finality is not computed here.** React to `chain.tx.finalized`; never decide finality from
  confirmation counts.
- **The verification state machine is fixed:** `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED →
  ATTESTED`, with the only reversals being `CONFIRMING → SEEN` and `SEEN → WATCHING` on reorg. Reorg is
  a first-class transition, not an error.
- **`ledger_entries` is append-only** (hash chain: `prev_hash` links every entry). The service DB role
  has INSERT + SELECT only. Receipts are written once to S3 Object Lock (compliance mode) — no overwrite
  or delete path exists in application code.

## Reusable procedures — reference, don't inline

Load the relevant Skill rather than restating: `idempotency` (consumer dedupe), `stored-proc-dao`
(complex reads), `code-review`. Feature specs are authored with the `spec-authoring` skill.
