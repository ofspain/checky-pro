# frontend · T15 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `frontend/src/auth/authorizeRequest.ts`, `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts`, `frontend/src/auth/pkce.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> Assemble the OIDC authorization request: exactly the three declared scopes, `state`, `nonce`, `code_challenge_method=S256` and the PKCE challenge from T14 — and persist the three L16 items the callback tasks will read back.

---

## Review scope

Reviewed the Phase 6 T15 implementation against the frozen brief, `agents.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is strong: it constructs the URL from `location.origin`, derives the redirect URI from `APP_BASENAME`, stores exactly three keys, keeps the verifier out of the return type and the URL, and has a sharp AC3 guard that recomputes the challenge from the stored verifier. The self-review found seven issues; this review corroborates them and adds one consolidated observation on the leak matcher.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| S1 | Parameter set never asserted to be exactly the eight | **Confirmed — the most important defect.** Add an assertion that `[...searchParams.keys()].sort()` equals the eight expected names exactly, plus a negative control with a ninth parameter. |
| S2 | "Percent-encoded" leak row is vacuous | **Confirmed.** A verifier contains only RFC 7636 unreserved characters, so `encodeURIComponent(verifier) === verifier`. The row tests nothing. |
| S3 | Matcher throws on malformed escapes | **Confirmed.** `decodeURIComponent` raises `URIError` on malformed input; the real URL is well-formed today, but the predicate should report, not crash. |
| S4 | Nothing asserts a second request replaces the first | **Confirmed, real gap.** Add two sequential calls and assert storage contains only the second request's three values. |
| S5 | `location` stub supplies only `origin` | **Confirmed.** The stub is narrower than the global it replaces. Either expand it or pin with a source-level assertion that the module reads only `origin`. |
| S6 | Substring matcher cannot detect encoded/split verifier | **Confirmed, by design.** Document the limitation in the test file so readers do not over-trust it. The structural guard (verifier absent from return type) is the stronger defence. |
| S7 | `toHaveLength(43)` couples T15 to T14's default | **Confirmed, informational.** The coupling is defensible but should be named; consider deriving the expected length from `randomUrlSafeToken().length`. |

---

## New finding

### 1. The verifier leak matcher should not decode the URL
**Issue ·** `urlLeaksVerifier` implements its check as `decodeURIComponent(url).includes(verifier)`. This combines S2 and S3: the decode is unnecessary because the verifier characters are URL-safe, and it introduces a crash path for malformed escapes. It also misleads the reader into thinking percent-encoding is a channel that could hide the verifier.  
**Severity ·** Medium — a predicate used in negative controls must be robust and honest about what it detects.  
**Evidence ·** `authorizeRequestsExactlyTheDeclaredScopes.test.ts:36-38`; the self-review measured that the "percent-encoded" row is byte-identical to the plain row and that `decodeURIComponent('https://x?a=%E0%A4%A')` throws.  
**Recommendation ·** Replace the matcher with `url.includes(verifier)`. The verifier is 43 characters from the RFC 7636 unreserved set, so raw substring search is sufficient and exact. Update the test comment to state explicitly that percent-encoding cannot hide these characters. The nested-redirect-uri and fragment cases still pass because the verifier appears literally in the resulting URL string.

---

## Non-gaps (acknowledged, no change needed)

- **AC1 (exact scopes)** is pinned by both value equality and the `scopeIsExactlyDeclared` matcher fed wrong inputs.
- **AC2 (method constant)** is pinned by importing `PKCE_CODE_CHALLENGE_METHOD` and a source scan forbidding quoted `'S256'`.
- **AC3 (stored verifier / sent challenge correspondence)** is pinned by reading the stored verifier back and recomputing its challenge.
- **AC4 (state/nonce)** is pinned by presence, distinctness, length, freshness over 50 requests, and the CSPRNG source scan.
- **AC5 (response_type, client_id, redirect_uri)** is pinned by value checks, single-occurrence `checky-spa` guard, and origin-following test.
- **AC6 (verifier absent from URL)** is pinned by substring matching, parameter iteration, and absence of `code_verifier` key.
- **AC7 (only three sessionStorage keys)** is pinned by key set equality and runtime Storage/console spies.
- **AC8 (drift guards)** are pinned by parsing `agents.md`, the edge Caddyfile, and `routes.tsx`.
- **AC9 (non-vacuity)** is partially paid by the scope matcher and leak-match negative controls; S1 and S2 close the remaining holes.
- **AC10 (suite delta / no files modified)** is asserted in the brief and verified by re-running T14's suite.
- **AC11 (fail closed)** is pinned by mocked invalid-verifier and derivation-failure cases that assert empty storage.
- **Module boundaries respected:** `authorizeRequest.ts` imports only from `src/auth/` and `src/app/routes`.
- **No `fetch`/`XMLHttpRequest`/`Buffer`/`import.meta.env`/absolute URL literal** in the module.
- **T14's `pkce.ts` change is sound:** `randomUrlSafeToken` is exported, `generateCodeVerifier` delegates, and T14's CSPRNG spy and source scans still hold.

---

## Summary

T15's implementation correctly starts the OIDC flow and secures the verifier. The highest-priority Phase 9 fixes are the exact-parameter-set assertion (S1) and the honest leak matcher (S2/S3 / New Finding 1). The remaining items are smaller: overwrite-on-retry (S4), a fuller `location` stub or source-level pin (S5), documented matcher limitations (S6), and naming the 43-character coupling (S7). All are test improvements; the module itself is sound.

**Note:** This review was performed statically; the suite could not be executed on this host because `frontend/node_modules` is incomplete (`vite-plugin-pwa` is missing), continuing the T12 OQ-C blocker.
