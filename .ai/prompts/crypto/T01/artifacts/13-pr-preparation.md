<!-- MODEL: Claude Sonnet — Phase 13 (PR Preparation). -->

# crypto · T01 · Phase 13 — PR Preparation

## Title

`crypto-service T01: threat-model tracking + Maven skeleton`

## Summary

- Registers `services/crypto` in the root Maven reactor (after `services/auth`, with an explicit
  dependency-order comment) and creates its `pom.xml`: auth's dependency set minus the OAuth2-issuer
  starter and the Flyway plugin, plus `org.web3j:core:6.0.0`, `io.github.tronprotocol:trident:1.0.0`,
  and an ADR-backed AWS KMS SDK dependency (`docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`,
  scoped to a future `attest` module's `kms:Sign` call only, mirroring ADR-0003's own discipline).
- Adds a bare `CryptoServiceApplication` so the module actually builds an executable jar
  (`mvn -pl services/crypto verify` — found broken with zero main classes on the classpath, fixed
  as a Phase 12 self-caught gap; no `@ConfigurationPropertiesScan`/`@EnableScheduling` yet, added
  when a class/job actually needs them).
- Enables Java 21 virtual threads globally (`spring.threads.virtual.enabled=true`).
- Updates `SECURITY-THREAT-MODEL.md`: threats #1–#6 move from `designed` to `tracked`, each mapped to
  the crypto-service task that closes it; #7–#8 (auth/payments concerns) untouched.
- Adds `T01SkeletonRegressionTest` (6 plain-JUnit guard tests, no feature-code equivalent yet) so
  this task's own AC1–AC4 fail CI if silently reverted, rather than relying on a human re-reading
  files in a later task. Every test individually mutation-tested to confirm it can actually fail.
- Extends `spec/auth-service/package.md` §12 with two dated re-confirmation notes tying this task's
  AC5 sibling-build check back to the pre-existing, already-accepted Kafka-relay-timing flakiness
  taxonomy — no new failure class, just a wider evidence trail.

## Files changed

**Created**
- `services/crypto/pom.xml`
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java`
- `services/crypto/src/main/resources/application.properties`
- `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java`
- `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`

**Modified**
- `pom.xml` (root — module registration)
- `SECURITY-THREAT-MODEL.md` (threats #1–#6)
- `spec/auth-service/package.md` (§12 — flakiness re-confirmation)

**Process artifacts** (not application code)
- `.ai/prompts/crypto/T01/artifacts/00-13-*.md` — full 14-phase pipeline record for this task.

## Test plan

- [x] `mvn -pl services/crypto validate` — clean.
- [x] `mvn -pl services/crypto dependency:resolve` — `web3j`, `trident`, `kms` all present and
      resolvable.
- [x] `mvn -pl services/crypto test` — 6/6 `T01SkeletonRegressionTest` pass; each individually
      mutation-tested (guarded condition broken → confirmed fails → reverted → confirmed clean).
- [x] `mvn -pl services/crypto verify` — passes, executable jar builds.
- [x] `mvn -pl services/auth verify` — run 3x total across this task (Phase 6 x2, Phase 9 x1
      targeted); zero files under `services/auth` touched at any point (`git status` re-confirmed
      each phase); failures are the pre-existing, now-doubly-documented Kafka-relay-timing
      flakiness class, not a regression from this task.

## Known, deliberate gaps (not this task's scope)

- No `services/crypto/src` feature code beyond the bare application class — every requirement
  (R1–R28) is owned by T02 onward.
- No Dockerfile for `services/crypto` — not named in this task's own statement or frozen brief;
  likely a later packaging/deploy task.
- `SECURITY-THREAT-MODEL.md`'s header line ("stub — must be completed before the first line of
  crypto-service code") is now slightly stale relative to the table below it — flagged at Phase 7,
  independently re-flagged at Phase 8 (Kimi), left as-is both times since AC1 scoped the table only;
  worth revisiting whenever T02 lands the first real feature code.
- `spec/crypto-service/package.md`'s own `Version 0.1` / `Status DRAFT` header is unchanged — that
  bump is the last task in the 29-task sequence (matching auth-service's own T40 precedent), not T01.

## Reviewer notes

- Two Phase 8 (Kimi) findings turned out to be inaccurate on closer verification against source —
  both fully traced and documented in `09-review-resolution.md`, not silently waved off:
  the "missing ADR" was a git-staging gap (file was real, just uncommitted at review time), and the
  "undocumented flaky tests" claim missed that `package.md` §12 already named all three tests. Worth
  a skim if reviewing Kimi's raw findings alongside this PR, so the same false read doesn't recur.
- Phase 11 (Kimi test review) correctly caught that Phase 10's original "no tests" call conflated
  "no feature tests" with "no tests for this task's own ACs" — the guard-test suite exists because
  of that catch.

---

**Phase 13 complete — PR description drafted, all phases 0–13 closed for crypto-service T01.**
