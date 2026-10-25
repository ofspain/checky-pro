# frontend · T14 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> Build the PKCE primitive for the SPA's OIDC client: generate an RFC 7636 code verifier, derive its S256 challenge, and publish both for the authorize request that T15 assembles. First file under `frontend/src/auth/`.

---

## Method

Reviewed the Phase 10 test manifest for `pkceVerifierMeetsRfc7636` (48 cases, 17 mutation breaks, 14 caught) against the frozen T14 acceptance criteria and the Phase 8 findings. No code was rewritten.

The Phase 9 resolution addressed every Phase 8 finding: the environment check now guards sample generation, the 128-character derivation case is present, the CSPRNG call site is spied, pair-level uniqueness is tested, runtime spies complement the source scan, forbidden names are matched as whole identifiers, the positive CSPRNG assertion runs over stripped code, and the comment stripper no longer eats `//` inside strings.

---

## Gaps

### 1. The block-comment stripper has the same shape of hole as the line-comment stripper did
**Gap ·** `codeWithoutComments` removes block comments with `/\/\*[\s\S]*?\*\//g`. If a string literal contains `/*` and a forbidden name appears later before the next `*/`, the regex will strip the forbidden call along with the synthetic "comment". This is the block-comment analog of S1 and is not closed.  
**Why it matters ·** A real leak could be hidden behind a string containing `/*` followed by a storage/URL/log call on the same logical stretch of code.  
**Suggested test ·** Add a unit test for `codeWithoutComments` that feeds it a synthetic source such as `const x = '/*'; localStorage.setItem('k','v');` and asserts `localStorage` is still detected. Then fix the stripper. A robust fix is to avoid regex-based comment stripping entirely and use a tiny lexer or the TypeScript compiler API; a lighter fix is to replace the block-comment regex with a line-by-line scan that only treats `/*` and `*/` as comment delimiters when not inside a string literal.

### 2. The runtime storage spy only watches `setItem`
**Gap ·** The runtime no-leak assertion spies on `sessionStorage.setItem`, `localStorage.setItem`, and common `console` methods. It does not watch `getItem`, `removeItem`, `clear`, `key`, or indexed access on `Storage` objects.  
**Why it matters ·** A leak through `localStorage['pkce'] = verifier` or `sessionStorage.getItem(...)` would not be caught by the current spy set, even though the source scan should catch the name. The runtime and static guards should cover the same surface.  
**Suggested test ·** Broaden the spies to cover all `Storage.prototype` methods and property descriptors, or add a separate assertion that no `Storage` method is called. Alternatively, assert that the module source contains no `localStorage`/`sessionStorage` identifier at all (already done), and document that the runtime spy is a sample, not exhaustive.

### 3. `deriveCodeChallenge` is not tested at the upper-boundary invalid length
**Gap ·** The validator table tests 129 as invalid, and `deriveCodeChallenge` is tested rejecting 42, a `+`-containing string, and empty string. It is not explicitly tested rejecting a 129-character verifier.  
**Why it matters ·** The published contract is 43–128. A regression that made `deriveCodeChallenge` accept 129 characters would be caught by the validator table only if callers used `isValidCodeVerifier` first; `deriveCodeChallenge` is advertised as self-validating.  
**Suggested test ·** Add one row to the AC9 rejection case: `await expect(deriveCodeChallenge('a'.repeat(129))).rejects.toThrow(TypeError)`.

### 4. No test exercises the validator with a 128-character verifier containing mixed unreserved characters
**Gap ·** The 128-character valid case in the validator table is `'a'.repeat(128)`. The `.`/`~` case is length 43. A 128-character string mixing `.`/`~` with base64url characters is not tested.  
**Why it matters ·** The validator's length and pattern checks are independent, but a boundary-length mixed string is the most stressful valid input.  
**Suggested test ·** Add a case: `isValidCodeVerifier('a'.repeat(126) + '.~')` returns true.

### 5. The `console` spy does not cover every `console` method
**Gap ·** The runtime spy covers `log`, `debug`, `info`, `warn`, `error`. It does not cover `table`, `dir`, `trace`, `group`, `time`, `assert`, or custom `console` methods.  
**Why it matters ·** A leak could be printed via `console.table({ verifier })`. The static forbidden-name scan catches `console`, so this is defense in depth.  
**Suggested test ·** Spy on `console` as a whole object (e.g., `vi.spyOn(console, 'table')` etc.) or document that the runtime spy is illustrative and the source scan is the authoritative guard.

### 6. No negative-control test for the whole forbidden-name scanner
**Gap ·** Each forbidden name is tested by the `it.each(FORBIDDEN_IN_SOURCE)` over the real module source, where none of the names are present. There is no synthetic source containing a forbidden name fed to the same detection logic.  
**Why it matters ·** The scanner is simple (`codeWithoutComments` + regex), but a regression in `FORBIDDEN_IN_SOURCE` patterns would be silently green because the real module contains none of them.  
**Suggested test ·** Add a case that calls `codeWithoutComments` on a synthetic snippet containing each forbidden pattern and asserts each is detected. This mirrors T13's negative-control approach.

---

## Non-gaps (acknowledged, no change needed)

- **48/48 cases pass** and **14/17 mutants caught**.
- **M12 survived** because the shared-buffer mutant is equivalent: `getRandomValues` refills synchronously and `base64UrlFromBytes` reads before any `await`. This is correctly reported as an equivalent mutant.
- **M14 survived** because raising the χ² threshold is a guard weakening; it is closed by M13, which proves the bias guard has real reach.
- **M15 survived** because restoring the broken comment stripper is a guard weakening; it is closed by M16/M17, which prove the fixed stripper catches a real leak and the broken one misses it.
- **AC3 is pinned** by the RFC 7636 Appendix B vector, which cannot be satisfied by a wrong hash.
- **AC4 is pinned** by the `[^=+/]` assertion and the M3/M4 mutants.
- **AC5 is pinned** by challenge≠verifier, avalanche, the literal-typed `S256` constant, and M5/M6.
- **AC1 is pinned** by the validator table including the `.`/`~` non-vacuity case and M1/M9/M10.
- **AC2 is pinned** by the CSPRNG spy, the uniqueness sample, and the M11 mutant.
- **AC6 is pinned** by the source scan, runtime spies, and M16.
- **AC7 is pinned** by χ², all-64-reached, the 4-bit tail case, and M13.
- **AC9 is pinned** by the invalid-verifier rejection cases and M8.
- **AC10 is pinned** by the no-Node-global/`btoa` assertion. The manifest honestly states this has no mutant because the suite has no browser.
- **AC8 suite delta** is 414 → 460, exactly +48, with the two known environmental T07 failures.
- **No T01–T13 file modified**; only `frontend/src/auth/pkce.ts` and its test were added.
- **The χ² test's limitation is honestly documented:** it validates `btoa`'s mapping, not the module's arithmetic.

---

## Summary

T14's test suite is excellent: 48 cases, strong mutation coverage, honest survivor reporting, and every Phase 8 finding resolved. The main remaining gaps are the block-comment stripper analog of S1, the runtime spy surface being narrower than the static scan, and a few boundary cases for the validator and challenge derivation. Adding these would make the no-leak guard fully symmetric between static and runtime checks and close the last regex-based stripping hole.
