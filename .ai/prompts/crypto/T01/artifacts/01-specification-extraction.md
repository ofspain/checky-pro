<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# crypto · T01 · Phase 1 — Specification Extraction

## Business Rules

None directly scoped — T01 is pure skeleton/POM/threat-model work; no requirement ID (R1–R28) names
behavior this task implements. Every requirement is owned by a later task once real code exists.

## Locked Decisions

- **L11** — KMS-only signing, single path: "`kms:Sign` on the attestation key is reachable **only**
  from the attest module — enforced by ArchUnit (package ban) *and* by IAM." This task's own
  statement directly echoes this ("AWS KMS SDK — KMS only, scoped to the attest module") — the
  dependency must be added in a way that doesn't yet violate this (no code exists yet to violate it,
  but the *pom-level* framing should not suggest KMS is a general-purpose dependency for the whole
  service).
- **L13** — Secrets discipline: no provider API key, DB credential, or KMS key ARN is committed;
  External Secrets Operator injects them; validated `@ConfigurationProperties` fail startup on
  missing/invalid config in non-local profiles. Governs how this task's skeleton config (if any
  `application.properties` placeholder is created) must be shaped — no literal secret, even a
  local-only placeholder value, for anything KMS/provider-credential-shaped.
- **L15** — Module boundaries: package-by-feature under `com.themistra.crypto`, no cross-module
  entity imports, ArchUnit-enforced, "mirroring the auth service." Governs the skeleton's package
  layout, even though `design.md` §6's full 12-module map is a later task's job to populate — the
  skeleton itself should not preclude it.

## Files involved

**To create:**
- `services/crypto/pom.xml` — this task's principal deliverable.

**To modify:**
- `/pom.xml` (root) — add `<module>services/crypto</module>`.
- `SECURITY-THREAT-MODEL.md` (repo root) — update for threats #1–#6.

**Read-only (already reviewed at Phase 0, cross-referenced not restated):**
- `services/auth/pom.xml` — the literal mirror source.
- `spec/crypto-service/{package.md,requirements.md,design.md,agents.md}`.

## Dependencies

- **From mirroring `services/auth/pom.xml`** (confirmed content, Phase 0): `spring-boot-starter-web`,
  `-validation`, `-data-jpa`; `flyway-core` + `flyway-database-postgresql`; `postgresql`;
  `spring-kafka`; `software.amazon.awssdk:kms` (bom + artifact — already covers this task's own
  "AWS KMS SDK" instruction); `-actuator`; `micrometer-registry-prometheus`; `shedlock-spring` +
  `-provider-jdbc-template`; test-scope: `-test`, `spring-security-test`, `-testcontainers`,
  Testcontainers `postgresql`/`kafka`/`junit-jupiter`, `archunit-junit5`, `awaitility`.
- **Genuinely new for this task**: web3j (EVM client), a Tron gRPC client (TronGrid or `java-tron`
  gRPC stubs) — exact artifact coordinates not yet resolved (Phase 0 open item, carried below).
- **Judgment call, not a given**: whether `spring-boot-starter-oauth2-authorization-server` (issuer-
  side) is appropriate to mirror, versus only `spring-boot-starter-oauth2-resource-server` +
  `spring-security-oauth2-resource-server` (crypto-service validates auth-issued tokens per R27; it
  never issues its own). Carried to Phase 2/5 as a real design question, not assumed either way here.
- **Judgment call**: whether `bucket4j_jdk17-core` (auth's rate-limiting dependency) has any
  equivalent need in crypto-service's launch scope — nothing in `requirements.md`/`design.md` names
  a crypto-service rate-limiting requirement, so likely not needed; not assumed included by default.

## Acceptance Criteria

Derived from the task statement's own four clauses (no `package.md` §9 checklist item maps
one-to-one to T01 specifically — that checklist is written against the *finished* service):

| AC | Statement | Note |
|---|---|---|
| AC1 | `SECURITY-THREAT-MODEL.md` updated for threats #1–#6 | **Under-specified by the task statement itself** — the doc's current state is a stub with every threat marked `Status: designed`; nothing yet exists to mark `implemented`. What "updated" concretely means at the skeleton stage (confirm mitigation text still matches `design.md`'s now-locked mechanisms? add a task-number cross-reference column? something else?) is not pinned down by `tasks.md`'s one-line description. Carried to Phase 2/4 as a genuine open question, not assumed. |
| AC2 | `services/crypto` added to the root `<modules>` | Unambiguous — one `<module>` line. |
| AC3 | `services/crypto/pom.xml` created, mirroring `services/auth` plus web3j, Tron gRPC, AWS KMS SDK | KMS half already satisfied by a straight mirror (Phase 0 finding); web3j/Tron are the real additions. The SAS-starter question (above) needs a Phase 2/5 decision before this AC can be called fully specified. |
| AC4 | Java 21 virtual threads enabled | Unambiguous mechanically (`spring.threads.virtual.enabled=true` or equivalent Boot 3.5.4 config) — Phase 5's job to pin the exact property. |

## Tests required

None — `package.md` §8's named tests all map to R1–R28, none of which this task implements. No new
test is expected from a pom/config/doc-only skeleton task (matches auth-service's own T01-equivalent
precedent of "no feature code, no test surface").

## Open Questions

**Carried from Phase 0, not blockers for continuing to Phase 2 but requiring a decision before
Phase 4 freezes:**
1. Exact `SECURITY-THREAT-MODEL.md` update scope (AC1, above) — genuinely ambiguous from the task
   statement's own wording.
2. Exact web3j and Tron-gRPC-client artifact coordinates/versions — not named anywhere in the spec
   package; must be verified against real Maven Central listings before pinning (matching this
   session's own established discipline from auth-service T31's Bucket4j artifact-ID lesson), not
   guessed from memory.
3. Whether to mirror `spring-boot-starter-oauth2-authorization-server` or only the resource-server
   half.

**Not a blocker for this task specifically, but worth naming**: `package.md` §11's Q1–Q7 (provider
set, screening vendor, fail-open/closed posture, Tron confirmation semantics, watcher transport, the
anchor-write endpoint, KMS key algorithm) are all explicitly framed as blockers for *real deployment*
or for specific *later* tasks (T06/T07 providers, T19 screening, T20 KMS signer, etc.) — none of them
block T01's own skeleton/POM scope, and none are answered here.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
