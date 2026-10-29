# frontend · T23 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the Phase 6 diff (`authorizeRequest.ts`, `CallbackScreen.tsx`, and the inherited-assertion updates) with fresh eyes. The implementation matches the frozen brief: one call site, after every consumer, on the success path only, guarded, and with no new dependency. The strongest issue is the coupling between the deletion set and `AUTHORIZE_STORAGE_KEYS`, which is one task away from becoming a bug.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of source.

---

## I1 · `consumeAuthorizeState()` will delete task 24's key if it is added to `AUTHORIZE_STORAGE_KEYS`

**Issue.** `consumeAuthorizeState()` iterates `Object.values(AUTHORIZE_STORAGE_KEYS)` and removes every key. The constant is the natural home for the L16 post-login return target that task 24 will add. If task 24 adds its key there, this function will delete it at callback — likely before task 24 reads it for navigation.

**Evidence.**
- `authorizeRequest.ts:120-128`: the function loops every value of the constant.
- `authorizeRequest.ts:35-39`: the constant currently holds exactly `state`, `nonce`, `codeVerifier`.
- `authorizeRequest.ts:104-105`: the doc comment says the fourth item belongs to task 24.
- No production assertion pins the constant to the three keys.

**Recommendation.** Concur with the self-review's S4: add a structural test that asserts `Object.keys(AUTHORIZE_STORAGE_KEYS)` is exactly `['state', 'nonce', 'codeVerifier']`. Better yet, change `consumeAuthorizeState()` to take the three keys explicitly (or to enumerate them by name inside the function) so task 24 cannot enrol a new key by accident. The frozen brief chose iteration for all-or-nothing; pair it with a pin so the set cannot grow silently.

**Confidence.** High — the code is correct today but fragile against a near-future change.

---

## I2 · The guard swallows every error shape indiscriminately

**Issue.** `consumeAuthorizeState()` catches every exception from `removeItem`. The intended failure mode is a denied store (`SecurityError`). A packaging mistake that makes `globalThis.sessionStorage` undefined would produce a `TypeError` and be swallowed the same way, leaving the test suite green while the deletion never runs.

**Evidence.**
- `authorizeRequest.ts:122-127`: bare `catch {}` around `removeItem`.
- The self-review S5 already identifies this.

**Recommendation.** Phase 10's throwing-store case should assert three `removeItem` attempts were made and all three threw, not merely that the visitor's outcome is unchanged. This distinguishes "tried and failed" from "never tried". No production change is needed; the swallowing is the right behaviour for the visitor.

**Confidence.** Medium — a testing gap, not a current behaviour defect.

---

## I3 · AC14's literal wording is violated by two comment edits

**Issue.** AC14 says "no production change outside `authorizeRequest.ts` and `CallbackScreen.tsx`." The diff edits head comments in `codeExchange.ts` and `idTokenNonce.ts` to restate where deletion now lives.

**Evidence.**
- `codeExchange.ts:95-103` and `idTokenNonce.ts:20-22` were edited.
- The edits are comments only; no behaviour changed.

**Recommendation.** Concur with the self-review's S2: amend AC14 to "no production **behaviour** change outside the two named files; comment corrections are allowed under T23-D8/T23-D9." `tsc` and the three purity cases are the proof that nothing else moved.

**Confidence.** High — a paperwork fix, not a code defect.

## I4 · A T16 test file was changed inside T23's commit

**Issue.** `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` gained `vi.resetModules()` in three places to fix an intermittent order-dependent flake observed while measuring T23. It is not an inherited assertion under AC12.

**Evidence.**
- `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:145, 453, 527` now call `vi.resetModules()`.
- The self-review S3 says one in five full-suite runs failed before the fix; six clean runs since.

**Recommendation.** This needs a gate decision: accept the fix as an out-of-scope robustness improvement (recommended, because reverting restores a flake), or split it into a standalone commit. Either way, record the measurement in the PR notes so it is not a silent cross-task change.

**Confidence.** Medium — correct fix, wrong commit boundary.

---

## I5 · Five acceptance criteria are not verified by the committed suite

**Issue.** The suite is green, but the named test `stateSurvivesRedirectAndIsConsumedOnce` does not exist yet. Several ACs that belong to it are unproven: AC5 (throwing store leaves all three on success), AC6 (ordering), AC7 (StrictMode one deletion), AC8 (throwing `removeItem` leaves outcome unchanged), and AC10 (new sign-in after consumed callback).

**Evidence.**
- `glob` for `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce*` returns no file.
- The self-review S1 lists the same five.

**Recommendation.** Carry these into Phase 10 as the named test's spine. Do not let the inherited assertion updates in other files stand in for them — they prove the *consequence* of deletion on the discard path, not its ordering, guard, or arity on the success path.

**Confidence.** High — a phase-boundary status finding, not a defect.

---

## I6 · A replayed callback clears a held token without a server round trip

**Issue.** `CallbackScreen.tsx:73-83` defines `discard()` to call `clearAccessToken()`. After T23, a second arrival at `/app/callback` finds no stored state and discards immediately, clearing any held token. Before T23 the same arrival re-exchanged and got `400 invalid_grant` before discarding. The outcome is the same, but the path is now local and instant.

**Evidence.**
- `CallbackScreen.tsx:78`: `clearAccessToken()` inside `discard()`.
- `CallbackScreen.tsx:122-124`: a second arrival with no stored state discards.
- The self-review S6 already identifies this.

**Recommendation.** Not T23's to fix. Record it as a handoff to task 24: the post-login navigation should move the visitor away from `/app/callback` so the route is not reachable by back/forward navigation while holding a token.

**Confidence.** Medium — behaviour is deliberate today, but the interaction is sharper after T23.

---

## I7 · The success path still ends on the holding screen

**Issue.** After `consumeAuthorizeState()` the screen does not navigate anywhere. A successful sign-in leaves the visitor on `/app/callback` with the callback query still in the URL.

**Evidence.**
- `CallbackScreen.tsx:153-170`: `retainAccessToken` and `consumeAuthorizeState` run, then no `navigate`.
- The self-review S7 already identifies this.

**Recommendation.** No change in T23. Record it clearly as a handoff to task 24, which owns the post-login destination and removing the callback query from the URL (T22-D9).

**Confidence.** Low — out of scope, but worth pinning.

---

## I8 · The `Object.values` iteration assumes insertion order

**Issue.** `consumeAuthorizeState()` relies on `Object.values(AUTHORIZE_STORAGE_KEYS)` returning the keys in the order they were declared. The T22 test's `expectAuthorizeStateConsumed` asserts that removal order matches write order. ECMAScript does guarantee insertion order for string-keyed object properties in practice, but the test is the place that enforces it.

**Evidence.**
- `authorizeRequest.ts:121`: `for (const key of Object.values(AUTHORIZE_STORAGE_KEYS))`.
- `tokensAreNeverWrittenToAnyStorage.test.tsx:360-368`: `expectAuthorizeStateConsumed` asserts removal order.

**Recommendation.** No production change. The existing test already makes the order observable. If the order ever matters for correctness (it does not today; it matters only for the test's assertion), switch to three named removals. Otherwise, the test is sufficient documentation.

**Confidence.** Low — theoretical, mitigated by the test.

---

## I9 · No direct isolation test for `consumeAuthorizeState()`

**Issue.** The function is only exercised through the screen flow. A direct unit test would cheaply prove idempotency, the guard, and the exact key set without the rendering harness.

**Evidence.**
- `consumeAuthorizeState()` is exported but not tested in isolation in any committed file.
- The self-review's S4 and S5 findings would be easier to lock down with a direct test.

**Recommendation.** Add at least one direct case in the named test file: seed the three items, call `consumeAuthorizeState()`, assert the store is empty, call it again, assert it does not throw; and a guard case with `removeItem` throwing, asserting the function returns without error and the remaining items stay.

**Confidence.** Low — coverage convenience, not a behaviour gap.

---

## Summary

The production code is correct against the frozen brief. I1 is the only issue that risks a future regression; I2, I3, I4, and I5 are verification or boundary items; I6 and I7 are handoffs to task 24; I8 and I9 are minor. The design challenge's F1-F3 warnings about inherited assertions and the T22 helper split were addressed in the implementation.
