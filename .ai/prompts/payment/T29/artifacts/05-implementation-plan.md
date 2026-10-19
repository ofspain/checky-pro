# payment · T29 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md`. This artifact contains no code; it is documentation only.

## Steps

### 1. Header (AC3) — `spec/payment-service/package.md`

**`:8`.** `` | Version | `0.1` | `` becomes `` | Version | `0.2` | ``.

**`:9`.** The Status row is replaced with:

```
| Status | `READY FOR IMPL` — implemented and verified through tasks 1–29 (2026-10-19). Disclosed gaps: R26's on-chain anchor is deferred (Q4, task 21), and R28's receipt read is not built (follow-up FU-T29-1). See §9 items 1, 13 and 14. |
```

### 2. §9 checklist (AC2)

| Item | Treatment |
|---|---|
| 2–12 | `[x]`, each followed by an italic typed citation `*(unit test / integration test / ArchUnit / contract test — names)*`, crypto style |
| 1 | `[ ] **Partial —**` |
| 13 | `[ ] **Partial —**` |
| 14 | Original text kept, with a note appended |

**Citations must name only methods that exist.** That way the AC5 guard can check every `should…` name it finds on a checklist line.

**Two exceptions, so the guard stays exact:**
- **Item 14** keeps its deferred, unwritten `shouldAnchorLedgerHeadOnChainDaily`. The guard skips the line marked `Not applicable this revision`.
- **Item 1** describes the drifted §8 names in prose. It cites only their real implementations, never the §8 spelling in `should…` form.

### 3. Header guard (AC4) — `T01SkeletonRegressionTest.paymentSpecIsBumpedToReadyForImplVersionZeroPointTwo`

- **Reads:** `../../spec/payment-service/package.md`.
- **Version:** matched by row with `(?m)^\| Version \| `0\.2` \|\s*$`.
- **Status:** matched by row prefix with `(?m)^\| Status \| `READY FOR IMPL``. Matching by prefix tolerates the disclosure sentence after the value (Kimi F2).
- **Negative check:** no `DRAFT` value remains in the Status row.

### 4. Citation guard (AC5) — `T01SkeletonRegressionTest.everyTestNamedInTheVerificationChecklistExists`

1. Slice `package.md` from `## 9.` to `## 10.`.
2. Take the `- [` lines, skipping the `Not applicable this revision` line.
3. Extract each `` `…should\w+…` `` token. Take the last dotted segment, so `ArchitectureTest.shouldX` becomes `shouldX`.
4. Assert that `void <name>(` occurs somewhere under `src/test/java`.
5. Also assert at least 15 names were found. Without that, a parse that finds nothing would pass vacuously.

### 5. Verification

**Mutations,** each restored and confirmed with `cmp`:

| Mutation | Expected to fail |
|---|---|
| Version reverted to `0.1` | the header guard |
| Status reverted to `DRAFT` | the header guard |
| One cited name misspelled | the citation guard |

**Full suite:** `mvn -o -pl services/payment verify` from the repo root, expecting 534/534. Item 11's re-run figure is filled in from that real run.

## Risks

- **Item 14's deferred name.** The guard excludes that line explicitly. Excluding it any more broadly could hide a real typo.
- **`readString` path.** The module's tests run with the working directory at `services/payment`, the existing `ROOT_POM` convention, so `../../spec/...` resolves.
