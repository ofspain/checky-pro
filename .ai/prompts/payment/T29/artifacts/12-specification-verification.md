# payment · T29 · Phase 12 — Specification Verification

Consumes:
- artifacts `00`–`11`
- `spec/payment-service/tasks.md` task 29 (`:50`)
- `requirements.md` and `design.md`, for the items §9 re-verifies

This artifact contains no code; it is documentation only.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| Task 29 gate: `mvn -pl services/payment verify` passes | Yes | Full run at Phase 11: 535/535, BUILD SUCCESS | the whole suite | — | No |
| Task 29 gate: threat-model check (task 28) passes | Yes | `SECURITY-THREAT-MODEL.md:14`, row #5 payment portion closed; T28 Phase 12 PASS; follow-ups `b8a654e8` | `ArchitectureTest` T28 rules, `ReceiptStoreObjectLockIntegrationTest`, `ReceiptReconcilerTest`, `LedgerIntegrityJobTest` | — | No |
| Task 29: Version → `0.2` (AC3) | Yes | `spec/payment-service/package.md:6` | `T01SkeletonRegressionTest.paymentSpecIsBumpedToReadyForImplVersionZeroPointTwo` (`:340`) | — | No |
| Task 29: Status → `READY FOR IMPL` (AC3) | Yes | `package.md:9`, with the D3 disclosure sentence | same test | — | No. The disclosure sentence was approved at Phase 4 (D3). |
| AC2: all 14 §9 items addressed | Yes | `package.md:114`–`:127` | `everyTestNamedInTheVerificationChecklistExists` (`:365`), `verificationChecklistKeepsItsPartialAndDeferredItemsUnticked` (`:427`) | — | No |
| AC2: item 1 partial (R28 receipt half unbuilt) | Yes, disclosed | `package.md:114` | partial guard (`:427`, which also checks FU-T29-1) | **R28 receipt read endpoint** (FU-T29-1) | No. Approved at Phase 4 (D2). |
| AC2: item 13 partial (cross-service unregister) | Yes, disclosed | `package.md:126` | partial guard | real crypto-service proof; crypto `unregisterWatch` defect | No (D2) |
| AC2: item 14, R26 deferred | Yes | `package.md:127` | partial guard; exemption tied to item 14 | R26 anchor (Q4) | No |
| AC4: header guard, mutation-proven | Yes | `T01SkeletonRegressionTest:340` | M1, M2, M8, M16 | — | No |
| AC5: citation guard, mutation-proven | Yes | `T01SkeletonRegressionTest:365`, `:453` (`declaresTestMethod`), `:46` (`CITATION`), `:49` (`DEFERRED_UNWRITTEN_TESTS`) | M3, M4, M5, M9, M10, M11, M15 | — | No |
| Phase 8 F7, Phase 11 Gaps 2/5: partials stay partial, verified items stay ticked | Yes | `T01SkeletonRegressionTest:427` | M6, M7, M12, M13, M14 | — | No |

## Scope check

**Since the Phase 2 commit `e438931e`, T29 changed only:**
- **`spec/payment-service/package.md`:** two hunks, the header (`@@ -3,10`) and §9 (`@@ -111,20`). This is the sanctioned exception (Phase 4 D1).
- **`services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`:** three tests and their helpers.

**Untouched:**
- `services/payment/src/main`
- the other `spec/` files
- `contracts/`
- `SECURITY-THREAT-MODEL.md`
- the `prompt` file

**Kimi findings rejected:** Phase 3 F5 and Phase 8 F8 (both `tasks.md`). They were rejected by the human because no `tasks.md` marks tasks done.

## A small inaccuracy in earlier artifacts

Artifacts `00`–`06` locate Version at `package.md:8`. It is actually at `:6`; Status is `:9`, as stated. The edit itself targeted the row by content, not by line, so nothing was affected. This is disclosed here rather than rewriting committed artifacts.

## Principal-engineer review

1. **Is the task fully complete?** Yes.
   - Both gates hold.
   - The header is bumped to `0.2` / `READY FOR IMPL`.
   - All 14 §9 items are addressed with typed, guard-checked citations.
2. **Does it satisfy every acceptance criterion?** Yes, AC1–AC5.
   - AC4 and AC5 are proven by 16 mutations across Phases 6, 9 and 11.
   - The M13 marker check is reported honestly as a backstop, subsumed by the per-item assertions.
3. **Does it violate any LOCKED decision?** No. There is no production code. L13 holds: no secrets.
   - The one guardrail exception, editing `spec/`, was approved explicitly at Phase 4. It matches the crypto T29 precedent and the task's own text.
4. **Remaining risks:**
   - **Partial and deferred items.**
     - Item 1: R28's receipt read is unbuilt (FU-T29-1).
     - Item 13: the cross-service unregister is unproven, and crypto's `unregisterWatch` defect affects it.
     - Item 14: the R26 anchor is deferred.
   - **"Ticked" means "evidenced", not "proven correct".** The guards prove only that the cited tests exist and stay marked. Whether the evidence is sufficient is reviewer judgement.
     - Item 2 rests on process.
     - Item 10's outbox atomicity holds by construction; no rollback test exists.
   - **Open follow-ups:**

     | ID | Follow-up |
     |---|---|
     | FU-T27-1 | sibling Dockerfiles |
     | FU-T27-2 | `jwk-set-uri` |
     | G2 | partition-key drift |
     | FU-1 | layering |
     | FU-2 | `@PreAuthorize` sweep |
     | — | infra alert rules and IAM (T28 follow-ups) |

**PASS** — the spec is bumped to v0.2 READY FOR IMPL. Every §9 item is evidenced or honestly marked partial or deferred, and both gates hold at 535/535.
