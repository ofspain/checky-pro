<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# crypto · T01 · Phase 11 — Test Review

Reviewed `artifacts/10-test-generation.md` and the final task state (no Java source, no existing
`services/crypto/src/test/` files). The decision to generate **zero feature tests** is correct for the
T01 feature surface — `package.md` §8's 29 named tests all target R1–R28/L15, which are implemented in
T02+. However, T01's own acceptance criteria (AC1–AC4) are currently verified only by manual file reads
and Maven `validate`; none are build-gated by a JUnit test. The gaps below are low-cost guard tests that
would fail the build if those ACs regress before T02 adds real feature code.

---

## Coverage gaps & recommendations

### 1. AC1 — Threat-model table is not build-gated

- **Gap:** `SECURITY-THREAT-MODEL.md` rows #1–#6 could be reverted to `designed`, the new
  `Implementing task` column could be emptied, or the table structure could change without any test
  failing. `ARCHITECTURE.md` §6.7 and `agents.md` Process rule make this document a hard gate.
- **Why it matters:** The threat model is the stated prerequisite for all crypto-service code. A
  silent regression here would undermine the "tracked" status that justifies starting T02.
- **Suggested test:** `SecurityThreatModelT01Test` in `src/test/java/com/themistra/crypto/build/`.
  Parse the markdown table (e.g., with a simple row splitter), assert rows 1–6 have `Status == tracked`
  and a non-empty `Implementing task` cell, and assert rows #7–#8 are unchanged (`designed`). Keep the
  parser tolerant of whitespace/padding changes.

### 2. AC2 — Root `<modules>` registration is not build-gated

- **Gap:** `services/crypto` could be removed from the root `pom.xml` `<modules>` block, placed before
  `services/auth`, or the ordering comment could be lost without failing a test.
- **Why it matters:** `agents.md` requires the module to be registered in the root POM, and the
  dependency-order comment is the convention this task added. Dropping the module silently excludes
  `crypto-service` from the reactor.
- **Suggested test:** `RootPomModuleOrderTest` using Maven's `ModelReader` on the repo-root `pom.xml`.
  Assert that `<modules>` contains `services/auth` and `services/crypto` in exactly that order and that
  a comment above `<modules>` mentions dependency order.

### 3. AC3 — POM dependency set is not asserted beyond `mvn validate`

- **Gap:** `mvn -pl services/crypto validate` checks that the POM is well-formed and dependencies
  resolve, but it does not verify that the *correct* dependencies are present. `web3j`, `trident`, or
  `kms` could be removed, or the OAuth2 authorization-server starter could re-appear, without failing
  `validate`.
- **Why it matters:** T06/T07 adapter work and T20 KMS signing depend on these dependencies being in
  place. A silent omission would surface only when much later code fails to compile.
- **Suggested test:** `CryptoPomStructureTest` using `ModelReader` on `services/crypto/pom.xml`. Assert
  presence of `org.web3j:core`, `io.github.tronprotocol:trident`, and
  `software.amazon.awssdk:kms`; assert absence of `spring-boot-starter-oauth2-authorization-server`;
  assert the KMS dependency is managed by the `software.amazon.awssdk:bom` import.

### 4. AC3 — ADR-0004 linkage is not verified

- **Gap:** The POM cites `ADR-0004`, but nothing checks that the file exists or contains the expected
  scope (`kms:Sign` only, confined to the `attest` module). Phase 8/9 showed this link can be broken by
  git-hygiene issues.
- **Why it matters:** AC3 requires the KMS dependency to be "ADR-backed"; a missing or mis-scoped ADR
  defeats the named-exception discipline established by ADR-0003.
- **Suggested test:** `Adr0004PresenceTest` asserts `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`
  exists and contains the substrings `kms:Sign`, `attest`, and `software.amazon.awssdk:kms`.

### 5. AC4 — Virtual-threads property has no runtime assertion

- **Gap:** `spring.threads.virtual.enabled=true` is set in `application.properties`, but no test
  asserts it. A typo or accidental deletion would not fail the build.
- **Why it matters:** `agents.md` Language & build rule and the frozen brief explicitly require Java 21
  virtual threads for the watcher layer. Until T09 adds watcher code that can be asserted to run on a
  virtual thread, a property-level test is the only possible guard.
- **Suggested test:** `CryptoApplicationPropertiesTest` loads
  `services/crypto/src/main/resources/application.properties` via `java.util.Properties` and asserts
  `spring.threads.virtual.enabled` equals `true`. Phase 10 dismissed this as a tautology, but it is a
  valid regression guard for a one-line AC.

### 6. Dependency-version drift across `services/auth` and `services/crypto` is not guarded

- **Gap:** Several dependencies are manually pinned to match auth (`testcontainers.version` `1.21.4`,
  ShedLock `7.7.0`, ArchUnit `1.3.0`, Awaitility `4.2.2`, AWS BOM `2.50.2`). A future update in one
  module but not the other could diverge silently.
- **Why it matters:** Version drift between sibling modules creates inconsistent platform behavior and
  makes global upgrades harder.
- **Suggested test:** `SiblingPomVersionAlignmentTest` reads both POMs and asserts that the shared
  pinned versions above are identical. This is optional for T01 but cheap to add while the two POMs are
  intentionally aligned.

---

## What does **not** need a test in T01

- **package.md §8's 29 named tests** are correctly deferred — they verify R1–R28/L15, which have no
  implementation yet.
- **AC5** (`mvn -pl services/auth verify` is unaffected) is inherently a Maven build check, not a
  unit/integration test, and the Phase 6/9 evidence already addresses it.
- **ArchUnit/module-boundary tests** cannot exist without packages or code.
- **Contract tests** cannot exist until `contracts/api/crypto-internal.yaml` and
  `contracts/events/chain/*.schema.json` are authored in T23.

---

## Verdict

The "no tests generated" outcome is appropriate for the feature-test surface, but the acceptance
criteria for this task are left unguarded. Adding the six small build/docs-level tests above would make
T01's AC1–AC4 regressions fail in CI rather than relying on human file reads in future phases.
