# payment · T28 · Phase 11 — Test Review Resolution

Consumes:
- `artifacts/11-test-review.md` (Kimi, Gaps 1–8)
- `artifacts/10-test-manifest.md`

**Human sign-off:** 2026-10-19. Every recommended option was chosen.

Each gap was checked against `ArchitectureTest.java` and the fixtures before it went to the
gate.

| Gap | Verified | Decision | Change |
|---|---|---|---|
| **1**: `putObject` allowed from `ReceiptStoreConfig` | True. Part (b) allowed `putObject` from any class. | **ACCEPTED** | `S3Client.putObject` is now allowed only when the caller is `ReceiptStore` (`isStore`). |
| **2**: `S3AsyncClient.putObject` allowed | True. | **ACCEPTED** | Only `S3Client` counts as the allowed owner (`onSyncClient`). Every `S3AsyncClient` operation, including `putObject`, is now a violation. |
| **5**: no fixture for `putObject` outside the store | True. | **ACCEPTED** | `RogueS3ObjectMutator` gains `putObjectOutsideReceiptStore` and `putObjectAsync`. The part (b)-only assertion now expects `RogueS3ObjectMutator.putObjectOutsideReceiptStore(`, `calls method <…S3Client.putObject(` and `calls method <…S3AsyncClient.putObject(`. |
| **3**: no receipt-side delete fixture | True. More serious than stated: a typo in `GUARDED_ENTITY_TYPES`' `Receipt` entry would have silently unguarded receipts, and no test would have failed. | **ACCEPTED, plus a real-guard test** | New `receipt/RogueReceiptRepositoryUser` (test tree; `ReceiptRepository` is package-private). It deletes directly and inside a lambda. New test `shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueriesGuardsTheRealRepositories` asserts that the guarded set in the real code is exactly `LedgerEntryRepository` and `ReceiptRepository`. |
| **4**: no receipt-side `@Modifying` / derived-delete fixture | True. | **ACCEPTED** | New `receipt/RogueModifyingReceiptRepository` (`@NoRepositoryBean`), with `@Modifying rewriteEverySignature` and `removeByPaymentId`. The D1 failing-fixture assertion expects both, plus `ReceiptRepository.delete(` and `RogueReceiptRepositoryUser.deleteInsideALambda(`. |
| **6**: `createNativeQuery` inside a lambda | The mechanism is already proven. A lambda body compiles to a synthetic method with an ordinary call, as `statementInsideALambda`, `copyInsideALambda` and `deleteInsideALambda` show. Method references on `EntityManager` are already proven by `removeByMethodReference`. | **REJECTED** | None. |
| **7**: automated check that the threat-model's test names exist | Brittle. Row #5 also cites crypto-service tests that aren't on this module's classpath. | **REJECTED** | None. The names were copied from source and are re-checked at Phase 12. |
| **8**: deferred items not tracked | Partly true. They were in the frozen brief and in row #5, but not in the manifest. | **ACCEPTED (documentation only)** | `10-test-manifest.md` gains an "Open follow-ups" section: FU-T28-1/2/3 and the infrastructure item. No `@Disabled` placeholder tests. |

`SECURITY-THREAT-MODEL.md` row #5 was updated to match. Prevention (2) now cites the
real-guard test. Prevention (5) now says "only `ReceiptStore` calls `S3Client.putObject` (exact
name, never the async client)".

## Files changed

- **`ArchitectureTest.java`:** D2 part (b) tightened; Javadoc updated; D1 and D2 fixture
  assertions extended; one new `@Test`. The class goes from 19 tests to 20.
- **New test fixtures:** `receipt/RogueReceiptRepositoryUser.java` and
  `receipt/RogueModifyingReceiptRepository.java`.
- **Extended test fixture:** `archtestfixtures/RogueS3ObjectMutator.java`.
- **Docs:** `SECURITY-THREAT-MODEL.md` (row #5) and `10-test-manifest.md`.
- **Unchanged:** production code, migrations and poms.

## Verification

- `mvn -o -q test -Dtest=ArchitectureTest` gives **20/20** on the real code.
- **Mutation proofs.** Each was temporary. The restore was confirmed with `cmp`, and a clean
  re-run returned exit 0.

| # | Mutation | Result |
|---|---|---|
| M12 | Typo in the `Receipt` entry of `GUARDED_ENTITY_TYPES` | **Both** the new `…GuardsTheRealRepositories` test and the D1 failing-fixture test fail. |
| M13 | `isStore` dropped, so `putObject` is allowed from any class | The D2 failing-fixture test fails. |
| M14 | Async client counted as an allowed owner | **Passed.** This was expected, and it is a limit of the proof, not of the rule. The fixture's async `putObject` is still rejected, because the fixture is not `ReceiptStore`. The async ban therefore only matters *inside* `ReceiptStore`, and no fixture can sit at that fully-qualified name. Gap 2 is enforced by construction (`onSyncClient` is the `S3Client` owner check, and code reading confirms it). The real-code canary proves that `ReceiptStore` itself uses only `S3Client.putObject` today. No separate mutation proof is possible without a class at `ReceiptStore`'s name. |

- **Full suite:** `mvn -o -pl services/payment verify` from the repo root: `Tests run: 505, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` in 01:29. That is 497 baseline + 8.

## Open Questions

None.
