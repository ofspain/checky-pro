# payment · T29 · Phase 0 — Repository Understanding

Task: `spec/payment-service/tasks.md` task 29 (`:50`), the spec status bump. This artifact contains no code; it is documentation only.

## 1. Architecture summary

`services/payment` is a Spring Boot 3.5.4 / Java 21 module in the `com.themistra:checky-pro` reactor. Its parts:

- **Packages.** It has feature packages `invoice`, `payment`, `consumer`, `receipt` (+ `receipt.ledger`, `receipt.event`), `verification`, `events`, `export`, `common`.
- **Persistence.** Postgres schema `payments` through Flyway `V1`–`V10`. The app connects as `payment_app`, which has narrow grants (INSERT+SELECT only on `ledger_entries`/`receipts`/`verification_records`).
- **Events.** A transactional outbox (`events.OutboxRelay`) publishes the `payments.*` topics. Idempotent `chain.tx.*` consumers dedupe on `processed_events`.
- **Receipts.** Receipts are attested through Crypto Service `/internal/v1/attest`. They are stored WORM in S3 (`ReceiptStore`, COMPLIANCE, conditional put) and chained in `ledger_entries` (`LedgerService`/`LedgerVerifier`). Since the T28 follow-ups, `LedgerIntegrityJob` + `ReceiptReconciler` run daily.
- **Security.** It is an OAuth2 resource server against auth-service's JWKS. Errors use RFC 9457 `ApiExceptionHandler`.

## 2. What this task touches

T29 changes **no code**. Its whole deliverable is a status edit in `spec/payment-service/package.md`. Nothing else is in scope.

- **Header table** (`:8`, `:9`). Version is `0.1` and Status is `DRAFT`, followed by a long explanatory note. The note says the bump happens here, at the end of the pipeline.
- **§9 Verification checklist** (`:112`–`:127`). It has 14 items, all unticked. The last item is the "not applicable this revision" R26 note.

**Precedent.** Crypto's T29 (`b303d682`) edited `spec/crypto-service/package.md` in exactly this way:
- Version `0.1→0.2` and Status `DRAFT→READY FOR IMPL`.
- Each §9 item ticked with an italic evidence citation (test class/name, or "disclosed partial").
- Honest partials left visible rather than ticked.
- A regression guard for the header in `services/crypto/src/test/.../T01SkeletonRegressionTest.java` (the only test referencing `READY FOR IMPL`).

There is no equivalent payment guard yet.

## 3. Patterns to follow

- **Status-bump convention:** the crypto T29 precedent above, which this task's own text names ("mirroring the auth/crypto services' own identical convention").
- **Regression guards:** these live in `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`, which already holds the T27 Dockerfile guard.
- **Evidence:** cite named tests verified with `grep "void <name>("` against `services/payment/src/test`, the T28 Phase 13 practice.

## 4. Testing conventions

- **Gate.** The task's gate is `mvn -o -pl services/payment verify` from the repo root. Last run: **532/532, BUILD SUCCESS** (2026-10-19, after the T28 follow-up commit `b8a654e8`).
- **Unit tests** are plain JUnit + Mockito with a fixed `Clock`.
- **Integration tests** run on Testcontainers: Postgres 16, `apache/kafka:3.8.0`, LocalStack 3.8, WireMock 3.9.1.
- **ArchUnit** rules are `private static final ArchRule` plus plain `@Test` canaries, because of the Surefire `@ArchTest` bug.

## 5. Ground truth on the §9 items (found while reading; for Phase 1/2)

**§8 named tests: 32 of 35 exist verbatim.**

- **Deferred (R26):** `shouldAnchorLedgerHeadOnChainDaily`, as §8 states.
- **Exists under a drifted name (three).** Same convention as crypto's disclosed "cosmetic naming drift":

  | §8 name | Actual test |
  |---|---|
  | `shouldPreventCrossModuleEntityImports` | `ArchitectureTest.shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (+ failing-fixture canary), from the `@ArchTest` workaround |
  | `shouldTransitionToFinalizedOnChainTxFinalized` | `PaymentStateMachineTest.shouldTransitionConfirmingToFinalizedOnChainTxFinalized`, plus `PaymentServiceTest.shouldTransitionToFinalizedFromConfirming` / `…FromSeenWhenNeverConfirmed` |
  | `shouldScopeInvoiceAndReceiptReadsToOwningMerchant` | Renamed in T07 to `InvoiceServiceReadTest.shouldScopeInvoiceReadsToOwningMerchant`. **The receipt half of R28 is not built:** no receipt read endpoint exists (`InvoiceController` and `ExportController` are the only `@GetMapping`s). T07 logged it as a backlog note. This is a functional gap, not cosmetic. |

**Other open items any honest checklist must disclose:**

- **Watch unregister (R7a / L14).** It is proven on the payment side (`shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch`, and the E2E `DELETE /watches/{id}` check). However, a known crypto-service defect means `unregisterWatch` stops *all* tracking of the matched tx. The E2E uses a WireMock stand-in, so it proves nothing about the cross-service flow. It is a cross-service follow-up, not a payment-test gap.
- **Contracts item.** T24 contract tests exist. Partition-key drift (G2) is open: design §4c says invoiceUuid, while code and contracts use the real keys.
- **Docker (FU-T27-1).** The payment Dockerfile builds (T27). The sibling auth/crypto/notification Dockerfiles do not, but §9 here only asks about this service.
- **R26 deferred.** On-chain anchor not built; interim reconciliation in place since `b8a654e8`.

## 6. Known gaps / unknowns

- **Open Question — editing `spec/`.** Every phase's guardrail says "Never modify the specification files under `spec/`", but this task's only deliverable *is* a `spec/` edit. Crypto T29 treated the task as the sanctioned exception. I do not know whether the human wants the same here. To be confirmed at the Phase 4 gate, not assumed.
- **R28 receipt half.** I do not know whether the human wants it ticked, ticked-as-partial, or left unticked. The precedent is to leave honest partials visible.
- **Header note.** I do not know whether the existing long explanatory note in the Status cell should be kept, trimmed or replaced. Crypto's cell was a bare `DRAFT`, so there is no precedent.
