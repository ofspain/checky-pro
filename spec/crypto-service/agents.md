# agents.md — Crypto Service

Standing, durable rules for `services/crypto` (**CODEOWNERS-protected**). This file is authoritative
for this service. A feature spec never restates these; it references this file and records only what is
specific to the feature. If a spec must override a rule here, it says so explicitly in its §4a (LOCKED).

## Platform rules (identical across all Themistra services)

**Language & build**
- Java 21, Spring Boot 3.5.4 (inherited from the root `/pom.xml` parent). Virtual threads are used for
  the watcher layer. The only permitted second language is a **translation-only** TypeScript chain
  sidecar under `sidecars/` (see service-specific rules) — never a primary implementation language.
- Maven multi-module (ADR-0002). Build: `mvn -pl services/crypto verify`. The module is registered in
  the root `<modules>`. Convention sharing happens through the parent POM.

**Configuration**
- Flat `application.properties` only — never YAML.
- Config is bound to validated `@ConfigurationProperties` records; startup FAILS on missing/invalid
  values in non-local profiles. Profiles: `local`, `dev`, `staging`, `prod`. Local dev runs against
  Docker Compose (Postgres + Kafka) and scripted **fake provider adapters** — real RPC providers are
  never called in tests or CI.

**Persistence & schema**
- PostgreSQL only, one logical schema per service (this service owns `chain`). No cross-schema queries.
- Flyway, DDL-only migrations. A merged migration is immutable; new work is a new `V<n>__...` file.
- JPA for simple find/save; a stored proc / native query only for complex or reporting reads.
- Money / base-unit values are `NUMERIC` / `BigDecimal` — never floating point. On the wire they are
  decimal strings, never JSON numbers. Token identity is `<chain, contractAddress>`, never a symbol.
- No `java.util.Date`; use `java.time` with an injectable `Clock`.

**Package layout & boundaries**
- Package-by-feature under `com.themistra.crypto`. Each module owns its entities, repositories,
  services, and API. ArchUnit enforces `api → application → domain` within a module and forbids
  cross-module entity imports. Shared plumbing lives only in `common` — no `core`/`util` dumping ground.

**Events & messaging**
- Kafka (AWS MSK). Every emitted fact is published through the **outbox** in the same transaction as the
  DB write; a relay publishes to Kafka. No direct producer call from domain code.
- Topic naming `<domain>.<entity>.<event>`. Event schemas live in `contracts/events/`, are versioned,
  and evolve backward-compatibly only. Models are generated from `contracts/` — never hand-written.
- Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype`; consumers dedupe.
- Services depend only on `libs/` and `contracts/` — never on another service's source.

**Security**
- Internal endpoints (`/internal/v1/*`) require a service-to-service JWT with the `internal.crypto:write`
  scope, validated as an OAuth2 resource server against the Auth JWKS. The public-endpoint set is an
  exhaustive, CI-enforced allowlist (`PublicEndpoints`: actuator + the verification-keys well-known path).
- Errors are RFC 9457 `application/problem+json` — no stack traces, no internal detail.
- Secrets (provider API keys, DB creds, KMS key references): injected by External Secrets Operator; none
  committed; gitleaks gate in CI. No AWS SDK use may expose key material outside the attest path.

**Observability**
- OpenTelemetry traces, Micrometer→Prometheus metrics, structured JSON logs with `trace_id`. Never log
  secrets or provider keys. Per-chain watcher lag and provider-disagreement rate are **paged** metrics.

**Testing**
- Unit (plain JUnit, fixed `Clock`, scripted fake `ChainAdapter`s) → ArchUnit + contract → integration
  (Testcontainers: Postgres + Kafka, fake providers). Contract tests validate payloads against `contracts/`.

**Deployment**
- Multi-stage Docker → distroless JRE 21, non-root, read-only rootfs. EKS ≥ 2 replicas; multi-replica
  scheduled/leased work is ShedLock-guarded. Infra is AWS CDK (TypeScript).
- IAM: **only the Crypto Service role may call `kms:Sign`** on the attestation key; an alarm fires on any
  use of that key outside this role.

**Process**
- Trunk-based; `main` always deployable. Changes to `services/crypto`, `contracts/`, or security stacks
  require CODEOWNERS review. Material design changes require an ADR in `docs/adr/`. Every feature PRs an
  update to `SECURITY-THREAT-MODEL.md` or states why none is needed.
- Non-custodial: no user funds, no user private keys. The only key is the platform attestation key, in KMS.

## Service-specific standing rules (durable, cross-feature)

- **This is the only component that talks to blockchains**, and the only holder of the path to
  `kms:Sign`. Everything that could cause a false attestation is concentrated here on purpose.
- **No single-provider answer ever leaves the service as fact.** Every emitted fact required ≥ 2-of-3
  agreement across independent providers. Disagreement → `HELD`, ops-alerted, never auto-resolved and
  never resolved in any party's favor.
- **Every provider response is logged verbatim to the observation log before the quorum decision**
  (Postgres `chain` schema + S3 snapshot), so any past attestation can be re-derived and defended. The
  observation log is append-only (INSERT + SELECT only).
- **Finality is a per-chain policy object, not a global constant** (Ethereum = beacon `finalized`
  checkpoint; Tron = solidified block). Adding a chain adds a policy object.
- **`kms:Sign` is reachable only from the `attest` module** — enforced by ArchUnit and by IAM. Attest
  refuses to sign unless quorum + finality + screening passed. Screening fails **closed** (no signature).
  The sign path stays Nitro-Enclave-portable (no host-only assumptions).
- **Sidecars are translation-only:** one chain, one process, disposable; no quorum authority, no signing
  access, no business state. The Java core treats sidecar output as one more provider answer under quorum.

## Reusable procedures — reference, don't inline

Load the relevant Skill rather than restating: `idempotency`, `stored-proc-dao`, `code-review`. Feature
specs are authored with the `spec-authoring` skill.
