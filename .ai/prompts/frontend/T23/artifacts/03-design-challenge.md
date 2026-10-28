# frontend · T23 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. The design is sound: keep the readers pure, delete only on the consumed path, and guard the deletion. The findings are about wording, test helpers, and the ripple into T22's acceptance criteria.

---

## F1 · The inherited `callbackValidatesNonce` assertion must not be updated to imply the pure function deletes

**Severity.** Medium.

**Evidence.**
- `callbackValidatesNonce.test.tsx:556-577` asserts that `idTokenNonceMatchesStoredValue` leaves all three items untouched and calls no storage method. Its comment says "task 18 left a case that fails by design the moment task 23 lands."
- T23-D1 deliberately keeps `idTokenNonce.ts` and `codeExchange.ts` pure; deletion lives in `authorizeRequest.ts` and is called by `CallbackScreen.tsx`.

**Recommended brief amendment.** Update the comment to say the *pure check* still does not touch storage, and deletion is the screen's responsibility after the check. Do not rewrite the assertion to expect deletion — that would violate T23-D1's purity requirement. The new wording should make it obvious why the case still passes unchanged.

**Confidence.** Medium — a wording trap, not a behaviour defect.

---

## F2 · `expectThreeAuthorizeWrites` cannot serve both success and failure cases after T23

**Severity.** Medium.

**Evidence.**
- In T22, `expectThreeAuthorizeWrites` asserts `setItem` three times and `removeItem`/`clear` never called.
- T23-D3 adds three `removeItem` calls on the success path.
- T23-D2 leaves `removeItem`/`clear` un-called on all five failure paths.
- The brief says "update `expectThreeAuthorizeWrites`" in T22 but does not say how it handles the divergence.

**Recommended brief amendment.** Replace the helper with two named helpers — `expectAuthorizeWritesOnly(watch, started)` for failure paths and `expectAuthorizeStateConsumed(watch, started)` for the success path — or add a `consumed: boolean` parameter. State explicitly in Files to Modify that T22's success case must assert three `removeItem` calls with the same three keys, in the same order, and that failure cases keep asserting zero removals.

**Confidence.** Medium — the test will fail to compile or fail assertions without this split.

---

## F3 · T22's AC7 needs its own amendment because the success path now deletes

**Severity.** Medium.

**Evidence.**
- T22's frozen brief AC7 reads: "exactly three `setItem` calls in a successful flow, all from the authorize step, none carrying a token; no `removeItem`, no `clear`."
- T23 changes the success flow to three `setItem` calls followed by three `removeItem` calls.

**Recommended brief amendment.** Update T22's AC7 (and the T22 test assertion) to: "exactly three `setItem` calls, all from the authorize step, none carrying a token; on the consumed path, exactly three matching `removeItem` calls and no `clear`; on every failure path, no `removeItem` and no `clear`." This preserves T22's intent while allowing T23's deletion.

**Confidence.** High — the current wording directly contradicts T23's success path.

---

## F4 · The StrictMode case assumes the `handled` ref survives React's double-invoke

**Severity.** Low.

**Evidence.**
- `CallbackScreen.tsx:41` uses `const handled = useRef(false)` to suppress the second effect invocation under StrictMode.
- T23-D1/AC7 requires one handling and one deletion, and that the second pass does not discard.
- If React's StrictMode double-invoke recreates the ref, the second pass would see `handled.current === false`, read an empty store, and discard.

**Recommended brief amendment.** Add an explicit control to AC7: assert not only that the token is held and the three items are removed, but also that the final route is not the sign-in discard route after the second mount. This makes the ref-survival assumption a tested property rather than an assumption. If the test fails, the fix is the latch mechanism, not the deletion design.

**Confidence.** Low — existing T20/T21 StrictMode tests suggest the ref persists, but T23's deletion makes the second-pass behaviour observable for the first time.

---

## F5 · The all-or-nothing deletion may conflict with the guarded deletion

**Severity.** Low.

**Evidence.**
- T23-D3 says deletion is "all-or-nothing and happens after every consumer has read."
- T23-D4 says the delete is guarded and "a failed deletion must not change the visitor's outcome."
- If `consumeAuthorizeState()` wraps the whole block in one `try`/`catch`, a throw on the first `removeItem` leaves the other two in place — a partial set, which D3 forbids. If it wraps each `removeItem` individually, a partial set is possible in theory, though in practice all three share the same storage and fail together.

**Recommended brief amendment.** State the implementation rule: attempt each `removeItem` individually inside its own guard, swallowing each error, so a storage-denied context leaves all three untouched and a normal context removes all three. Document that true transactionality is unavailable in `sessionStorage`, so "all-or-nothing" is best-effort against real storage failures.

**Confidence.** Low — the behaviour is correct either way; the wording should not promise strict atomicity.

---

## F6 · The ordering test for AC6 needs a concrete mechanism

**Severity.** Low.

**Evidence.**
- AC6 requires deletion "only after every consumer has read."
- The Required Tests section says the ordering case "may have to be structural, since a deletion that happened too early and a deletion that happened at the right time can leave the same end state."

**Recommended brief amendment.** Choose one concrete assertion and add it to AC6/Required Tests:
- **Spy-based:** install `getItem` and `removeItem` spies and assert the last `getItem` call for each key occurs before the first `removeItem` call for that key; or
- **Mutation-based:** introduce a mutant whose `exchangeAuthorizationCode` throws if `code_verifier` is missing, and assert the mutant is killed (proves deletion did not happen before the exchange read the verifier).

Either makes AC6 a real test rather than a structural inference.

**Confidence.** Low — the design already places the call after the nonce check; the test just needs to make that order observable.

---

## F7 · `consumeAuthorizeState()` should have a documented, narrow contract

**Severity.** Low.

**Evidence.**
- T23-D1 exports `consumeAuthorizeState()` from `authorizeRequest.ts`.
- The brief does not specify its JSDoc contract: idempotent, guarded, deletes exactly the three keys named by `AUTHORIZE_STORAGE_KEYS`, swallows errors, returns nothing.

**Recommended brief amendment.** Add a contract paragraph in Files to Modify for `authorizeRequest.ts`: the function must be documented as "remove the three single-use L16 items; idempotent; never throws; does not clear the store or touch any other key." This prevents a future reader from using it as a general cleanup helper.

**Confidence.** Low — documentation, not behaviour.

---

## F8 · The `stateSurvivesRedirectAndIsConsumedOnce` test should include a failure-path vacuity control

**Severity.** Low.

**Evidence.**
- The Constraints section names vacuity as the standing risk: "a case that asserts 'the items are gone' passes if the flow never wrote them."
- AC2 and AC3 require proving the items were present before deletion.

**Recommended brief amendment.** Explicitly require that every "consumed" and "replay" case asserts the three items exist immediately before the deletion point. The helper `startSignIn()` already does this, but the brief should make it an AC-level requirement, not an implementation detail.

**Confidence.** Low — already implied by the design; worth pinning.

---

## Summary

F1, F2, and F3 are the most important because they prevent T23 from accidentally rewriting inherited assertions in a way that contradicts its own purity decision or breaks T22. F4-F8 are smaller but should be frozen before Phase 4 so the implementation and test generation have no ambiguity.
