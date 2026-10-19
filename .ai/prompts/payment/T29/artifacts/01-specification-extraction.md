# payment · T29 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`. This artifact contains no code; it is documentation only.

## Business Rules

T29 targets no requirement of its own. It is governed by `tasks.md` task 29 (`:50`):

> Once `mvn -pl services/payment verify` (task 27) and the threat-model check (task 28) both pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`, mirroring the auth/crypto convention.

Because §9 restates them, these requirements are re-verified (not re-implemented) as the evidence for the bump:

| Requirement | Rule |
|---|---|
| R7a | Unregister the watch on the first `SEEN` payment. |
| R13 | Dedupe chain events by key. |
| R17/R18 | Receipts only from `FINALIZED`. |
| R21/R22 | Hash chain append and verify. |
| R23 | WORM receipts. |
| R26 | Anchor, **deferred** (Q4). |
| R28 | Merchant-scoped reads, including receipts. |
| R30 | Event topic routing. |
| R31 | OpenAPI contract. |

## Locked Decisions

None constrains the edit itself. §9 item 2 asks that every §4a LOCKED decision (L1–L14) was implemented as written. That was verified task by task through T01–T28 and is cited, not re-derived. The ones §9 names explicitly are:

| Decision | Subject |
|---|---|
| L2 | Receipt only from `FINALIZED` |
| L6 | No floating-point money |
| L7 | Append-only ledger |
| L9 | WORM receipts |
| L11 | No KMS or chain RPC in this service |
| L12 | Module boundaries |
| L14 | Unregister on first `SEEN` |

## Files involved

**Modified (the deliverable).** `spec/payment-service/package.md`:
- the header (`:8` Version, `:9` Status)
- the §9 checklist (`:114`–`:127`)

**Added (precedent).** A header regression guard in `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`. This mirrors crypto T29's guard in `services/crypto/src/test/.../T01SkeletonRegressionTest.java`.

**Read only:** `tasks.md`, `requirements.md`, `design.md`, `SECURITY-THREAT-MODEL.md`.

## Dependencies

- **Build gate:** `mvn -o -pl services/payment verify`. Last run: 532/532, BUILD SUCCESS, at `b8a654e8`.
- **T27:** payment Dockerfile builds; image guard in place.
- **T28:** threat-model row #5 payment portion closed (`SECURITY-THREAT-MODEL.md:14`), with follow-ups FU-T28-1/2 closed and FU-T28-3 interim.

## Acceptance Criteria

The task text gives no numbered ACs. These are derived from the task text plus the crypto T29 precedent:

| AC | Criterion |
|---|---|
| AC1 | Gate: `verify` passes and the T28 threat-model check passes. Both hold. |
| AC2 | Every §9 item is addressed with a typed evidence citation. An item without full evidence stays visibly partial or unticked, with the reason, and is never ticked silently. |
| AC3 | Header: Version `0.1→0.2`, Status `DRAFT→READY FOR IMPL`. |
| AC4 | Regression guard: a test fails if the header regresses. |

## Evidence found per §9 item

| # | Item | Evidence | Verdict |
|---|---|---|---|
| 1 | §3 ACs have passing §8 named tests | 32/35 verbatim. 2 cosmetic drift (`shouldPreventCrossModuleEntityImports…IsCheckedDuringStandardBuild`; `shouldTransitionConfirmingToFinalizedOnChainTxFinalized`). R26 deferred. **R28 receipt half not built** (no receipt read endpoint; T07 renamed the test to `shouldScopeInvoiceReadsToOwningMerchant`) | **partial** |
| 2 | LOCKED decisions as written | per-task Phase 12 verifications T01–T28 | tick |
| 3 | §4c VERBATIM artifacts | `SchemaBaselineMigrationIntegrationTest`, `PaymentsOpenApiContractTest`, `PaymentEventPayloadContractTest`, `CryptoClientTest` | tick |
| 4 | Receipt only from `FINALIZED` | `ReceiptServiceTest.shouldIssueReceiptOnlyFromFinalizedState` / `.shouldNeverIssueReceiptBeforeFinalized`; E2E reorg tests `assertNoReceiptIssued` | tick |
| 5 | BigDecimal money, ArchUnit | `ArchitectureTest.shouldRejectFloatingPointMoneyTypesAtArchUnit` | tick |
| 6 | Idempotent consumers | `ChainEventConsumerTest.shouldDedupeDuplicateChainEventsByEventKey`, `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`, E2E finalized replay | tick |
| 7 | Chain verifies after full flow; tamper detectable | E2E `Intact`; `LedgerVerifierTest`; `ReceiptReconciler` (since `b8a654e8`) | tick |
| 8 | S3 Object Lock COMPLIANCE; no overwrite | T28 rules + `ReceiptStoreObjectLockIntegrationTest` (conditional put) | tick |
| 9 | No KMS client, no chain RPC | `ArchitectureTest.shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` | tick |
| 10 | Outbox same-tx; `EventTopics` total | `InvoiceServiceTest.shouldEmitInvoiceCreatedInSameTransactionAsInsert`, `EventTopicsTest.shouldRouteEachPublishedEventToItsPaymentsTopic` | tick |
| 11 | `mvn -pl services/payment verify` | 532/532 | tick |
| 12 | Contracts cover every endpoint and event | T24 contract tests. Disclose G2 partition-key drift (design §4c vs real keys, which the contracts document) | tick + disclosure |
| 13 | Unregister on first `SEEN`; second match produces no further event | Payment half proven: `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch`, E2E `DELETE` check. "No further event this service receives" depends on crypto-service. Its E2E is a WireMock stand-in, and a known crypto defect makes unregister stop *all* tracking of the matched tx | **partial**, cross-service |
| 14 | R26 not applicable | deferred, disclosed; interim reconciliation | keep as-is |

## Tests required

- **AC4 guard.** Asserts `package.md` declares Version `0.2` and Status `READY FOR IMPL`.
- **Existing suite** stays green.

## Open Questions

- **OQ1 — `spec/` edit.** The guardrail says "never modify `spec/`", yet the task's only deliverable is a `spec/` edit. Crypto T29 treated this as the sanctioned exception. **Human decision at Phase 4.**
- **OQ2 — bump with partials.** Should the bump go ahead with items 1 (R28 receipt half) and 13 (cross-service) honestly partial? Crypto bumped with item 13 partial. **Human decision at Phase 4.**
- **OQ3 — Status note.** The existing explanatory note in the Status cell (`:9`) becomes stale after the bump. Replace it, or keep it as history? Not a blocker.
