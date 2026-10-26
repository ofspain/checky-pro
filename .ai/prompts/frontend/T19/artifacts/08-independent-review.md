# frontend · T19 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `idTokenNonce.ts`, `CallbackScreen.tsx`, `codeExchange.ts`, `callbackWithMismatchedStateIsDiscarded.test.tsx` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

---

## Findings

### I1 · `idTokenNonceMatchesStoredValue` accepts only `string`, but its caller passes `response.idToken` which may be undefined

- **Issue ·** `idTokenNonceMatchesStoredValue(idToken: string)` is typed to require a string, but `CallbackScreen.tsx:121` calls it with `response.idToken`. Under task 20's real response, `idToken` will be a string. Under task 20's error response, the promise rejects before the call site runs. **But** the compile-time type does not encode this: `TokenResponse.idToken` is `string`, so TypeScript is happy, but the runtime boundary is a network response. The self-review (F1) already identified that a non-string input throws because `decodePayload` calls `idToken.split('.')` outside its `try`. The function's doc comment says "Reads exactly one stored item and never throws," which is false for non-string input.
- **Evidence ·** `idTokenNonce.ts:52` — `function decodePayload(idToken: string)` calls `idToken.split('.')` before any validation. `idTokenNonce.ts:104` — `export function idTokenNonceMatchesStoredValue(idToken: string)`. `CallbackScreen.tsx:121` — `idTokenNonceMatchesStoredValue(response.idToken).ok`.
- **Recommendation ·** Widen the signature to `idToken: unknown`, reject non-strings as `'malformed-token'` before touching `.split`, and update the doc comment to say the function rejects rather than throws for any malformed input. This is the same as the self-review's F1.
- **Confidence · High.** This is a confirmed bug: the self-review's probe already demonstrated the throw.

---

### I2 · A throwing `discard()` inside `.then` would run the discard handler twice

- **Issue ·** `CallbackScreen.tsx:116-123` chains `.then(...).catch(discard)`. If the `.then` handler throws — e.g., because `navigate` or `setSignInNotice` fails — the rejection propagates into `.catch(discard)` and `discard()` runs a second time. Both calls are `replace` navigations to the same path, so the visible result is identical today, but it is still a latent double-invocation on a screen whose entire design is built around single-invocation (the `handled` ref exists for StrictMode for exactly this reason).
- **Evidence ·** `CallbackScreen.tsx:116-123`.
- **Recommendation ·** Use the two-argument form: `exchangeAuthorizationCode(...).then(onFulfilled, discard)`. This routes a fulfillment-handler failure away from the rejection handler. Alternatively, guard `discard()` itself so it is idempotent.
- **Confidence · High.** Same as self-review F3; confirmed as a real pattern defect.

---

### I3 · The test helper `idTokenWith` cannot exercise `TextDecoder` because `btoa` rejects non-ASCII

- **Issue ·** `idTokenWith` in `callbackWithMismatchedStateIsDiscarded.test.tsx:75-79` uses `btoa(JSON.stringify(claims))` to encode the payload. `btoa` throws on any code point above U+00FF, so the helper cannot produce a token whose payload contains non-ASCII characters. Production code uses `TextDecoder` to handle arbitrary UTF-8 byte sequences in the payload. Therefore the `TextDecoder` path in `decodePayload` is currently unreachable by the test suite; replacing it with plain `atob` would pass all tests.
- **Evidence ·** `callbackWithMismatchedStateIsDiscarded.test.tsx:75-79`. `idTokenNonce.ts:65` — `new TextDecoder().decode(...)`.
- **Recommendation ·** Either (a) update `idTokenWith` to use `TextEncoder` plus a manual base64url encode so it can produce non-ASCII payloads and add a test that kills a mutant dropping `TextDecoder`, or (b) remove `TextDecoder` from production and state that only ASCII payloads are decoded. The self-review (F4) argues for (a); I agree that untested robustness is worse than absent robustness.
- **Confidence · High.** Confirmed by the helper's use of `btoa`.

---

### I4 · `decodePayload` returns `null` for a two-segment JWT, but the function comment says "exactly three" without explaining why two is rejected

- **Issue ·** `idTokenNonce.ts:56` rejects any segment count other than 3. A two-segment JWT is an unsecured JWS (alg=none), which SAS does not issue and which the SPA should not accept. A four-segment token is JWE. The comment says "exactly three" but does not say what threat two-segment acceptance would introduce. Since this is a security decision, the comment should state it plainly.
- **Evidence ·** `idTokenNonce.ts:54-56`.
- **Recommendation ·** Expand the comment: "Two-segment tokens are unsecured JWTs (alg=none). Accepting one would let an attacker substitute an unauthenticated payload. Four-segment tokens are encrypted (JWE). The token endpoint returns signed, unencrypted ID tokens, so three is the only acceptable shape."
- **Confidence · Low.** Documentation improvement, not a bug.

---

### I5 · The `TokenResponse` type only contains `idToken`, but task 20 will add more fields

- **Issue ·** `codeExchange.ts:27-29` defines `TokenResponse = { idToken: string }`. The comment says task 20 will add `access_token`, `expires_in`, etc. This is fine as a seam, but the type will need to grow. More importantly, the comment states the real response carries no `refresh_token`, which is the empirical basis for L3 — but that fact is now embedded in a seam file rather than in a test or ADR. If the auth service ever changes, this comment will rot.
- **Evidence ·** `codeExchange.ts:19-29`.
- **Recommendation ·** No code change needed now, but ensure task 20's tests or a dedicated suite assert the absence of `refresh_token` in the real response, so the comment's empirical claim is pinned by a test rather than by documentation.
- **Confidence · Low.** Process observation.

---

### I6 · The `NonceRejection` type is exported but production never reads `.reason`

- **Issue ·** `NonceRejection` and `NonceResult` are exported, and the reasons are used by tests to distinguish rejection modes. Production only checks `.ok`. This is a deliberate design choice, but it means the type is part of the public API of `idTokenNonce.ts` solely for testability. A future refactor might be tempted to render or log the reason, which would violate L4.
- **Evidence ·** `idTokenNonce.ts:32-39`, `CallbackScreen.tsx:121`.
- **Recommendation ·** Keep the type and reasons, but add a source-scan test in the new T19 suite that asserts no `NonceRejection` value is concatenated into a string, passed to `setSignInNotice`, or rendered. This turns the export from "unused surface" into "constrained surface."
- **Confidence · Medium.** Same as self-review F5; the recommendation is slightly stronger (add a scan).

---

### I7 · `CallbackScreen.tsx` no longer comments that the nonce check happens after the exchange, which could be misread as the order being reversible

- **Issue ·** The comment at `CallbackScreen.tsx:112-114` explains that the seam rejects until task 20 fills it, but it does not explicitly state that the nonce check must run **after** a successful exchange and cannot be moved before it. AC15 requires R8 to hold before any nonce work. A later refactor might reasonably move the nonce check earlier for perceived efficiency; the code should document why that would be wrong.
- **Evidence ·** `CallbackScreen.tsx:112-123`.
- **Recommendation ·** Add a comment at line 117: "The nonce check runs inside the exchange's fulfillment, after the state check admitted the callback. Moving it earlier would violate R8's ordering: a bad state must discard before any nonce read."
- **Confidence · Low.** Defensive documentation.

---

### I8 · The `spyOnExchange` mock reads the stored nonce to mint a token, which masks a failure mode

- **Issue ·** The updated `spyOnExchange` in `callbackWithMismatchedStateIsDiscarded.test.tsx:101-107` reads `AUTHORIZE_STORAGE_KEYS.nonce` and mints a token carrying it. This is realistic for a success path. However, it means the T18 suite's success-path cases now implicitly depend on the nonce being stored. A regression in `buildAuthorizeRequest` that stopped writing the nonce would not be caught here; it would be caught only in the new T19 suite. This is not a defect, but it is a coupling worth noting.
- **Evidence ·** `callbackWithMismatchedStateIsDiscarded.test.tsx:90-109`.
- **Recommendation ·** No change to T18's suite; ensure the new T19 suite has a case that exercises the success path without a stored nonce and asserts rejection. The brief already requires this (AC8), but it is worth confirming in Phase 10.
- **Confidence · Low.** Already covered by AC8 in the brief.

---

### I9 · `storedNonce()` normalizes `''` to `null`, but the comparison function does not document why this matters

- **Issue ·** `storedNonce` normalizes an empty stored string to `null` so the caller has one absence (`idTokenNonce.ts:92`). The comment explains that `'' === ''` would otherwise match a token with an empty nonce claim. However, the comparison function's comment (`idTokenNonce.ts:95-103`) does not mention this normalization. A reader of `idTokenNonceMatchesStoredValue` might wonder why `stored === null` is the only absence check.
- **Evidence ·** `idTokenNonce.ts:84-93`, `idTokenNonce.ts:119-120`.
- **Recommendation ·** Add one sentence to the comparison function's comment: "The stored value is normalized to `null` when it is missing or empty, so only one absence value is compared."
- **Confidence · Low.** Documentation.

---

### I10 · No test exists yet for `callbackValidatesNonce`

- **Issue ·** The named test `callbackValidatesNonce` (`package.md:175`) does not have a test file yet. Phase 6 notes that `callbackValidatesNonce.test.tsx` is Phase 10's responsibility. This is by design, but it means the implementation's correctness against R7 is not yet independently verified. The self-review and this review can only assess the production code and the updated T18 suite.
- **Evidence ·** `frontend/src/auth/callbackValidatesNonce.test.tsx` does not exist. `artifacts/06-implementation-notes.md:48` — "`callbackValidatesNonce.test.tsx` is **Phase 10's**."
- **Recommendation ·** Ensure Phase 10 creates the file and covers every AC, with special attention to AC6 (non-vacuous comparison against storage) and the test-double-reachability lesson from Phase 6's D-2.
- **Confidence · High.** This is a scheduling observation, not a code defect.

---

## Summary

| # | Issue | Severity | Recommendation |
|---|---|---|---|
| I1 | Non-string `idToken` throws, contradicting "never throws" | **High** | Widen signature to `unknown`, reject non-strings as `malformed-token` |
| I2 | `.then(...).catch(discard)` can run discard twice | **Medium** | Use `.then(onFulfilled, discard)` |
| I3 | Test helper cannot produce non-ASCII payload; `TextDecoder` path untested | **Medium** | Update helper to use `TextEncoder` + base64url, or remove `TextDecoder` |
| I4 | Two-segment JWT rejection unexplained in comment | Low | Expand comment on why unsecured JWTs are rejected |
| I5 | `TokenResponse` will grow; `refresh_token` absence is comment-only | Low | Pin absence with a test in task 20 |
| I6 | `NonceRejection` exported but unread in production | Low | Add scan asserting reasons never reach DOM/log |
| I7 | Ordering of nonce check after exchange not explicitly documented | Low | Add comment guarding against future reordering |
| I8 | T18 success-path mock now depends on stored nonce | Low | Ensure T19 suite covers missing-nonce success rejection |
| I9 | `''` → `null` normalization not mentioned in comparison comment | Low | Add sentence to comparison function doc |
| I10 | `callbackValidatesNonce.test.tsx` does not exist yet | — | Phase 10 must create it |

**Verdict:** The implementation is architecturally sound and correctly scoped. The security-critical properties — exact comparison, guarded storage read, no token retention, L4-indistinguishable discard — are all in place. The only material defects are I1 (confirmed throw on non-string input) and I2 (double-discard hazard), both already flagged in the self-review and both straightforward to fix in Phase 9. I3 is the most important test-quality gap for Phase 10.