# frontend · T23 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` against the frozen brief and the 26-case manifest. The file is comprehensive: every AC has a dedicated case, the Phase 8 findings are answered, the mutation score is 15/16 with a well-argued survivor, and the vacuity controls are present. The gaps below are strengtheners; none suggests a behaviour defect.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed test source.

---

## Gap 1 · No direct test for a partially populated store in `consumeAuthorizeState`

**Why it matters.** The direct cases prove idempotency on a full store and on an empty store, but not on a partially populated one. A regression that made `consumeAuthorizeState` assume all three keys exist (for example, by iterating over an array that does not include the constant) would pass both existing cases.

**Suggested test.** Add a direct case that seeds only `state` and `nonce`, calls `consumeAuthorizeState()`, and asserts those two are removed while the missing `code_verifier` does not cause a throw and no other key is touched.

---

## Gap 2 · No source scan proves `consumeAuthorizeState` has only one production call site

**Why it matters.** The file asserts that `CallbackScreen.tsx` contains exactly one `consumeAuthorizeState()` call, but it does not scan the other production modules. A future change in another module could import and call it — for example, from a discard path — and the existing cases would not catch it unless that path was one of the six discard shapes already tested.

**Suggested test.** Add a source scan case (similar to T22's scan) that reads every production `.ts`/`.tsx` file and asserts `consumeAuthorizeState` appears only in `auth/authorizeRequest.ts` (its definition) and `auth/CallbackScreen.tsx` (exactly one call). Include a synthetic violation to prove the scan works.

---

## Gap 3 · The M16 survivor argument lives only in the manifest

**Why it matters.** M16 moves the deletion above `retainAccessToken` and survives because nothing reads the three items between those two statements. The argument is sound, but it is recorded in `artifacts/10-test-generation.md`, not in the test file or a code comment. If the manifest is separated from the repository, a future maintainer might add a structural ordering assertion there or move the call and wonder why no test fails.

**Suggested test.** Add a short code comment at the call site or in the test file's ordering block stating that the order between `retainAccessToken` and `consumeAuthorizeState` is intentionally not asserted because both positions satisfy AC6, and the guarded boundaries are the exchange and the nonce check. This makes the survivor's rationale durable.

---

## Gap 4 · The replay case does not prove the discard is caused by the missing `state`

**Why it matters.** The replay case asserts no network call and a sign-in route, which is the desired post-T23 behaviour. But it does not include a control showing that the same replay would have exchanged (and then been refused server-side) if the stored `state` had still been present. Without that control, the assertion "no network call" could be misread as "the replay always discards" rather than "the replay discards *because the three items are gone*".

**Suggested test.** Add a control row inside the replay case: before the second `arriveAt`, manually re-seed the three L16 items with the original values, then assert the second arrival does make a network request (and is then refused, ending on the discard route). This pins the cause to the deleted state.

---

## Gap 5 · No test asserts deletion happens before any post-login navigation task 24 may add

**Why it matters.** Task 24 owns the post-login destination. If a future implementation navigates before calling `consumeAuthorizeState()`, the three items would still be deleted, but the screen state would change mid-cleanup. More importantly, task 24 might read a return target from storage; if the deletion runs after that read, task 24's read is fine, but if it runs before, task 24 would need a different mechanism.

**Suggested test.** Not a test for T23 to write today, but a handoff comment should be added to task 24's brief: navigation must happen after `consumeAuthorizeState()` returns, or task 24 must read its return target before the deletion point. Record this in the T23 Phase 11 artifact so the boundary is visible.

---

## Gap 6 · The `FAULTS` table is missing a "dropped connection" row

**Why it matters.** The brief's AC4 and the implementation's five failure shapes include a dropped connection (`fetch` throws). The existing `FAULTS` table has six rows: no state, mismatched state, no code, authorization error, refused exchange, and bad nonce. A dropped connection is tested elsewhere in the package (T20/T21) but is not explicitly listed in this file's discard matrix, so a reader counting rows against the brief may miss it.

**Suggested test.** Add a seventh row to `FAULTS` for a dropped connection, or document that the six rows deliberately subsume it because the exchange's `catch` path produces the same discard. If the latter, add a comment to the table explaining the omission.

---

## Summary

The T23 test suite is already strong enough to ship. Gap 1 and Gap 2 are the most valuable additions: they close edge cases around the deletion function itself and its call graph. Gap 3 and Gap 4 are documentation/clarity improvements. Gap 5 is a handoff to task 24. Gap 6 is a minor consistency item.
