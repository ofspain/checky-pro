<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T35 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | **223/223 passed** |
| `python3 -I .ai/prompts/frontend/T35/artifacts/07-mutation-harness.py` | **18/18 regressions killed, 1/1 canary killed, 0 survivors, 0 broken** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1613/1614 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### F1 — No test can prove `signOut()` is wired in production
· **Severity:** **Blocker** (testability / scope)
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:12-19` states explicitly that nothing in the SPA calls `signOut`. AC10 guards against partial sign-outs being hand-rolled elsewhere, but it does not assert that `signOut` itself is called from any production module.
· **Assessment:** Every behavioral case is a statement about how the function behaves when invoked. The suite is honest about this limit, but the limit remains. AC10 is a good substitute for a dormancy canary, but it is not a substitute for an integration test that exercises the real wiring.
· **Recommendation:** Add a wiring assertion when task 36 or 39 introduces a caller. Until then, record in the phase 11 artifact that AC1–AC6 are verified only by direct invocation.

### F2 — AC9's hazard test uses a polling loop that can race or time out
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:662-664` polls `accessToken.current()` up to 50 times with 2 ms delays, because a single `setTimeout(0)` was flaky against `BroadcastChannel` delivery.
· **Assessment:** The comment explains the rationale well, and the test is better than a flaky single-tick assertion. But polling is still a source of non-determinism. On a slow runner, 100 ms may not be enough; on a fast runner, the loop may exit early and still assert the hazard exists. The test is also slow relative to the rest of the suite.
· **Recommendation:** Replace the poll with an explicit wait for a microtask/macrotask boundary that is known to follow BroadcastChannel delivery, or use a `MessageChannel`/`BroadcastChannel` message back to the test to signal arrival. If polling must stay, document the maximum wait and make it proportional to a constant.

### F3 — AC7's module-scope mutable scan misses plain-object declarations
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:483-484` uses `/^(?:let|var)\s|^const\s+\w+\s*(?::[^=\n]*)?\s*=\s*(?:new\s+(?:Map|Set|WeakMap|WeakSet)\s*\(|\[)/m`. The control plants `let leaked`, `const cache = new Map()`, and `const seen: string[] = []`.
· **Assessment:** The scan catches arrays and Map/Set containers but not `const state = { count: 0 };` or `const flags = { ready: false };`. A plain object at module scope is mutable state that persists for the tab and would not be cleared by sign-out. No production module has one today, so the scan passes, but a future addition would go undetected.
· **Recommendation:** Extend the scan to include top-level `const` declarations whose initializer is an object literal (`=\s*\{`), or add a control that proves a planted `const state = {}` is caught. If plain objects are intentionally excluded, document why.

### F4 — No test verifies `signOut.ts` does not import React or render JSX
· **Severity:** **Minor/Major**
· **Evidence:** AC11 scans `signOut.ts` for `fetch`, `XMLHttpRequest`, `react-router`, `t(`, `catalogue`, and `navigate`. It does not scan for `react`, `React`, `JSX`, or JSX syntax.
· **Assessment:** The file is `.tsx` only because task 36 will extend it, and it currently contains no JSX. A future edit could add a React import or JSX fragment without failing any existing test. The task's scope is "clearing half" only; a render would be out of scope.
· **Recommendation:** Add a source scan that asserts `signOut.ts` contains no JSX element (`<` not part of a type assertion) and no `react` import. This is a small addition that pins the file's current character.

### F5 — The test file and harness are still duplicated debt
· **Severity:** **Minor/Major**
· **Evidence:** The test file header at `:56-59` says the `strip` helper is the fourth copy in the package. The file is now 847 lines.
· **Assessment:** As noted in OQ-T32-4, duplication means a bug fix or behavior change in one copy does not propagate. The suite is correct, but the maintenance burden grows.
· **Recommendation:** Extract a shared source-scanning helper module before a fifth copy appears. This is additive and does not edit closed suites.

### F6 — AC12 covers only two storage-failure modes
· **Severity:** **Minor/Major**
· **Evidence:** AC12 has one case for a store that refuses every operation (`denyStorage`) and one case for `sessionStorage` being `undefined`. The criterion says "for any storage condition."
· **Assessment:** "Any" is not exhaustively covered. Other realistic conditions include `QuotaExceededError`, a store that throws only on `removeItem`, or a store whose methods return `null` instead of throwing. The two cases are the most important ones, but the wording overclaims.
· **Recommendation:** Either add cases for `QuotaExceededError` and a partial throw (e.g., only `removeItem` throws), or narrow the criterion to "a store that is absent or refuses every operation."

### F7 — The `AC8` literal scan does not verify keys are removed by signOut when present
· **Severity:** **Minor**
· **Evidence:** AC8 enumerates keys from source and asserts the inventory accounts for them. AC3 and AC6 assert sign-out removes the keys.
· **Assessment:** This is a separation of concerns, not a gap. AC8 finds keys; AC3/AC6 verify clearing. But a reader might expect AC8 to also trace each key to a test that proves sign-out clears it.
· **Recommendation:** Add a comment in AC8 cross-referencing AC3/AC6, or add a single case that iterates the inventory and asserts each key is removed by sign-out. The existing cases already cover this, so this is a readability recommendation.

### F8 — `AC6` does not distinguish `sessionStorage` from `localStorage` operations
· **Severity:** **Minor**
· **Evidence:** `watchStorage()` spies on `Storage.prototype`, which is shared by both `sessionStorage` and `localStorage`. The assertion `keysOf(watch.removeItem).sort()` checks the keys removed, not which store.
· **Assessment:** If signOut incorrectly called `localStorage.removeItem` with an inventory key, the keys list would still match and the test would pass, because both stores use the same prototype. A call to `localStorage.setItem` would fire `watch.setItem` and fail the "writes nothing" assertion, so writes are covered. Reads and removals are not store-distinguished.
· **Recommendation:** Add an assertion that `localStorage.length` is unchanged, or spy the store instances separately and assert neither is touched beyond the expected sessionStorage removals.

### F9 — The `AC10` caller allowlist does not verify `signOut.ts` is the only module that may call all four primitives together
· **Severity:** **Minor**
· **Evidence:** AC10 checks each primitive's callers individually. `signOut.ts` is permitted to call all four.
· **Assessment:** A future module could be added to `PERMITTED_CALLERS` for each primitive and effectively assemble a second sign-out. The scan would not flag it because each primitive's list is checked independently.
· **Recommendation:** Add an assertion that only `signOut.ts` appears in more than one primitive's permitted list, or that no other module is permitted to call all four. This pins the monopoly of the sign-out function.

### F10 — The mutation harness SO17 is fragile to `cleanReauth.ts` removal
· **Severity:** **Minor**
· **Evidence:** SO17 edits `frontend/src/auth/cleanReauth.ts` to remove its `clearAccessToken` call.
· **Assessment:** If `cleanReauth.ts` is removed or renamed, SO17's anchor will not match and the mutant will be marked BROKEN. The AC10 stale-caller test is the non-fragile version.
· **Recommendation:** None required; mutation harness anchors are expected to be coupled to source. But consider adding a comment that SO17 is the mutant counterpart of the AC10 stale-caller case.

### F11 — No test verifies `signOut` clears state when called while the tab is mid-renewal
· **Severity:** **Minor**
· **Evidence:** The boundary's `pending` map holds in-flight renewals. `signOut` does not interact with the boundary because it takes no parameter.
· **Assessment:** This is out of scope for T35, but the hazard exists: a tab that signs out while a renewal is in flight will clear its token, but the boundary may still dispatch waiting calls. Not a T35 defect.
· **Recommendation:** Document that boundary-state cleanup is owned by task 39 or the boundary wiring, not T35.

### F12 — The `AC9b` test checks the hazard message but not the action instruction
· **Severity:** **Minor**
· **Evidence:** AC9b asserts the hazard case source contains `task 39` and `OQ-T35-3`.
· **Assessment:** It does not assert that the message tells the author to replace the case with an assertion that the token stays null. The message does say that, but the test doesn't pin the instruction.
· **Recommendation:** Extend AC9b to assert the message also contains "replace this case" or "token stays null".

### F13 — The whole-suite baseline has the known environmental failure
· **Severity:** **Informational**
· **Evidence:** `vitest run` reports 1613 passed / 1 failed of 1614, the same `oxlint` host case.
· **Assessment:** Unrelated to T35.
· **Recommendation:** None.

---

## What the test suite gets right

- **S1 — Phase 10 closed the material gaps.** AC11's false clause was corrected, T24 now holds `clearReturnTarget`'s contract, and AC7/AC8/AC10 have both-directions checks.
- **S2 — The SO03 survivor hole is now pinned from both ends**: a behavioral case showing the token clears first, and a corrected case showing later throws abandon residue (not a token).
- **S3 — Every scan has a control**, including the new dynamic-key ban and the stale-caller check.
- **S4 — The hazard test is explicit and named**, with a separate AC9b pinning the message addressee.
- **S5 — The mutation harness is independently reproducible** with canaries scored apart and coarse kills verified narrowly.
- **S6 — The idempotence case compares whole observable state** rather than a list of nulls, closing the AC2 gap Phase 10 found.

---

## Summary

Phase 10 produced a strong, corrected test suite. The most important remaining test gaps are **F1** (no wiring test — acknowledged but uncloseable until a caller exists), **F2** (AC9's polling loop is pragmatic but non-deterministic), and **F3** (AC7's mutable-state scan misses plain-object declarations). Smaller gaps include F4 (no JSX/React scan), F6 (AC12 overclaims "any storage condition"), and F8 (AC6 does not distinguish sessionStorage from localStorage). The mutation score and gates are independently reproducible.

**Phase 11 complete.**
