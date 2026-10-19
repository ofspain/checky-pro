# payment · T29 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`.

## Changes

### `spec/payment-service/package.md`

This is the sanctioned exception from Phase 4 D1. Only the header and §9 changed.

**Header (AC3):**
- Version `0.1` → `0.2`.
- Status `DRAFT` (plus its long pre-bump note) → `READY FOR IMPL`, followed by one disclosure sentence: R26 is deferred, the R28 receipt read is unbuilt (FU-T29-1), and §9 items 1, 13 and 14 have the detail.

**§9 (AC2):**

| Items | Treatment |
|---|---|
| 2–12 | `[x]`, with italic typed citations in crypto style: *(unit test / integration test / ArchUnit / contract test — names)* |
| 1 | `[ ]` **Partial —**. Gives 32/35 verbatim, names both cosmetic drifts by their real methods, defers R26 to item 14, and states that R28's receipt half is unbuilt (FU-T29-1). |
| 13 | `[ ]` **Partial —**. The payment half is proven. The crypto-service half is unproven (a WireMock stand-in) and is affected by the known crypto `unregisterWatch` defect. |
| 14 | Original text kept, plus a note on the interim reconciliation |

**Further notes:**
- **Item 11** cites "532/532 at commit `b8a654e8`" and "re-run at T29 Phase 6: 534/534". Both are labelled as snapshots (Kimi F6). The 534 is the real result of this phase's run.
- **Item 12** discloses the G2 partition-key drift.
- **Citation correction.** One citation was fixed before commit: `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch` lives only in `ChainEventConsumerTest`, not in `InvoiceServiceTest` as first drafted. `grep` caught it.

### `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`

This gains two tests, taking it from 9 to 11.

**`paymentSpecIsBumpedToReadyForImplVersionZeroPointTwo` (AC4):**
- The Version row is matched by an anchored row regex.
- The Status row is matched by prefix (Kimi F2).
- A negative check asserts there is no `` `DRAFT` `` in the Status row.

**`everyTestNamedInTheVerificationChecklistExists` (AC5, Kimi F4):**
- It slices §9 to §10, reads the `- [` lines, and skips the "Not applicable this revision" line.
- It extracts each `` `[Class].should…` `` citation and asserts that `void <name>(` exists under `src/test/java`.
- It also asserts at least 15 names were found. It finds **20**.

## Verification

**Mutations.** Each was restored and confirmed with `cmp`:

| Mutation | Result |
|---|---|
| M1: Version → `0.1` | header guard fails (`:342`, "Version must be 0.2") |
| M2: Status → `DRAFT` | header guard fails (`:344`, "Status must be READY FOR IMPL") |
| M3: one cited name misspelled (`…AtArchUnitX`) | citation guard fails (`:382`, "§9 cites tests that do not exist") |

**Full suite.** `mvn -o -pl services/payment verify` from the repo root: **534/534, BUILD SUCCESS**.

**Untouched**, as the frozen brief requires:
- `services/payment/src/main`
- the other `spec/` files
- `contracts/`
- `SECURITY-THREAT-MODEL.md`
- the `prompt` file

## Deviations

None.
