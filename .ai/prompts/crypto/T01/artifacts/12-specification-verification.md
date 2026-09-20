<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# crypto · T01 · Phase 12 — Specification Verification

## Acceptance criteria (frozen brief)

| AC | Verified how | Result |
|---|---|---|
| AC1 | `SECURITY-THREAT-MODEL.md` re-read directly + `T01SkeletonRegressionTest` | Threats #1–#6 `tracked` with an owning task each; #7–#8 untouched. |
| AC2 | Root `pom.xml` re-read + `T01SkeletonRegressionTest` | `services/crypto` registered after `services/auth`, ordering comment present. |
| AC3 | `services/crypto/pom.xml` re-read + `T01SkeletonRegressionTest` + `mvn dependency:resolve` | web3j/Tron/KMS present, issuer starter absent, KMS ADR-backed (ADR-0004 exists, scoped, and — as of this phase — committed). |
| AC4 | `application.properties` re-read + `T01SkeletonRegressionTest` | `spring.threads.virtual.enabled=true`. |
| AC5 | `mvn -pl services/auth verify` x2 (Phase 6) + targeted re-run x1 (Phase 9) + `package.md` §12 update + `git status services/auth` empty (re-confirmed this phase) | No file under `services/auth` touched by this task at any point; failures are the pre-existing, now cross-confirmed Testcontainers/Kafka flakiness class, not a regression. |

## New finding this phase, resolved

Running `package.md` §9's own whole-service checklist item — "`mvn -pl services/crypto verify` passes" —
as a genuine end-to-end check (not assumed from `validate`/`test` alone) surfaced a real failure:
`spring-boot-maven-plugin:repackage` cannot produce an executable jar with zero `@SpringBootApplication`
classes on the classpath. Human-gate decision: added `CryptoServiceApplication` (bare
`@SpringBootApplication` + `main`, no scan/scheduling annotations — those get added by whichever task
first needs them, not mirrored from auth pre-emptively). `mvn -pl services/crypto verify` now passes
clean, jar builds. `git status services/auth` re-confirmed empty after this addition — AC5's evidence
is unaffected.

## Locked decisions (`design.md` §4a)

- **L11** (KMS reachable only from `attest`): no code exists to violate this yet; the pom-level framing
  (comment citing ADR-0004) and the ADR itself both correctly scope the dependency to a future module,
  not a general capability. Satisfied as far as a skeleton task can be.
- **L13** (secrets discipline): no secret, real or placeholder, in any new file — confirmed by re-read
  of all five touched/created files (pom, ADR, properties, root pom, threat model) plus the new
  application class. No Flyway plugin (which would have carried one, per Phase 4 Finding 6).
- **L15** (package-by-feature, no cross-module entity imports, mirroring auth): the one package that
  now exists (`com.themistra.crypto`, holding only the application class) doesn't preclude the
  12-module package map `design.md` §6 specifies. Nothing to violate yet.

## `package.md` §9 whole-service checklist — items relevant to T01

Every checklist item not listed below requires feature code that doesn't exist yet (quorum, KMS
signer, observation log, finality, reorg, token matching, event contracts) — correctly out of scope,
matching Phase 1's own finding that this checklist targets the *finished* service, not any one task.

- "No secret... committed" (L13) — verified above.
- "`mvn -pl services/crypto verify` passes... Docker image builds" — verify now passes (this phase's
  fix). No Dockerfile exists yet for `services/crypto`; not named in T01's own task statement or the
  frozen brief's files list, so out of this task's scope (likely a packaging/deploy task later in the
  29-task sequence, not named T01).

## Spec status

`package.md`'s own header (`Version 0.1`, `Status DRAFT`) is unchanged — matches the established
auth-service precedent where the spec status bump is the *last* task in the sequence (auth's T40),
not the first. Not touched here.

---

**Phase 12 complete — AC1–AC5 and L11/L13/L15 verified against final state; one new build-completeness
gap found and fixed.** Proceed to Phase 13 (PR Preparation) on approval.
