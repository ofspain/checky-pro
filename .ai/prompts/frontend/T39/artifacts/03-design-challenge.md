<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T39 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The latch is module-scope mutable state, and the brief does not say how to exempt it
· **Severity:** **Major/Blocker**
· **Evidence:** T35's test suite scans production files for module-scope mutable declarations and fails on a hit (`signOutClearsMemoryAndEndsSasSession.test.tsx:232-` and `NOT_SESSION_STATE`). `crossTabSignOut.ts` as proposed will hold `let signedOut = false` (or similar) at module scope to implement `markSignedOut()` / `hasSignedOut()`.
· **Assessment:** This is not an error in the design — a latch needs a place to live — but it is a direct collision with an existing instrument. The brief must state the exemption explicitly and update `NOT_SESSION_STATE` in T35's suite, or the suite will fail the moment `crossTabSignOut.ts` is added. The existing entries show the required shape: file, declaration count, and a justification that the state is not session state.
· **Recommended brief amendment:** Add `src/api/crossTabSignOut.ts` to `NOT_SESSION_STATE` with `declarations: 1` and the justification that the latch is the inverse of session state: it records that no session may be adopted, not that a session exists.

### F2 — `accessToken.ts` would import a runtime value from `api/crossTabSignOut.ts`
· **Severity:** **Major**
· **Evidence:** D4 proposes calling `markSignedIn()` inside `retainAccessToken()`. `accessToken.ts` currently imports only a type from `api/crossTabRenewal` (erased at runtime). `markSignedIn()` is a value.
· **Assessment:** This creates a runtime dependency from `auth/accessToken.ts` to `api/crossTabSignOut.ts`. The layering is defensible — `api/` is lower-level infrastructure and `auth/` consumes it — but it is a change from the current type-only import. It also means `accessToken.ts` can no longer be reasoned about without `crossTabSignOut.ts`.
· **Recommended brief amendment:** Justify the direction explicitly. Consider whether the reset could be done in `CallbackScreen.tsx` (the only real caller of `retainAccessToken`) instead of inside `accessToken.ts`. The brief rejects this to keep the reset at the single assignment point; that trade-off should be stated in the Files section.

### F3 — The position of `markSignedOut()` inside `signOut()` is load-bearing and under-specified
· **Severity:** **Major/Blocker**
· **Evidence:** AC9 says the five-step order is unchanged and `markSignedOut()` is additive, but it does not say where it goes. `signOut.ts` currently clears `accessToken` first, then `takeIdToken`, then stores.
· **Assessment:** If `markSignedOut()` is placed at the END, there is a window after `clearAccessToken()` during which the tab holds no token but the latch is not yet set; a `renewal:succeeded` arriving in that window would be adopted, re-authenticating a signed-out tab. If it is placed at the START, the latch is set before any state is cleared, closing the window. The only safe position is first.
· **Recommended brief amendment:** State explicitly that `markSignedOut()` runs FIRST in `signOut()`, before `clearAccessToken()`. Update AC9 to assert this position and add a test that posts `renewal:succeeded` immediately after `markSignedOut()` (before the clears finish) and asserts the token stays null.

### F4 — T35's AC9 replacement must not silently become a different claim
· **Severity:** **Major**
· **Evidence:** The existing AC9 asserts the hazard exists: "a signed-out tab still adopts a sibling renewal." The brief says T39 replaces it with the assertion that the token stays null.
· **Assessment:** The replacement is correct, but the test must also prove the mechanism: the listener is installed, the sign-out broadcast is received, and only then is the renewal refused. A test that merely asserts the token stays null after `signOut()` + postMessage could pass for the wrong reason (e.g., the listener was never installed and the token was already null).
· **Recommended brief amendment:** The replacement case should: (1) install the sign-out listener, (2) install `shareRenewalAcrossTabs`, (3) sign out and assert the broadcast is delivered to a third endpoint, (4) post `renewal:succeeded`, (5) assert the token stays null. Separate assertions for delivery and refusal.

### F5 — AC2's "no re-broadcast" proof needs a third endpoint on the sign-out channel
· **Severity:** **Major**
· **Evidence:** AC2 says a receiving tab does not re-broadcast, proven by a third endpoint observing exactly one message.
· **Assessment:** The structural argument (receiver calls `signOut()`, which does not broadcast) is sound, but a test must show it. The test should: create channel C, install listener in tab A, have tab B broadcast, observe on tab C that exactly one message arrives, and assert tab A's token is cleared.
· **Recommended brief amendment:** Add the third-endpoint design to the test plan. Specify that the observer listens on `SIGN_OUT_CHANNEL` and counts messages.

### F6 — OQ-T39-6 is a real latent race and should be more prominent
· **Severity:** **Major**
· **Evidence:** D6 notes that a `renewal:succeeded` from a sibling's *pre-sign-out* renewal could arrive after a new sign-in and overwrite the new token. The brief carries it as OQ-T39-6.
· **Assessment:** This is not just a future-renewal issue; it is a correctness issue for the latch design itself. The proposed fix only refuses adoptions after the latch is set; it does not correlate adoptions with a generation. A tab that signs in again while a sibling's stale renewal is in flight could still be overwritten.
· **Recommended brief amendment:** Make OQ-T39-6 a first-class deviation (D-e) rather than burying it in D6. Document the exact race and why a generation stamp was rejected. Add a test that demonstrates the race is still possible, so a future renewal task meets it as a failing expectation.

### F7 — Nothing installs the listener, so AC1 is not end-to-end testable in production
· **Severity:** **Major**
· **Evidence:** D-a states no module owns app-level listeners and `mountShell.tsx` is unowned.
· **Assessment:** This is the same dormant-task pattern as T36/T37. The primitives will be built and correct but no production code path exercises the full flow. The acceptance criteria should be honest about this limit.
· **Recommended brief amendment:** Add an acceptance criterion that the listener primitive is correct and a wiring point exists, but note that the full cross-tab behavior is verified by direct installation in tests until an app-level owner is assigned.

### F8 — The sign-out message parser should reject unknown shapes, but the brief does not specify strictness
· **Severity:** **Minor/Major**
· **Evidence:** AC11 says a malformed or hostile message is ignored. D5 says the message carries `{ type: 'session:signed-out' }` and "a parser rejecting every other shape, mirroring `parseOutcome`."
· **Assessment:** The design is right, but the brief should specify exactly what shapes are rejected: objects with extra fields, wrong `type`, non-object data, etc. This determines the parser implementation and tests.
· **Recommended brief amendment:** Add: the parser accepts only objects whose `type` is exactly `'session:signed-out'` and rejects everything else, including objects with additional properties.

### F9 — The `SIGN_OUT_SET` derivation will fire, and the brief must show the instrument firing before updating it
· **Severity:** **Minor/Major**
· **Evidence:** D2 notes that `signOut()` gains a sixth call and T36's derived `SIGN_OUT_SET` will fire. T36's phase 9 rule says an instrument may be edited when it must grow to admit new state, but phase 6 must show the instrument firing before it is updated.
· **Assessment:** This is a process requirement. The brief should include a step in phase 6 to run T35's suite, observe the failure caused by the new `markSignedOut()` call, and only then update `PERMITTED_CALLERS` / `SIGN_OUT_SET`.
· **Recommended brief amendment:** Add a phase-6 instruction: run T35's suite with the new `markSignedOut()` call and capture the failure; then add `markSignedOut` to `PERMITTED_CALLERS` (only `auth/signOut.ts`) and grow the derived set.

### F10 — T35's AC9b retirement needs a clear transition
· **Severity:** **Minor/Major**
· **Evidence:** AC14 says T35's AC9 is replaced and AC9b retired. AC9b currently asserts the hazard case names task 39 and OQ-T35-3.
· **Assessment:** Once the hazard is fixed, AC9b's purpose disappears. It should be removed or replaced with a case asserting the hazard case was replaced. A stale AC9b would test nothing useful.
· **Recommended brief amendment:** State that AC9b is deleted entirely, and add a new case in T39's suite (or T35's replacement) that asserts the old hazard message no longer appears in the source.

### F11 — AC6 (BroadcastChannel undefined) requires environment manipulation in tests
· **Severity:** **Minor**
· **Evidence:** AC6 says propagation is a no-op, not a throw, where `BroadcastChannel` is undefined.
· **Assessment:** This is testable by deleting `globalThis.BroadcastChannel` temporarily. The test must restore it. The brief should note this.
· **Recommended brief amendment:** Add a test design: delete `globalThis.BroadcastChannel`, call `broadcastSignOut()` and `listenForSignOut()`, assert no throw and the returned `close()` is callable.

### F12 — `endSession.ts` adds a runtime dependency on `api/crossTabSignOut.ts`
· **Severity:** **Minor**
· **Evidence:** D2 says `endSession()` calls `broadcastSignOut()`.
· **Assessment:** This is consistent with `auth/` consuming `api/`. Not a problem, but should be listed in the Files table.
· **Recommended brief amendment:** Add `src/auth/endSession.ts` to the Files table with the modification reason.

### F13 — The lifecycle of `BroadcastChannel` instances in `listenForSignOut` and `broadcastSignOut` should be explicit
· **Severity:** **Minor**
· **Evidence:** The proposed API accepts an optional channel for injection but does not say who owns it.
· **Assessment:** In production, `listenForSignOut` would likely create and hold its own channel; tests inject one. `broadcastSignOut` creates a channel, posts, and... should it close it? If it closes it, subsequent broadcasts create/close repeatedly. If it doesn't, production leaks channels unless the caller closes. The API design should be explicit.
· **Recommended brief amendment:** Specify that `broadcastSignOut` creates a channel, posts, and closes it (fire-and-forget). `listenForSignOut` creates a channel, holds it, and `close()` closes it. Optional injection is only for tests.

### F14 — `crossTabSignOut.ts` must not hold a `BroadcastChannel` at module scope
· **Severity:** **Minor**
· **Evidence:** The proposed API creates channels in functions.
· **Assessment:** Holding a channel at module scope would make the module stateful in a way that is hard to test and could leak. The function-scoped design is correct.
· **Recommended brief amendment:** Add a source-scan criterion that `crossTabSignOut.ts` declares no `BroadcastChannel` at module scope.

### F15 — The name `markSignedIn()` is correct but could be misread as tracking session state
· **Severity:** **Minor**
· **Evidence:** D3 proposes `markSignedIn()` to clear the latch.
· **Assessment:** The name is the complement of `markSignedOut()`. It should be documented that it does not assert the tab is signed in; it merely removes the sign-out latch so future adoptions are allowed.
· **Recommended brief amendment:** Add a doc comment on `markSignedIn()` stating it resets the latch and is called whenever a token is assigned, not when the tab is confirmed authenticated.

### F16 — The `shareRenewalAcrossTabs` modification should be minimal and isolated
· **Severity:** **Minor**
· **Evidence:** D2 says `crossTabRenewal.ts: onMessage` refuses `renewal:succeeded` when `hasSignedOut()`.
· **Assessment:** The change should be a guard at the top of `onMessage` before calling `local.adopt()`. It should not change the message parsing or the `Outcome` type.
· **Recommended brief amendment:** Specify the exact code shape: `if (outcome.type === 'renewal:succeeded' && hasSignedOut()) return;` before `local.adopt()`.

### F17 — No test is proposed for the latch reset behavior
· **Severity:** **Minor**
· **Evidence:** AC8 says a tab that signs out and signs in again adopts a sibling renewal normally.
· **Assessment:** This is the right criterion, but the test must be precise: sign out, assert latch is set, sign in (call `retainAccessToken`), assert latch is reset, post `renewal:succeeded`, assert adoption works.
· **Recommended brief amendment:** Add the test steps to AC8.

### F18 — The new test file should be in `src/api/` but may also need integration with `auth/` tests
· **Severity:** **Minor**
· **Evidence:** The proposed test file is `src/api/signOutPropagatesToAllTabs.test.ts`.
· **Assessment:** Testing the new module in isolation is good. The integration with `crossTabRenewal` and `signOut()` may need to live in T35's modified suite (AC9 replacement) and T36's suite (endSession broadcasts).
· **Recommended brief amendment:** Clarify which tests live where: `signOutPropagatesToAllTabs.test.ts` tests the new module; T35's suite tests the fix for OQ-T35-3; T36's suite tests that `endSession()` broadcasts.

---

## Summary

T39's design is structurally sound: a separate channel, a latch set inside `signOut()`, broadcast outside, and no re-broadcast by receivers. The most important challenges are **F1** (module-scope latch needs explicit exemption from T35's mutable-state scan), **F2** (runtime dependency from `accessToken.ts` to `api/crossTabSignOut.ts`), **F3** (the latch must be set FIRST in `signOut()` or the race window remains), **F4** (the AC9 replacement must prove the mechanism, not just the outcome), and **F6** (OQ-T39-6 is a real latent race that deserves prominence). The brief is otherwise thorough and honest about the dormant-task nature of the work.

**Phase 3 complete.** Eighteen findings returned; no code or contract changed.
