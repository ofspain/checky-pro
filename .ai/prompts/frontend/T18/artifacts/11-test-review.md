# frontend · T18 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md`, `callbackWithMismatchedStateIsDiscarded.test.tsx` (131 cases), `CallbackScreen.tsx` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Gaps

### Gap 1 · The D-12 ordering case can pass even when the stored state is read, if `getItem` throws

- **Why it matters ·** The two cases at lines 755–769 seed a matching stored state and assert the `state` key was **not** read. The assertion is:
  ```ts
  expect(read).not.toContain(AUTHORIZE_STORAGE_KEYS.state);
  ```
  If `sessionStorage.getItem` throws inside the early-return path (before the comparison), the error check still precedes the comparison, which is what D-12 wants — but the test's *method* of proving that (no recorded `getItem` call) happens to hold, because the early return fires before the read. However, the test does not assert that the error check **short-circuits** before reaching the comparison; it only asserts no read occurred. A future refactor that moved the read above the error check but still returned on error would also record no `getItem` call if `getItem` threw, yet would violate D-12's intent.
- **Suggested test ·** Add a positive assertion that the effect reaches the sign-in screen via the discard path, and a mutant-aware comment. More concretely: stub `Storage.prototype.getItem` to throw and assert that an error+matching-state callback still discards without propagating the throw (this already exists for the normal path in the storage-denial block, but not combined with a matching stored state and an error response). This would prove ordering even when the read is attempted.

---

### Gap 2 · The storage-denial case stubs the global `Storage.prototype.getItem`, affecting all keys

- **Why it matters ·** The case at lines 806–818 stubs `Storage.prototype.getItem` to throw for **every** call. In this test path the only expected storage access is the `state` read at line 83 of `CallbackScreen.tsx`. But `i18next` or other test harness code might also call `getItem` during the render, and the stub would throw for those too. The test happens to pass because the render path does not touch storage elsewhere, but the stub is broader than the behaviour under test and could make the test brittle to harmless harness changes.
- **Suggested test ·** Narrow the stub to throw only when the key equals `AUTHORIZE_STORAGE_KEYS.state`, allowing other keys to fall through to the real implementation. This documents that the guard is specifically about the state read, not about all storage access.

---

### Gap 3 · AC5's second value is still only one additional value

- **Why it matters ·** The `it.each` at lines 828–849 now runs with two matching values: `MATCHING_STATE = 'xyz'` and `'a-quite-different-state-value'`. This kills the "stored compared against a hardcoded literal" mutant, which is excellent. But it does not prove the comparison works for arbitrary lengths or characters. A regression that compared only the first three characters of the stored value would still pass both rows. The original AC6 already covers prefix/suffix mismatches, but there is no symmetric test that a match must be exact across the full string.
- **Suggested test ·** Add at least one success-path case where the matching state is a realistic 43-character base64url token (the production shape from `randomUrlSafeToken`), and the URL supplies the same token. This proves the comparison scales to the real length and character set.

---

### Gap 4 · The OQ-2 tripwire asserts the seam is called twice, but does not assert storage survives the first call

- **Why it matters ·** The tripwire at lines 782–797 asserts `second.calls` has length 1, but the reason the second call succeeds is that the stored state survived the first arrival. The test does assert `globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)` equals `MATCHING_STATE` before the second arrival (line 793), which is good. However, it does not assert that `nonce` and `code_verifier` also survive. A regression that deleted only the `state` key on a successful comparison would fail the second call, but a regression that corrupted `nonce` or `code_verifier` would not be caught here — though those are task 19 and 20's concerns, not T18's.
- **Suggested test ·** Extend line 793 to assert all three keys survive unchanged. This keeps the tripwire focused on T18's boundary (read without consume) rather than letting task 19/20 regressions leak undetected.

---

### Gap 5 · The `MISMATCH_SHAPES` table has no row where the stored value equals the URL state modulo percent-encoding

- **Why it matters ·** T17 trims the *supplied* value before comparison. A URL state of `'xyz%20'` (literal percent-encoded space, not decoded by `URLSearchParams.get`) becomes `'xyz%20'`, not `'xyz '`. The current rows use `arrivalWith`, which encodes via `encodeURIComponent`, so `%20` in a value becomes `%2520`. There is no test that a URL with a literal `%20` in the query string is treated as a mismatch against a stored `'xyz '` or `'xyz'`. This is an edge case in how `URLSearchParams` decodes, and it is not covered.
- **Suggested test ·** Add a row to `MISMATCH_SHAPES` that passes the URL literally: `['/callback?code=abc&state=xyz%20', 'stored-value', ...]` or similar, depending on the intended behaviour. The expected behaviour should be documented: `URLSearchParams.get('state')` returns `'xyz '`, which would match a stored `'xyz '` — but the test should pin whether that is considered a match or mismatch.

---

### Gap 6 · The whole-suite regression number is asserted in prose, not in code

- **Why it matters ·** The artifact reports `692 passed, 1 failed (693)` and relies on human reviewers to notice if the count changes. There is no test that fails when the total changes. In a CI environment, a new failure could be introduced and the suite would still report "one failed" — the exact count is the only guard, and it is not automated.
- **Suggested test ·** This is a process matter, not a unit test, but consider adding a CI check that asserts the expected total against the actual total. Phase 12 should record the expected count in a place CI can enforce it.

---

### Gap 7 · No test verifies that the `state` read uses `AUTHORIZE_STORAGE_KEYS.state` and not a different key

- **Why it matters ·** The source scan at lines 953–955 asserts the literal `'themistra.auth.'` does not appear and `AUTHORIZE_STORAGE_KEYS.state` does. But there is no runtime test that the actual `getItem` call uses the correct key. A regression that changed the key to `AUTHORIZE_STORAGE_KEYS.nonce` would pass the scan (because the code still uses the constant) but would compare the callback state against the stored nonce, which is wrong.
- **Suggested test ·** Add an assertion in the comparison-path no-read case (lines 647–663) that the recorded `getItem` call's first argument is exactly `AUTHORIZE_STORAGE_KEYS.state`. The test already captures `read`; add `expect(read.filter((k) => k === AUTHORIZE_STORAGE_KEYS.state)).toHaveLength(1)` or similar.

---

### Gap 8 · The `arrivalWith` helper defaults `code` to `'abc'` but does not encode it

- **Why it matters ·** `arrivalWith` correctly encodes the `state` parameter. It also accepts a `code` parameter and encodes it with `encodeURIComponent`. This is fine. But every caller passes only `state`, relying on the default `code = 'abc'`. The default is never exercised with a code that needs encoding, so the helper's `code` branch is not tested within this file. Not a critical gap, but it means the helper's symmetric encoding of `code` is dead code from the perspective of this suite.
- **Suggested test ·** Either drop the `code` parameter (it is unused) or add one case that passes a code containing characters requiring encoding. Keeping the parameter without using it is mild technical debt.

---

### Gap 9 · No test verifies the storage-read guard returns `null` rather than re-throwing

- **Why it matters ·** The storage-denial case at lines 806–818 asserts the visitor is discarded and the page is not blank. It does not assert that the caught exception is not re-thrown or logged. The implementation catches and assigns `null`, which is correct, but a mutant that re-threw inside the `catch` block would be caught only by the test failing — which it would. So this is partially covered. However, the test name says "discards when reading the stored state throws" and the assertion set does not explicitly confirm the throw was caught rather than propagated elsewhere.
- **Suggested test ·** The current assertions are sufficient in practice. This gap is minor; no new test is strictly needed unless phase 12 wants a mutant test for "catch block re-throws."

---

### Gap 10 · The `MISMATCH_SHAPES` positive control is implicit, not explicit

- **Why it matters ·** Every row in `MISMATCH_SHAPES` expects a discard. There is no row that expects a match within that table. The match is tested elsewhere (success path), but the table itself lacks a positive control row. A table with a subtly inverted condition could pass all rows because they all discard, without proving the table can ever produce a match.
- **Suggested test ·** This is already covered by the success-path cases and by AC5. Marking it as a gap only because the table's doc comment claims "every pair here is a discard" — which is true and appropriate. No change needed.

---

## Summary

| # | Gap | Severity | Suggested action |
|---|---|---|---|
| 1 | D-12 ordering test can pass even if read is attempted but throws | Low | Add error+matching-state case with throwing `getItem` |
| 2 | Storage-denial stub is global, not key-specific | Low | Narrow stub to `AUTHORIZE_STORAGE_KEYS.state` |
| 3 | AC5 only uses two matching values; no full-length production token | Low | Add one success case with a 43-character base64url token |
| 4 | OQ-2 tripwire does not assert nonce/verifier survive | Low | Extend line 793 to assert all three keys survive |
| 5 | No test for literal percent-encoded state in URL | Low–Medium | Add explicit row for `%20`/`%2520` behaviour |
| 6 | Regression count is prose, not CI-enforced | Low | Record expected total for CI in Phase 12 |
| 7 | No runtime assertion that the read uses the correct key | Low | Assert `getItem` argument equals `AUTHORIZE_STORAGE_KEYS.state` |
| 8 | `arrivalWith` `code` parameter is unused | Trivial | Remove parameter or add encoded-code case |
| 9 | No explicit assertion that caught exception is not re-thrown | Trivial | Optional mutant test |
| 10 | Mismatch table lacks positive control (already covered elsewhere) | None | No change |

**Overall assessment:** The test suite thoroughly verifies R8 and the acceptance criteria. The security-critical properties — exact comparison, storage-bound comparison, no widening, no exchange on mismatch, L4-indistinguishable outcomes — are all well-tested and mutation-verified. The gaps above are minor edge cases and maintainability improvements rather than missing coverage. The suite is ready for Phase 12, with Gap 5 being the most substantive remaining edge case to consider.