# frontend · T21 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 additions to `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` against the frozen brief and the named-test requirements. The 35 new cases close the Phase 8 gaps and the mutation run is clean. The gaps below are strengtheners; none suggests a behaviour defect.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed test source.

---

## Gap 1 · `shareRenewalAcrossTabs` is tested only through its fallback path

**Why it matters.** `shareRenewalAcrossTabs` returns a fallback when `navigator.locks` or `BroadcastChannel` are unavailable (`crossTabRenewal.ts:83-86`). In jsdom, that is likely the path exercised by the existing case. The real cross-tab path — a Web Locks leader renewing, posting a `renewal:succeeded` message, and the listener calling `local.adopt(token)` — is not exercised. AC6 says the holder is accepted by the real cross-tab renewal, but "accepted" is only proven for the degraded branch.

**Suggested test.** Inject a mock `BroadcastChannel` and `TabLocks` into `shareRenewalAcrossTabs(accessToken, { channel, locks })`, post a `{ type: 'renewal:succeeded', token: 'SIBLING-TOKEN' }` message, and assert `accessToken.current()` becomes `SIBLING-TOKEN`. This proves the production listener path reaches `adopt`.

---

## Gap 2 · No direct isolation test for `clearAccessToken`

**Why it matters.** `clearAccessToken` is exercised indirectly through every screen-layer discard and the stale-token case, so a regression that made it a no-op is covered. But the only way to understand what the function itself does is to read the production file. A direct case makes the contract explicit and gives future maintainers a place to add edge cases (e.g., idempotency).

**Suggested test.** Add a direct-layer case: `retainAccessToken('TOKEN'); clearAccessToken(); expect(accessToken.current()).toBeNull();`.

---

## Gap 3 · The "token in a thrown message" scan is narrowly scoped

**Why it matters.** The scan pattern is `/new Error\([^)]*\$\{/`, which catches a template literal in an `Error` constructor. It does not catch string concatenation (`new Error('token ' + held)`), a `.toString()` call, or a non-Error throw. The seam message itself is a fixed string today, but the scan is the durable guard.

**Suggested test.** Broaden the pattern to also reject `new Error(... + ...)` forms inside the holder file, or add a synthetic-violation row that uses concatenation and assert the scan catches it.

---

## Gap 4 · `renew()` is not proven to return a rejected promise rather than throw

**Why it matters.** The boundary's `attempt.catch(...)` at `sessionBoundary.ts:59` catches a rejected promise but not a synchronous throw. The mutation run killed M11 (synchronous throw instead of rejection), which shows the difference matters, but the committed case uses `rejectionOf(accessToken.renew())`, which awaits and therefore passes whether the function throws or returns a rejected promise.

**Suggested test.** Assert `await expect(accessToken.renew()).rejects.toThrow(...)` and also that the returned value is a `Promise` that rejects (e.g., `expect(accessToken.renew()).toBeInstanceOf(Promise)` followed by `await expect(...).rejects.toThrow(...)`).

---

## Gap 5 · No test asserts the token survives a successful exchange after the screen unmounts

**Why it matters.** The existing unmount test releases a success response after unmount and asserts no token reaches storage. It does not assert whether `current()` is still held. The module variable outlives the component, so the token should persist — but that persistence is the core of T21, and a case that accidentally cleared it on unmount would not be caught.

**Suggested test.** Extend the success-after-unmount case to also assert `rendered.holder.accessToken.current()` is `ACCESS_TOKEN` after release and `turn()`. This documents that "in memory" means module scope, not component scope.

---

## Gap 6 · The test file header still describes only T20

**Why it matters.** The file comment at lines 16–36 explains T20's verifier half in detail but never describes T21's in-memory half. A reader opening the file sees "T20 - the token request carries the STORED code_verifier" and may miss that the file now owns both halves.

**Suggested test.** Update the header comment to describe both T20 (verifier) and T21 (in-memory retention), or add a second header block above the `the token is held in memory` describe.

---

## Summary

The T21 test additions are strong: they prove retention, absence after every failure path, real boundary/cross-tab conformance, source-level singleness, and the forged-callback clearing tradeoff. Add G1 and G4 for confidence in the seam and cross-tab paths; G2, G3, and G5 are polish; G6 is documentation.
