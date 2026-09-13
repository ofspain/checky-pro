# crypto · T21 · Phase 6 — Implementation Notes

## What changed

**Created (production code):**
- `resources/db/migration/V10__crypto_chain_cursors_chain_tx_hash_idx.sql` — additive index on
  `chain_cursors(chain, tx_hash)`.
- `attest/AttestOutcome.java` — enum `{SIGNED, BLOCKED, REFUSED}` with a package-private `DbConverter`
  mirroring `screening.ScreeningOutcome`'s exact shape.
- `attest/Attestation.java` — JPA entity mapping `chain.attestations`. `receiptDigest` uses
  `@JdbcTypeCode(SqlTypes.CHAR)` — see Deviations below.
- `attest/AttestationRepository.java` — package-private `JpaRepository<Attestation, Long>`.
- `attest/AttestRequest.java` — `{receiptDigestSha256, chain, txHash}` with `@Pattern` validation on
  the first two fields.
- `attest/AttestResponse.java` — `@JsonInclude(NON_NULL)`, `signed(...)`/`blocked(...)` factories.
- `attest/AttestationRefusedException.java` — fixed, generic message format.
- `attest/AttestExceptionHandler.java` — `409` mapping, mirrors `watch.WatchExceptionHandler`.
- `attest/AttestationService.java` — the full gate orchestration per the frozen brief's execution shape.
- `attest/AttestController.java` — `POST /internal/v1/attest`.

**Modified:**
- `quorum/QuorumDecisionService.java` — added `isAgreed(chain, txHash, factType)`.
- `watch/ChainCursorRepository.java` — added `findByChainAndTxHash(chain, txHash) -> List<ChainCursor>`.
- `watch/WatchService.java` — added `findChainCursors(chain, txHash)`.

**Created (tests, written alongside production code per this task's own established T19/T20
precedent):**
- `attest/AttestOutcomeTest.java`, `attest/AttestationTest.java`,
  `attest/AttestationRepositoryIntegrationTest.java`, `attest/AttestationServiceTest.java`,
  `attest/AttestControllerTest.java`.
- Extended `quorum/QuorumDecisionServiceTest.java` (+3 `isAgreed` tests) and
  `watch/WatchServiceTest.java` (+2 `findChainCursors` tests).
- Extended `watch/WatchRepositoryIntegrationTest.java` (+1 test proving the real, multi-row
  `findByChainAndTxHash` behavior against Postgres).

## Deviations from the plan, forced by reality

1. **A real bug in `AttestationService`, caught by its own test suite before self-review.** The first
   draft's `requireAllFactsAgreed` threw `AttestationRefusedException` directly, without persisting a
   `REFUSED` `Attestation` row first — contradicting the frozen brief's own explicit design ("any false
   → `AttestationRefusedException` (persisting `REFUSED` first)") and R20's "persist every outcome."
   `shouldRejectAttestWhenQuorumOrFinalityNotMet`'s own `verify(attestationRepository).save(...)`
   assertion failed with "zero interactions with this mock," catching it immediately. Fixed by threading
   `receiptDigest` into `requireAllFactsAgreed` and persisting before the throw.
2. **`Attestation.receiptDigest`'s JPA mapping needed two attempts.** `receipt_digest` is `CHAR(64)`
   (fixed-length, `bpchar`) in the DDL; Hibernate's default mapping for a `String` column is `VARCHAR`,
   and its schema *validation* (not creation — Flyway owns the real schema) failed at context startup
   with a type mismatch. A first fix using `columnDefinition = "CHAR(64)"` did not resolve it — that
   attribute only affects schema *generation*, not the type code Hibernate's validator itself checks
   against. The actual fix, verified by running the integration test: `@JdbcTypeCode(SqlTypes.CHAR)`.
3. **A pre-existing T20 test broke as a direct, disclosed ripple of this task's own package growth.**
   `KmsSignerSpringWiringTest.TestConfig` used `@ComponentScan(basePackageClasses = KmsSigner.class)` —
   harmless when `attest/` held only `KmsSigner`/`SignatureResult`, but this task added test classes with
   their own nested `@Configuration`s in the same package (e.g. `AttestationRepositoryIntegrationTest
   .TestConfig`, which also defines a `clock()` bean), and the broad scan swept those up too, colliding
   on the bean name (`BeanDefinitionOverrideException`). Fixed by narrowing to
   `@Import(KmsSigner.class)`, which registers exactly that one class. Caught by running the full module
   regression, not assumed to be unaffected.
4. **`ChainBaselineMigrationIntegrationTest`'s hardcoded Flyway-version-list assertion needed updating**
   for `V10` — the same category of unavoidable ripple T17/T18/T19 each hit with this identical file.

## Mapping to acceptance criteria

AC1 (`shouldReturnKmsSignatureFromAttestForValidDigest`), AC2 (`shouldReturnBlockedFromAttestOnSanctionedCounterparty`
+ `blockedIsOnlyReachableAfterQuorumAndFinalityPass`), AC3 (`shouldRejectAttestWhenQuorumOrFinalityNotMet`,
parameterized), AC4 (`screeningErrorRefusesRatherThanBlocking`, `aThrownScreeningExceptionRefusesRatherThanPropagating`),
AC5 (per-outcome persistence assertions throughout `AttestationServiceTest` + the KMS-failure
zero-persistence test), AC6 (`KmsSignerArchitectureTest` re-run, unmodified, still green), AC7 (both
repositories stay package-private — verified by compilation itself), AC8 (`multipleCursorsWith...`,
`emptyCursorListRefuses`, `allCursorsWithNullFromAddressRefusesRatherThanNpe`, plus the real multi-row
Postgres test in `WatchRepositoryIntegrationTest`), AC9 (`AttestControllerTest`'s validation tests), AC10
(`blockedResponseOmitsSigningFieldsEntirely`), AC11 (`refusalMessageNeverNamesTheSpecificFailedFact`).

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly.
`mvn -pl services/crypto test -Dtest=AttestOutcomeTest,AttestationTest,AttestationRepositoryIntegrationTest,AttestationServiceTest,AttestControllerTest,QuorumDecisionServiceTest,WatchServiceTest,WatchRepositoryIntegrationTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
— 106/106 pass. `mvn -pl services/crypto test -Dtest=ChainBaselineMigrationIntegrationTest,KmsSignerSpringWiringTest,KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest`
(ripple regression) — 28/28 pass. Full module regression (`mvn -pl services/crypto -am test`): 700
tests, 6 failures, all pre-existing and unrelated to this task (disclosed since T18/T19/T20). Zero
regressions.
