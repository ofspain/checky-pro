<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T35 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **29/29 passed** |
| `python3 -I .ai/prompts/frontend/T35/artifacts/07-mutation-harness.py` | **14/14 regressions killed, 1/1 canary killed, 0 survivors, 0 broken** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

No production or test code was changed during this review.

---

## Findings

### I1 — `signOut()` is still uncalled in production
· **Severity:** **Blocker** (scope / process)
· **Evidence:** `frontend/src/auth/signOut.ts:31-38` states explicitly that nothing in the SPA calls it. `AppShell.tsx` renders a bare `<div>` and no authenticated route has an element.
· **Assessment:** The implementation is correct, idempotent, and thoroughly tested, but it is dormant. AC10 guards against partial sign-outs being hand-rolled elsewhere, which is a good substitute for a wiring test, but it does not change the fact that the function never runs in a production browser.
· **Recommendation:** Record this as OQ-T35-2 and ensure task 36 or 39 (or an explicit wiring task) has a handoff note. Do not claim AC1–AC6 are satisfied in production until a caller exists.

### I2 — `clearReturnTarget()` was added to a closed task's module without updating that task's tests
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/returnTarget.ts:161-167` adds a new exported function. The only references to it are in `signOut.ts` and `signOutClearsMemoryAndEndsSasSession.test.tsx`. `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` does not mention `clearReturnTarget`.
· **Assessment:** T24 owns `returnTarget.ts`. Adding a new export changes its contract. The function is simple, but T24's suite should assert the same contract it asserts for `takeReturnTarget`: idempotent, never throws, removes exactly `RETURN_TARGET_KEY`, never calls `clear()`. Without that, a future refactor of `returnTarget.ts` could break `clearReturnTarget` and only T35 would fail, making the failure look like a sign-out problem rather than a return-target problem.
· **Recommendation:** Add a small set of cases to T24's suite for `clearReturnTarget`, or at minimum a source scan that asserts the new function exists and matches the contract. The self-review (S3#4) discusses the T23 widening but does not mention this T24 widening.

### I3 — AC9's passing test asserts a real hazard exists
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:505-549` drives a real `BroadcastChannel`, signs out, posts a sibling renewal success, and asserts the token is re-adopted. The assertion message says "GOOD NEWS, AND THIS CASE MUST CHANGE".
· **Assessment:** This is the correct instrument available today. It proves the hazard, gives task 39 a failing expectation to replace, and prevents the defect from being forgotten. However, a reader skimming the suite will see a green test and may not read the assertion message. The file header explains it, but the case name could be clearer.
· **Recommendation:** Keep the case, but rename it to something like `KNOWN HAZARD a signed-out tab still adopts a sibling renewal (task 39 must replace this assertion)` so the intent is visible in the test report. The current name `adopts a sibling token AFTER sign-out, which is a session nobody asked for` is good but could start with `KNOWN HAZARD:`.

### I4 — AC7's module-scope mutable scan misses mutable `const` objects
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:421` uses `/^(let|var)\s/m` to find module-scope mutable state.
· **Assessment:** This catches `let` and `var` but not `const` declarations that hold mutable objects. A module could legitimately need tab-scoped mutable state as a `const config = { ... };` or `const cache = new Map();` and mutate it. Such state would not be cleared by `signOut()` and would not fail AC7. The self-review acknowledges closure state (S3#3) but not this `const` object case.
· **Recommendation:** Either narrow AC7's claim to "module-scope `let`/`var` declarations" and document that object mutations are out of scope, or extend the scan to also flag top-level `const` declarations whose types include mutable containers (`Map`, `Set`, arrays, objects with mutable fields). The simpler and more honest path is to narrow the claim.

### I5 — AC8's `themistra.` literal regex can miss dynamically constructed keys
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:473` uses `['"\`](${KEY_PREFIX}[\w.$-]*)['"\`]`. `NOT_STORAGE` and `CLAUSE_B_HANDOFF` handle known exceptions.
· **Assessment:** The scan finds literal strings. A key constructed by concatenation or template literal interpolation (e.g., `` `${KEY_PREFIX}auth.${name}` ``) would not match. The package currently uses constants, so this is not an immediate risk.
· **Recommendation:** Add a comment in the scan explaining that it only covers literal keys, and that any dynamic construction must be reviewed manually. Alternatively, forbid dynamic `themistra.` key construction by a source scan in T22.

### I6 — AC10's permitted-caller scan can miss indirect calls
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:561` uses `new RegExp(`\\b${primitive}\\s*\\(`)`.
· **Assessment:** This catches direct calls but not calls through an alias (`const drop = clearAccessToken; drop()`), a wrapper function, or a method reference passed as a callback. The code is stripped of comments, so a comment mentioning the primitive is safe.
· **Recommendation:** This is acceptable for the threat model — a partial sign-out would most likely call the primitives directly — but document the limitation. A more robust scan would parse the AST, but that is disproportionate here.

### I7 — The test file itself holds module-level mutable state
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:40` declares `let tokenSequence = 0;` at module scope.
· **Assessment:** AC7 scans production files, not tests, so this does not fail any criterion. But it is a mild inconsistency: the suite enforces a rule it does not follow.
· **Recommendation:** Move `tokenSequence` into a factory function or reset it in `beforeEach`. This is a tidy-up, not a correctness issue.

### I8 — `PERMITTED_CALLERS` could become stale
· **Severity:** **Minor**
· **Evidence:** `PERMITTED_CALLERS` lists `cleanReauth.ts` as a caller of `clearAccessToken`. If `cleanReauth.ts` is removed or stops calling it, the list would still claim it does.
· **Assessment:** Stale entries are harmless in one direction: they don't allow unauthorized callers. But they make the allowlist less precise over time.
· **Recommendation:** None required, but consider a control that proves each permitted caller actually calls the primitive. This would turn a stale entry into a failure.

### I9 — No test verifies `signOut` runs the later primitives if an earlier one throws
· **Severity:** **Minor/Major**
· **Evidence:** AC1 proves `clearAccessToken` runs before `consumeAuthorizeState` by making `consumeAuthorizeState` throw. AC12 proves sign-out does not throw when storage is denied. There is no case for "if `takeSignInNotice` throws, do the storage primitives still run?"
· **Assessment:** `takeSignInNotice` cannot throw today, so the case would not fail. But the module's comment says "Each later call is guarded inside its own module and so cannot stop another." A test that manufactures a throw from `takeSignInNotice` would pin that claim.
· **Recommendation:** Add a case that spies `takeSignInNotice` to throw and asserts `consumeAuthorizeState` and `clearReturnTarget` are still reached. This is the symmetric counterpart to the existing AC1 order test.

### I10 — The `AC4` source scan forbids `Promise` anywhere in the file
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:354` forbids `/\bPromise\b/`.
· **Assessment:** This is broader than needed: `Promise.resolve()` is harmless in a synchronous function but would fail the scan. The function does not use it.
· **Recommendation:** Narrow to `\basync\b` and `\bawait\b` plus timer/microtask patterns. The current broad scan is safe but could block a legitimate future change.

### I11 — The whole-suite baseline grew by 34 tests
· **Severity:** **Informational**
· **Evidence:** Self-review reports 1603 passed / 1 failed of 1604. T33's baseline was 1569 passed / 1 failed of 1570.
· **Assessment:** The growth is consistent with T35 adding 29 cases plus a few from cross-task edits. The environmental failure remains the same `oxlint` host case.
· **Recommendation:** None.

### I12 — T23's single-caller pin was widened with a strong justification
· **Severity:** **Informational**
· **Evidence:** `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx:812-846` now expects three callers and explains why sign-out is not a discard path.
· **Assessment:** The justification is sound: sign-out is a deliberate gesture, unreachable by a link, and leaving the PKCE triple behind would be worse. The self-review S3#4 correctly identifies this as the most consequential cross-task edit.
· **Recommendation:** None. The edit is well-documented.

---

## What the implementation gets right

- **S1 — Phase 3 F3 was accepted.** `clearReturnTarget()` was added to `returnTarget.ts` instead of misusing `takeReturnTarget()`, and SO07 proves the storage-count instrument catches the wrong choice.
- **S2 — The dormancy canary was dropped.** The test file header explains why a failing canary would obstruct the right thing, and AC10 substitutes a wiring-shape guard.
- **S3 — The SO03 survivor was closed properly** with both a behavioural case and a source-order case.
- **S4 — AC7 is honest about its limitation** and AC9 pins the closure-state hazard that the scan cannot see.
- **S5 — Every scan has a control**, and the inventory is derived from imported constants rather than hand-spelled keys.
- **S6 — The mutation harness is independently reproducible** with canaries scored apart.

---

## Summary

T35 is a careful implementation with strong tests and a sound mutation score. The overriding issue is **I1** (still uncalled in production), which is acknowledged but unresolved. The most important new finding is **I2**: adding `clearReturnTarget()` to `returnTarget.ts` without updating T24's suite creates a hidden contract change. **I3** is a process note on AC9's unusual but correct hazard-as-test. The other findings are smaller: scan limitations (**I4**, **I5**, **I6**), a missing symmetric order test (**I9**), and a tidy-up (**I7**).

**Phase 8 complete.**
