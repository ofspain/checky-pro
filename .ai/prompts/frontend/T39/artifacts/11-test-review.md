<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T39 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/api/signOutPropagatesToAllTabs.test.ts src/api/siblingTabsShareOneRenewal.test.ts src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **136/136 passed** |
| `python3 -I .ai/prompts/frontend/T39/artifacts/07-mutation-harness.py` | **31/31 regressions killed, 1 unfalsifiable scored apart (XT18), 0 broken, 0 canaries** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1721/1722 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### F1 — FU-T39-1: the shared module-scope mutable scan has holes that affect 14 production files
· **Severity:** **Major**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:426-487` widened T39's own AC16 scan to catch exported `const` object/array/Set literals and typed `new Set<T>()` constructors, because XT32 survived the inherited pattern. The comments there list **fourteen** production files whose module-scope mutable declarations are invisible to the shared `MUTABLE_AT_MODULE_SCOPE` pattern, including `auth/accessToken.ts:46` (the session object) and `api/client.ts:6` (`new Set([…])`).
· **Assessment:** T39 fixed its own instrument but left the shared one unchanged because widening it would alter what four closed tasks are measured against and would turn an excused file's pinned count false. That is a defensible scope decision, but it means the gap is now recorded rather than closed.
· **Recommendation:** Either schedule a cross-task fix for the shared scan (updating the fourteen files' excuses and the one pinned count), or accept FU-T39-1 as a known limitation. The Phase 11 artifact should keep it visible.

### F2 — `quiet()` is still a fixed 50 ms guess, now honestly labelled
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:44-50` admits the helper is a fixed bound and that the previous comment was wrong. Every negative case now ends with a positive control on the same channel.
· **Assessment:** The controls remove the false-PASS risk for negative cases, but AC1, AC2's "every tab", and other positive cases still wait a fixed 50 ms for delivery. On a slow runner, a message that arrives at 60 ms would fail a positive case; on a fast runner, the suite pays 50 ms per case unnecessarily. The technique used in T35's `settle()` — observe delivery on a third endpoint first, then poll briefly — is the stronger instrument.
· **Recommendation:** Replace `quiet()` with an observe-then-poll helper for positive cases, and keep a short fixed bound only where no observer can be placed.

### F3 — No test directly asserts that `broadcastSignOut()` closes the channel it creates
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/crossTabSignOut.ts:115-131` creates a fresh `BroadcastChannel`, posts, and closes it in `finally`. AC16 asserts no module-scope channel, and XT03 verifies behavior when the channel is closed before posting.
· **Assessment:** A mutant that removed `own.close()` from the `finally` would pass every test: channels leak, but no case counts open channels. AC16 would still pass because the channel is not at module scope. This is an observability gap, not a behavioral one.
· **Recommendation:** Either accept it as unfalsifiable (like XT18) or add an observable side effect to `close()` that a test can read. Not critical.

### F4 — AC2's echo test uses `markSignedOut()` as the callback, not `signOut()`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:124` installs `listenForSignOut(() => markSignedOut(), …)` and asserts exactly one message is seen.
· **Assessment:** The test proves the callback does not re-broadcast, which is the structural guarantee. But the real receiving tab will call `signOut()`, and `signOut.ts` is source-scanned to prove it does not import `broadcastSignOut`. The combination is strong. A more realistic test would use `signOut()` as the callback and assert no echo, but that would also clear the access token and require setup/teardown.
· **Recommendation:** Optional. The current test plus the source scan is sufficient.

### F5 — No production code installs the listener
· **Severity:** **Blocker** (process, not code)
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:9-12` states explicitly that nothing installs the listener. `endSession()` has no caller (OQ-T36-5) and no module owns app-level listeners.
· **Assessment:** This is the same dormant-task limit T36/T37 had. The mechanism is built and correct, but "signing out in one tab signs out all open tabs" is not reachable in production.
· **Recommendation:** None for T39. Close when the sign-out affordance and an app-level listener owner are assigned.

### F6 — The harness scores XT18 correctly as unfalsifiable, but the wording could be clearer
· **Severity:** **Minor**
· **Evidence:** The harness output lists "regressions: 31/31 killed" and "unfalsifiable: 1" and "survivors: XT18". XT18 is the `close()` idempotency guard removal, which cannot be observed because double `removeEventListener` and double `BroadcastChannel.close()` are no-ops.
· **Assessment:** The phase 10 artifact explains this well. The harness output is slightly confusing because XT18 appears under "survivors" while also being "scored apart." A reader might think a survivor is a regression that escaped.
· **Recommendation:** Consider renaming the harness output line to "unfalsifiable:" instead of "survivors:" for rows scored apart. Low priority.

### F7 — The `AC6` degraded-path test does not assert `listenForSignOut` returns the same no-op object on repeated calls
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:210-226` asserts `close()` is callable twice without throwing.
· **Assessment:** This is sufficient for the degraded path. No gap.
· **Recommendation:** None.

### F8 — No test verifies the `catch` block in `broadcastSignOut` does not write to `console`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/crossTabSignOut.ts:122-128` catches and drops the error. AC10's source scan forbids `console.`, so a `console.error` would be caught.
· **Assessment:** The source scan is the guard. No runtime test is needed.
· **Recommendation:** None.

### F9 — The `AC4` JSON-stringify assertion is brittle to key order
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:196-197` asserts `JSON.stringify(seen[0])` equals `'{"type":"session:signed-out"}'`.
· **Assessment:** The object has only one property, so key order is deterministic. The assertion is fine and pins the exact wire shape.
· **Recommendation:** None.

### F10 — No test asserts `hasSignedOut()` returns `false` in a completely fresh module graph
· **Severity:** **Minor**
· **Evidence:** T35's AC9 throw case dynamically imports `crossTabSignOut` and asserts `hasSignedOut()` is false before signing out (`signOutClearsMemoryAndEndsSasSession.test.tsx:934`). T39's own `beforeEach` calls `markSignedIn()`.
· **Assessment:** The fresh-module assertion exists in T35. The `beforeEach` ensures a clean latch for T39's cases. No gap.
· **Recommendation:** None.

### F11 — The source scan for `markSignedIn` in `PERMITTED_CALLERS` uses the same direct-call regex as the other primitives
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:181-190` adds `markSignedIn: ['auth/accessToken.ts']`. The stray-caller sweep uses `/\b${primitive}\s*\(/`.
· **Assessment:** `markSignedIn()` is a function call, so the regex catches it. An alias or callback reference would pass, as the comments already note for the other primitives. This is accepted.
· **Recommendation:** None.

### F12 — The `AC9d` positional scan in `endSession.ts` now strips comments, but the stripper is duplicated rather than shared
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:69` defines a local `strip()` function. T35's suite has its own stripping logic.
· **Assessment:** The phase 10 artifact notes this as a decision for phase 10. Duplication is cheaper than an abstraction until it isn't. No correctness gap.
· **Recommendation:** None for T39. Extract to a shared helper if a third task needs it.

### F13 — No test verifies that the sign-out listener and the renewal listener can coexist on the same tab
· **Severity:** **Minor**
· **Evidence:** T39's suite tests sign-out propagation. T21's suite tests renewal. The new T21 case tests the interaction when signed out.
· **Assessment:** No case installs both listeners in the same tab and asserts neither interferes with the other. This is unlikely to be a problem because they use different channels and different callbacks, but it is an unexercised integration.
· **Recommendation:** Add a test that installs both listeners on the same tab, posts a renewal success and a sign-out, and asserts each is handled by the right callback. Low priority.

### F14 — OQ-T39-6 remains pinned, now as a measured fact
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:1003-1042` asserts AC9c: a stale sibling success overwrites a fresh token after a new sign-in.
· **Assessment:** This is correctly pinned as a hazard, not a fix. The phase 10 artifact notes OQ-T39-7 is now a measured fact too. No action needed in T39.
· **Recommendation:** None.

---

## What the test suite gets right

- **S1 — Phase 10 closed all major Phase 8 findings.** F1 (false comment), F2 (latch leak), F3 (negative controls), F4 (comment stripping), F5 (fire-and-forget throw), I6/I14 (OQ-T39-7 test), I8 (channel name), I9 (markSignedIn allowlist), and I10 (AC16 widening) are all addressed.
- **S2 — The harness correctly scores XT18 apart** rather than pretending an unfalsifiable mutant is a killed regression or a surviving defect.
- **S3 — The new T21 case kills XT27** and makes the guard placement a measured contract rather than a paragraph.
- **S4 — AC11's positive control** removes the false-PASS risk that the self-review and independent review identified.
- **S5 — The mutation score is independently reproducible** at 31/31 regressions killed plus one correctly scored unfalsifiable.

---

## Summary

T39's Phase 10 is strong. The most important remaining gap is **F1** (FU-T39-1: the shared module-scope mutable scan still has holes affecting fourteen production files). **F2** is a smaller instrumentation issue — `quiet()` is an honest guess, but positive cases would be more robust with an observer-first helper. **F3** is an observability gap around channel closure, similar in nature to the correctly scored XT18. The other findings are minor or already mitigated. The suite and gates are independently reproducible, and the one whole-suite failure is the known environmental `oxlint` host case.

**Phase 11 complete.** Fourteen findings returned; no code or contract changed.
