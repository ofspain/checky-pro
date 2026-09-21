# crypto · T27 · Phase 9 — Review Resolution (Human Approval Gate)

## Kimi Phase 8 findings — dispositions

Re-verified directly against real source (not taken at face value). One finding was true only because
of a branch-integrity incident discovered while investigating it (see below) — reverting that incident
also reverted the finding's own premise.

| # | Finding | Disposition |
|---|---|---|
| 1 | Dockerfile exposes 8080 but service defaults to 8082 | **REJECTED — premise was itself corrupted.** True only against a `server.port=8082` line that turned out to belong to an incompatible parallel implementation merged into this branch mid-review (see "Branch-integrity incident" below). The real, restored `application.properties` has this only as a commented-out fragment; the real default is 8080. `EXPOSE 8080` is correct, unchanged. |
| 2 | Dockerfile compiles test sources during the production image build | **REJECTED** — `-DskipTests` (not `-Dmaven.test.skip=true`) preserves exact structural parity with `services/auth/Dockerfile`, a deliberate Phase 1/2 design choice; the concern is confined to the intermediate build-stage image, never the shipped distroless runtime layer (multi-stage build). Low confidence, no functional risk. |
| 3 | KMS-SDK rule's name-prefix exception is broader than an exact allowlist | **REJECTED** — already explicitly considered and documented in the class's own Javadoc (T25/T26-era reasoning: an exact-name list would need editing for every future legitimate `KmsSigner`-testing file; the package+prefix combination was chosen specifically to close the one real gap an independent review found). No new information. |
| 4 | Negative-proof test doesn't verify failure message content | **ACCEPTED.** `bothRulesActuallyFailAgainstAGenuineViolation` only asserted `isInstanceOf(AssertionError.class)`, which `CrossModuleEntityArchitectureTest`'s own equivalent negative-proof test does not do (it also asserts `.hasMessageContaining(...)`). Added `.hasMessageContaining("RogueAttestReferencer")` / `"KmsSigner"` / `"KmsClient"` to both assertions, verified directly against `RogueAttestReferencer`'s real two fields. |
| 5 | No smoke test that the built image starts | **REJECTED — already resolved.** Identical to T27 Phase 3 Finding #5, already accepted-with-correction in the Phase 4 frozen brief and restated in Phase 6's implementation notes. |
| 6 | Regression guard's stale-entry failure mode (`IllegalArgumentException`) isn't a friendly message | **REJECTED — contradicts established precedent.** `CrossModuleEntityArchitectureTest.allowlistedCrossModuleEntityDependenciesStillExistInCode`'s own Javadoc explicitly reasons that `analyzedClasses.get(...)` throwing `IllegalArgumentException` on a renamed/removed class satisfies "fails loudly" — this file's regression guard mirrors that exact, already-accepted design intentionally. |
| 7 | Scan-includes-tests behavior undocumented in a Phase 10 traceability matrix | **N/A** — T27 has no Phase 10 test-generation artifact; Phase 1 explicitly declared "no test authored, this task's own test is the verification commands themselves." |

## Branch-integrity incident discovered during this phase

While verifying Finding #1, a `git blame` on the newly-nonexistent `server.port=8082` line traced it to
a commit dated 2026-09-06 by a different author, part of a much larger merge (`189c4e3`) performed
between Phase 7 and this phase. Full investigation (`git diff --name-status a15a320 189c4e3 --
services/crypto/`) found the merge had replaced **64 files** under `services/crypto/` — including
`Watch.java`, `WatchService.java`, `WatchController.java`, `WatchRepository.java`, and
`application.properties` — with a separate, incompatible parallel implementation (wrong `Watch` entity
shape, an entirely separate `chain`/`config`/`dev` package tree, a `WatcherService` alongside the real
`Watcher`), the same "wrong feat-stack" pattern that has recurred via force-pushes throughout T24-T27,
this time landing via a genuine, clean, non-conflicting merge under the user's own git identity.

Presented to the user directly (not a routine gate, since it blocked all further Phase 9 verification
work). Per explicit instruction: `services/crypto/` was restored file-for-file to the last known-good
commit (`a15a320`, immediately pre-merge), while the one genuine, worth-keeping addition from that merge
— the `maven-failsafe-plugin` Docker-API-version pin (`api.version=1.44`, fixes a real
`docker-java`/Docker-Engine-29.x API-negotiation mismatch, unrelated to and independent of this
environment's own separate daemon-absence issue) — was manually re-applied to the restored `pom.xml`.

Full suite re-verified clean afterward: 696 tests, 0 failures, the same 14 already-disclosed Docker-only
errors as Phase 7 (`docker info`/`docker build` in this environment still fail at the daemon-connection
level, unaffected by the API-version pin).

## Verification performed

- `git diff --name-status a15a320 189c4e3 -- services/crypto/` — full inventory of the incident's scope.
- `git checkout a15a320 -- services/crypto/` + manual deletion of the 47 files the bad merge added that
  checkout alone cannot remove (checkout only restores/overwrites paths present in the target commit; it
  does not delete paths absent from it).
- `mvn -pl services/crypto -am verify` — 696 tests, 0 failures, 14 Docker-only errors (matches Phase 7
  exactly).
- `mvn -pl services/crypto -am test -Dtest=KmsSignerArchitectureTest` — 3/3 passing with the new message
  assertions.
