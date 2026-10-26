# frontend · T19 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md`, `callbackValidatesNonce.test.tsx` (97 cases) |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

---

## Gaps

### Gap 1 · The `tokenWith` helper builds a JWT header with `alg: 'RS256'` but never tests an `alg: 'none'` header

- **Why it matters ·** The helper always emits `{ alg: 'RS256', kid: 'test' }`. The production code never reads the header, so this is harmless, but the test suite has no case proving that an `alg: 'none'` token is rejected. The production comment states that two-segment tokens are unsecured JWTs and must be rejected; the test only covers the segment count, not the algorithm claim. A regression that started reading the algorithm and accepted `none` would not be caught.
- **Suggested test ·** Add a row to the malformed-token table: `['an alg:none header on a three-segment token', tokenWith({ alg: 'none' }, { nonce: STORED })]` — or, if the helper cannot override the header, add a parameter to `tokenWith` for the header and assert rejection. The expected result is still `{ ok: false, reason: 'malformed-token' }` because the production code rejects all non-object payloads, but the case documents the boundary.

---

### Gap 2 · No test proves the signature segment is ignored

- **Why it matters ·** The production code deliberately does not verify the signature (OQ-2). The test helper uses a hardcoded `c2lnbmF0dXJl` ("signature" in base64) filler. There is no test that changes the signature segment and proves the comparison still works. This is the production design, but it is not explicitly pinned.
- **Suggested test ·** Add a direct-function case that mints a matching token, mutates the third segment to a different string, and asserts it still matches. This documents and protects the "decode only, no verify" decision.

---

### Gap 3 · The non-ASCII cases use `tokenWith` but the helper's base64url encoding is hand-rolled and untested itself

- **Why it matters ·** `tokenWith` at lines 58–66 implements its own base64url encoding. The test suite relies on it to exercise `TextDecoder`, but there is no separate proof that `tokenWith` produces a valid JWT. If the helper had a bug (e.g., wrong padding, wrong alphabet), the production code would reject its tokens for the wrong reason, and the suite would still pass.
- **Suggested test ·** Add a self-test for `tokenWith`: parse its output manually (split, decode segment 1, decode payload) and assert the original claims round-trip. This ensures helper bugs do not masquerade as production rejections.

---

### Gap 4 · The `arriveWithToken` mock resolves `{ idToken }` but does not assert the exchange seam was actually called

- **Why it matters ·** The screen-layer tests verify outcomes, but none of them asserts that `exchangeAuthorizationCode` was invoked with `{ code, state }`. A regression that bypassed the seam entirely and called `idTokenNonceMatchesStoredValue` directly would still pass these tests. The T18 suite already asserts the seam is called; T19's screen tests should inherit that guard.
- **Suggested test ·** Add one screen-layer case that spies on `exchangeAuthorizationCode` and asserts it is called once with the callback's `code` and `state`.

---

### Gap 5 · AC11 "no token kept in a module variable" is not directly asserted

- **Why it matters ·** AC11 says a rejected sign-in leaves no token anywhere, including module variables. The current tests check storage and URL but not module state. The only module that could retain a token is `idTokenNonce.ts`, which does not — but this is not tested.
- **Suggested test ·** Add a source scan or a runtime assertion proving `idTokenNonce.ts` has no module-level variable that could hold the token. Alternatively, mock a token with a recognisable value, trigger a rejection, and assert the value is not present in any exported state.

---

### Gap 6 · The source scan for "prefix or partial matching" uses `includes\(` which is too narrow

- **Why it matters ·** The forbidden pattern `includes\(` at line 631 only catches calls using the method-name syntax `x.includes(y)`. It does not catch `x['includes'](y)` or `String.prototype.includes.call(x, y)`. These are unlikely in production but the scan's purpose is to catch any widening of equality.
- **Suggested test ·** Add a synthetic violation using `String.prototype.includes.call(stored, claimed)` to the negative-control test, or broaden the pattern to `/\.includes\b|includes\s*\(/`. Verify the broadened pattern still does not match legitimate code.

---

### Gap 7 · The `nonce` claim shape tests include `Number.NaN` but not `Infinity` or `-Infinity`

- **Why it matters ·** `Number.NaN` is a non-string claim that JSON cannot represent (it serializes to `null`). The test uses it as a "number that reads like the stored value," but `Infinity` and `-Infinity` are also non-string numbers that JSON cannot represent. If the token payload were constructed from a runtime object rather than JSON, these could appear. The production code rejects them via `typeof claimed !== 'string'`, but the suite does not exercise them.
- **Suggested test ·** Optional: add `['positive infinity', Infinity]` and `['negative infinity', -Infinity]` to the non-string claim table. This is a minor gap because real ID tokens are JSON.

---

### Gap 8 · The screen-layer "no token kept" test only checks storage entries that exist

- **Why it matters ·** Lines 566–573 concatenate all storage entries. If no entries exist, the concatenated string is empty and every `not.toContain(part)` passes vacuously. The test seeds storage with `seedAllThree`, so entries exist, but the assertion does not guard against the token being written under a new key.
- **Suggested test ·** Add an assertion that the token's segments are not present in `localStorage` and `sessionStorage` individually, and assert the storage was not empty (so the test is not vacuous). Already partially covered by the seed; strengthen by checking both stores separately.

---

### Gap 9 · No test verifies the `idTokenNonceMatchesStoredValue` function does not log

- **Why it matters ·** AC11 forbids tokens in logs. The production code does not log, but there is no scan or test for `console.log`, `console.error`, or other logging calls in `idTokenNonce.ts` or `CallbackScreen.tsx`.
- **Suggested test ·** Add a source scan forbidding `console\.` in the new module and in `CallbackScreen.tsx`, with a synthetic violation. This is consistent with the existing source scans.

---

### Gap 10 · The test file imports `randomUrlSafeToken` but does not assert the production-shaped nonce length

- **Why it matters ·** The AC6 table uses `randomUrlSafeToken()` as one row, but the suite does not assert that the stored and claimed nonces have the production length (43 characters). A regression that truncated the stored nonce or the claimed nonce would be caught by the exact comparison, but a test that explicitly asserts the length would document the production shape.
- **Suggested test ·** In the `randomUrlSafeToken` AC6 row, assert `value.length === 43` after storing it. This documents the expected production shape.

---

### Gap 11 · The whole-document scan for `NonceRejection` reasons checks `document.body.textContent` after rendering only one screen

- **Why it matters ·** Lines 592–596 check `document.body.textContent` and `document.title`. This is good. However, the test does not check `console` output, network payloads, or `window.name` — other places a leaked reason could surface. The most important missing one is the console.
- **Suggested test ·** Add a `vi.spyOn(console, 'error')` / `console.log` and assert no reason is emitted. This is part of AC11/AC13's "no cause disclosed" guarantee.

---

### Gap 12 · No test proves a token with a `nonce` claim equal to the stored `state` value is rejected

- **Why it matters ·** The Required Tests "Shape" section in the brief listed "a string equal to the `state`" as a shape to reject. The current suite has AC7 (reads nonce key, not state or verifier) which covers this by reading the wrong key. But there is no direct case where the ID token's `nonce` claim equals the stored `state` value and the stored `nonce` is different — the exact scenario the brief described.
- **Suggested test ·** Add a direct-function case: store `state='xyz', nonce='abc'`, mint a token with `nonce: 'xyz'`, assert rejection with reason `'mismatch'`. This is a clearer test of AC7 than the current "wrong key read" case.

---

## Summary

| # | Gap | Severity | Suggested action |
|---|---|---|---|
| 1 | No `alg: 'none'` three-segment case | Low | Add header-overload and rejection case |
| 2 | Signature segment ignored is not pinned | Low | Add case with mutated signature segment |
| 3 | `tokenWith` helper's encoding is self-untested | Low–Medium | Add helper round-trip self-test |
| 4 | Screen tests do not assert the seam is called | Medium | Add seam-call assertion in one screen case |
| 5 | Module-variable token retention unasserted | Low | Add scan or runtime assertion |
| 6 | `includes\(` scan is narrow | Low | Broaden pattern and add control |
| 7 | Missing `Infinity`/`-Infinity` claim shapes | Trivial | Optional addition |
| 8 | Storage token-leak test could be vacuous | Low | Check each store separately and assert non-emptiness |
| 9 | No scan for logging tokens | Low | Add `console\.` scan |
| 10 | Production nonce length not asserted | Low | Assert 43-character length in `randomUrlSafeToken` row |
| 11 | `NonceRejection` leak scan misses console | Low | Spy on `console` methods |
| 12 | No direct "nonce equals stored state" case | Low–Medium | Add the direct AC7 scenario |

**Overall assessment:** The 97-case suite is comprehensive and well-designed. The two-layer approach (direct function + screen harness) correctly addresses the D-2 reachability problem. All 15 mutants are killed, including the three created by Phase 9 fixes. The remaining gaps are minor strengthenings and documentation tests rather than missing coverage. The suite is ready for Phase 12, with Gap 4 being the most substantive.