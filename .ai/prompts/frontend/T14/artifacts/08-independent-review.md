# frontend · T14 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `frontend/src/auth/pkce.ts`, `frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> Build the PKCE primitive for the SPA's OIDC client: generate an RFC 7636 code verifier, derive its S256 challenge, and publish both for the authorize request that T15 assembles. First file under `frontend/src/auth/`.

---

## Review scope

Reviewed the Phase 6 T14 implementation against the frozen brief, `agents.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is clean and correct: `btoa`-based base64url, full RFC unreserved validator, typed `S256` constant, validation before hashing, and a thorough test file. The self-review found seven issues; this review corroborates them and adds three fresh observations.

---

## New findings

### 1. The environment check runs too late
**Issue ·** The `beforeAll` block generates 2,000 verifiers by calling `generateCodeVerifier()`, which immediately needs `crypto.getRandomValues`. The dedicated `it('exposes the two Web Crypto calls...')` test is nested inside the same describe, so it runs *after* `beforeAll`. If `crypto.getRandomValues` is missing, `beforeAll` throws first and the readable environment-check test never executes, producing a stack trace instead of the intended failure message.  
**Severity ·** Low — the failure is still visible, but the diagnostic value of the environment test is lost.  
**Evidence ·** `pkceVerifierMeetsRfc7636.test.ts:81-93`.  
**Recommendation ·** Move the environment assertion out of `beforeAll`'s dependency path. Either make it a standalone top-level `it` that runs before the sample-generating `beforeAll`, or guard the sample generation itself with a clear check and message.

### 2. The `URL` forbidden-name check is substring-based and fragile
**Issue ·** The source scan uses `code.includes('URL')`. This catches `URL.createObjectURL`, `new URL(...)`, and `URL` as an identifier, but it would also flag a helper renamed to `base64URLFromBytes` even though that identifier has nothing to do with the global `URL`. Conversely, it does not catch `globalThis.URL` or `window.URL`.  
**Severity ·** Low — the current helper name avoids the false positive, but the rule is brittle.  
**Evidence ·** `pkceVerifierMeetsRfc7636.test.ts:64` and `:227-228`.  
**Recommendation ·** Replace the substring check with a regex that matches `URL` as a standalone identifier: `/\bURL\b/`. This keeps the reach for `URL.`/`new URL(` while avoiding false positives from mixed-case identifiers. Add a case verifying that `base64UrlFromBytes` does not trigger the rule.

### 3. No test pins the number of bytes drawn from the CSPRNG
**Issue ·** `generateCodeVerifier` is documented to request 32 bytes from `crypto.getRandomValues`. The suite tests the *output* length (43 characters) and statistical properties, but it does not assert that the input array passed to the CSPRNG is exactly 32 bytes. A regression that changed `VERIFIER_BYTE_LENGTH` to 31 or 33 would still produce a valid-looking verifier (base64url of 31 bytes is 42 chars, failing AC1; base64url of 33 bytes is 44 chars, also failing AC1), so the length tests would catch it. However, the direct contract with the CSPRNG is not pinned.  
**Severity ·** Low — AC1 would catch the regression indirectly, but the test does not isolate the mechanism.  
**Evidence ·** `pkce.ts:26`, `pkce.ts:69-73`.  
**Recommendation ·** Add a test that mocks `crypto.getRandomValues` with `vi.spyOn(crypto, 'getRandomValues')` and asserts it is called once with a `Uint8Array` of length 32. This documents the exact entropy source and catches a change to `VERIFIER_BYTE_LENGTH` at the call site.

### 4. No test exercises `generatePkcePair` producing distinct pairs
**Issue ·** The uniqueness test covers `generateCodeVerifier` over a 2,000-item sample, but `generatePkcePair` is only tested for shape and method. It returns `{ verifier, challenge }`, and the challenge is deterministically derived from the verifier. If `generatePkcePair` somehow reused a verifier, the pair-level test would not notice.  
**Severity ·** Info.  
**Evidence ·** `pkceVerifierMeetsRfc7636.test.ts:122-130`.  
**Recommendation ·** Optional: add a case that generates two pairs and asserts their verifiers differ. This is cheap and mirrors the existing uniqueness test at the public API level.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| S1 | Comment stripper eats `//` inside string literals | **Confirmed — this is the most important defect.** The regex `//.*$` strips from any `//` to end of line, even inside a string. Fix by stripping line comments only when `//` begins the trimmed line. |
| S2 | Positive CSPRNG assertion runs over raw source, not stripped code | **Confirmed.** `moduleSource.includes('crypto.getRandomValues')` would pass if the name only appeared in a doc comment. Combine with S1 fix: assert the positive over stripped code too. |
| S3 | `URL` substring check is brittle | **Confirmed.** See New Finding 2 for a concrete replacement. |
| S4 | `.length` counts UTF-16 code units | **Confirmed, not a defect.** The `UNRESERVED_PATTERN` rejects all non-ASCII code points, so code units and code points coincide for any accepted string. |
| S5 | Derivation never exercised on a long verifier | **Confirmed, real coverage gap.** Add a case deriving a challenge for a 128-character verifier and asserting it is well-formed base64url and differs from a 43-character challenge. |
| S6 | `generatePkcePair` re-validates its own verifier | **Confirmed, deliberate and sound.** It is redundant by construction but keeps exactly one path to a challenge. |
| S7 | No runtime leak assertion | **Confirmed, low-value addition.** Add a test that spies on `localStorage`, `sessionStorage`, and `console` during `generatePkcePair()` and asserts none are called. This complements the source scan. |

---

## Non-gaps (acknowledged, no change needed)

- **AC3 (Appendix B vector)** is the load-bearing case and is correctly implemented against the published vector.
- **AC4 (base64url, no padding)** is tested with both a regex and the Appendix B vector.
- **AC5 (S256, not plain)** is tested by challenge ≠ verifier, one-character avalanche, and the `S256` method constant.
- **AC6/AC10 (no leak, browser-safe)** are enforced by the source scan and the `btoa` / no-Node-global assertion.
- **AC7 (no bias)** uses a measured chi-square threshold over 2,000 verifiers and documents the honest limit.
- **AC1's non-vacuity is paid for** by feeding the validator deliberately invalid and boundary input, including `.` and `~`.
- **Null handling is correct:** `isValidCodeVerifier` never throws; `deriveCodeChallenge` rejects invalid verifiers.
- **Module boundaries are respected:** `pkce.ts` imports nothing from `app/`, `pwa/`, or `features/`.
- **No file outside `src/auth/` was modified**.
- **The return type is stable:** `PkcePair` with `{ verifier, challenge, method: typeof PKCE_CODE_CHALLENGE_METHOD }`.

---

## Summary

T14's PKCE implementation is correct and well-tested. The highest-priority Phase 9 fixes are the comment-stripper bug (S1) and the positive CSPRNG assertion over stripped code (S2); both are guard weaknesses in the test itself. The remaining items are small hardening: a 128-character derivation case, a runtime no-leak assertion, an environment check that runs before sample generation, a word-boundary `URL` rule, and a CSPRNG call-site test. None of these change the correctness of the module.

**Note:** This review was performed statically; the suite could not be executed on this host because `frontend/node_modules` is incomplete (`vite-plugin-pwa` is missing), continuing the T12 OQ-C blocker.
