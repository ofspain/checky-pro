<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# crypto · T01 · Phase 8 — Independent Code Review

Reviewed the implementation (Phase 6 notes + actual touched files) and the self-review (Phase 7)
against the frozen brief AC1–AC5, `spec/crypto-service/agents.md`, `spec/crypto-service/requirements.md`,
and sibling `services/auth/pom.xml`.

Local Maven verification was not possible in this environment (`mvn` is not installed), so build
claims are evaluated from the committed files and the Phase 6 evidence recorded by the implementer.

---

## Findings

### 1. ADR-0004 file is missing — AC3 not satisfied

- **Issue:** The named ADR that is supposed to back the AWS KMS SDK exception for crypto attestation
  was not created, despite being required by the frozen brief and cited in the new POM.
- **Evidence:** `docs/adr/` contains only `0001`, `0002`, `0003`; there is no
  `0004-narrow-kms-exception-for-crypto-attestation.md`. `services/auth/docs/adr/` does not exist.
  `services/crypto/pom.xml:26` and `services/crypto/pom.xml:64` both cite `ADR-0004`.
  The Phase 7 self-review discusses the ADR's technical claim but never verified that the file exists.
- **Recommendation:** Create `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` matching
  the structure of ADR-0003, scoped to the future `attest` module and `kms:Sign` only, and commit it.
  If the location was intentionally moved from the frozen brief's `services/auth/docs/adr/` path,
  record that path correction explicitly in the Phase 8/9 resolution notes.
- **Confidence:** High.

### 2. AC5 verification relies on a non-green build

- **Issue:** The sibling-build acceptance criterion (`mvn -pl services/auth verify` still passes) was
  not satisfied with a green run. Phase 6 recorded 1 failure + 6 errors, and Phase 7 did not re-run
  after its own edit. While the edit only touched `services/crypto/pom.xml`, AC5 as written expects a
  passing build, not a mechanism argument.
- **Evidence:** `06-implementation-notes.md:51-74` records `Tests run: 707, Failures: 1, Errors: 6` on
  two consecutive runs, attributed to pre-existing Testcontainers/Kafka flakiness.
  `07-self-review.md:55-60` states no re-run was needed. The three additional Kafka-observation
  failures are not documented as accepted-flaky in any project doc referenced by the task.
- **Recommendation:** Get a clean `mvn -pl services/auth verify` run before closing AC5. If genuine
  pre-existing flakiness genuinely prevents a green run, record the specific failing tests and their
  accepted-flaky status in the appropriate project doc (e.g., `spec/auth-service/package.md` §12) so
  the exception is visible and auditable. Re-run AC5 after any reactor change, even a sibling-only one.
- **Confidence:** Medium.

### 3. Phase 7 self-review missed the missing ADR

- **Issue:** The self-review claims to have re-read all five touched files and verified ADR-0004's
  content, but the file is absent. This indicates the self-review did not include an explicit
  "file exists and is tracked" check for newly created artifacts.
- **Evidence:** `07-self-review.md:5-7` lists `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`
  among the "five touched files". `07-self-review.md:24-27` asserts the ADR's claim was verified.
  `git ls-files docs/adr/` shows only `0001`, `0002`, `0003`.
- **Recommendation:** Add an explicit artifact-existence check to future self-review checklists,
  especially for documentation files whose absence does not break the build.
- **Confidence:** High.

### 4. `spring-security-oauth2-resource-server` is a redundant explicit dependency

- **Issue:** `services/crypto/pom.xml:47-48` declares both `spring-boot-starter-oauth2-resource-server`
  and the lower-level `spring-security-oauth2-resource-server`. The starter already pulls the latter
  transitively. This is harmless and mirrors `services/auth/pom.xml:56-63`, so it is not a defect in
  the context of T01.
- **Evidence:** `services/auth/pom.xml:56-63` contains the same explicit pair; removing it from crypto
  alone would break the "mirror auth" convention.
- **Recommendation:** Leave as-is for T01. Optionally drop the explicit transitive in a future
  platform-wide POM cleanup if desired.
- **Confidence:** Low.

### 5. `SECURITY-THREAT-MODEL.md` header is now slightly misleading

- **Issue:** The header still says "Status: stub — must be completed before the first line of
  crypto-service code", but T01 has produced a buildable skeleton and updated threats #1–#6 to
  `tracked`. The Phase 7 self-review noted this and chose to leave it.
- **Evidence:** `SECURITY-THREAT-MODEL.md:3`; `07-self-review.md:46-53`.
- **Recommendation:** Either update the header to reflect "threats #1–#6 tracked; threats #7–#8
  designed" or add an inline TODO so T02 is not surprised. Not blocking for T01 because the scoped
  change was the table only.
- **Confidence:** Low.

---

## No other material issues

No Java source exists to review for logic, thread-safety, transaction boundaries, or state-machine
bugs. The POM dependency set (minus the missing ADR), root `<modules>` registration, virtual-threads
property, and threat-model table otherwise match the frozen brief and `agents.md`.
