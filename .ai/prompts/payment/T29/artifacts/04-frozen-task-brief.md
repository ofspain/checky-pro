# payment · T29 · Phase 4 — Frozen Task Brief

Consumes:
- `artifacts/02-task-implementation-brief.md`
- `artifacts/03-design-challenge.md` (Kimi)

This artifact contains no code; it is documentation only. Once frozen, this brief is the only specification Phases 5–13 use.

## Human decisions (2026-10-19)

| # | Question | Decision |
|---|---|---|
| D1 | OQ1, Kimi F1: may T29 edit `spec/`? | **Approved as the sanctioned exception.** `tasks.md` task 29 assigns exactly this edit to this task, mirroring `spec/crypto-service/package.md` (crypto T29, `b303d682`). Only the `package.md` header and §9 change. Every other `spec/` file stays untouched. |
| D2 | OQ2: bump with partial items? | **Bump, with the partials shown.** §9 item 1 (the R28 receipt half) and item 13 (the cross-service half) stay unticked, each with its reason. That matches crypto, which bumped with its item 13 partial. The missing R28 receipt read endpoint is logged as follow-up **FU-T29-1**. |
| D3 | OQ3, Kimi F3 and F7: wording | **A short note, plus `[ ]` "Partial —".** The stale Status note is replaced by `READY FOR IMPL` followed by one disclosure sentence: R26 is deferred and the R28 receipt read is unbuilt. Partial items keep a real `[ ]` checkbox with a bold `**Partial —**` prefix. `[~]` is rejected because it is not a GFM checkbox and renders as literal text. |
| D4 | Kimi F2, F4, F5, F6 | **Accept F2, F4 and F6. Reject F5.** |

**Kimi F2: accepted.** It was checked against source first. The brief never planned line-number access, and crypto's guard already matches whole rows. F2 is accepted as a constraint:
- The header guard matches the table row by its leading cells, never by line number.
- The Status row is matched by prefix, because a disclosure sentence follows the value.

**Kimi F4: accepted.** A second guard extracts every `should…` test name cited in §9 and asserts that `void <name>(` exists under `services/payment/src/test/java`.

**Kimi F6: accepted.** The build is cited as "532/532 at commit `b8a654e8`", a dated snapshot rather than a live count.

**Kimi F5: rejected.** Checked against source: no `tasks.md` in this repo marks any task done. Crypto's task 29 line is unchanged since its bump. `tasks.md` stays out of scope.

## Task

- **Header:** bump `spec/payment-service/package.md` Version `0.1→0.2` and Status `DRAFT→READY FOR IMPL`.
- **§9:** address all 14 §9 items with typed citations, keeping partials visible.
- **Guards:** add two regression guards.

## Scope

**In:**
- `package.md` `:8` Version and `:9` Status
- `package.md` §9 (`:114`–`:127`)
- two new test methods in `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`

**Out:**
- all of `services/payment/src/main`
- `requirements.md`, `design.md`, `tasks.md`, `agents.md`
- `package.md` §8 and §11
- `contracts/` and `SECURITY-THREAT-MODEL.md`
- other services
- the untracked `prompt` file
- building the R28 receipt read (FU-T29-1)
- fixing crypto's unregister defect

## Acceptance Criteria

**AC1.** Both gates hold, and are cited:
- `mvn -o -pl services/payment verify` passes.
- T28 is closed: `SECURITY-THREAT-MODEL.md` row #5, plus follow-ups `b8a654e8`.

**AC2.** All 14 §9 items are addressed:

| Item | Treatment |
|---|---|
| 2–12 | `[x]`, with an italic typed citation |
| 12 | Also discloses G2 partition-key drift |
| 11 | Cites "532/532 at commit `b8a654e8`" plus the T29 re-run |
| 1 | `[ ] **Partial —**`. Names 32/35 verbatim, the two cosmetic name drifts, R26 deferred, and the R28 receipt half unbuilt (FU-T29-1). |
| 13 | `[ ] **Partial —**`. The payment half is proven. The second-match half is cross-service, unproven against real crypto-service (WireMock stand-in), with crypto's known unregister defect. |
| 14 | Kept, with a note on the interim reconciliation |

**AC3.** The header reads Version `` `0.2` ``. Status reads `` `READY FOR IMPL` `` followed by the short disclosure sentence.

**AC4.** The header guard fails if either value regresses. It is matched by row (F2) and mutation-proven.

**AC5.** The §9 citation guard fails if any cited `should…` name has no `void <name>(` in the payment test sources. It is mutation-proven (F4).

## Required Tests

- **`T01SkeletonRegressionTest`, header guard** (AC4).
- **`T01SkeletonRegressionTest`, §9 citation guard** (AC5).
- **Full `verify`** stays green: 532 + 2 = 534.

## Constraints

- **Partials stay visible.** No item is ticked without full evidence.
- **Citations are real.** Every cited name is verified by the AC5 guard and by `grep`.
- **No secrets** (L13).

## Follow-ups (logged, out of scope)

| ID | Follow-up |
|---|---|
| FU-T29-1 | R28 receipt read endpoint (merchant-scoped, `404` cross-merchant) and its test. |
| Existing | FU-T27-1, FU-T27-2, G2, FU-1, FU-2, R26 anchor, the crypto unregister defect. |

## Open Questions

No blockers.
