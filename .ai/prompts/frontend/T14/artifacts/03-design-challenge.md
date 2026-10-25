# frontend · T14 — Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim):**
> Build the PKCE primitive for the SPA's OIDC client: generate an RFC 7636 code verifier, derive its S256 challenge, and publish both for the authorize request that T15 assembles. First file under `frontend/src/auth/`.

---

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T14 — PKCE verifier**. Findings are against the brief itself; no code was changed.

The brief is precise: fixed 43-character base64url verifiers, S256 challenges, a published validator, and strong test coverage including the RFC Appendix B vector. The main risks are environmental (can the tests run?), a few missing edge cases, and the usual baseline-number uncertainty.

---

## Findings

### 1. The host dependency gap from T12/T13 is still present
**Issue ·** The suite cannot be executed on this host because `frontend/node_modules` is incomplete (`vite-plugin-pwa` is missing). Phase 6 implementation and the AC8 baseline verification therefore cannot be checked here.  
**Severity ·** High — T14 itself adds no package, but its tests run in the existing suite and rely on a working build/test environment.  
**Evidence ·** Earlier attempts to run `vitest` in `frontend/` fail with `ERR_MODULE_NOT_FOUND` for `vite-plugin-pwa`.  
**Recommendation ·** Close the T12 OQ-C blocker before Phase 6. Record in the frozen brief that the environment must have `frontend/node_modules` installed.

### 2. The validator is required against invalid input but not explicitly against the full valid character set
**Issue ·** Required test 5 lists invalid cases: lengths 42 and 129 rejected, 43 and 128 accepted, and strings containing `+`, `/`, `=`, space, non-ASCII rejected. It does not explicitly require a verifier containing `.` and `~` to be accepted. Because the generation scheme never emits those two characters, the validator's acceptance path for them is untested unless deliberately fed.  
**Severity ·** Low — the rule is "every character is from the unreserved set", and `.`/`~` are part of that set.  
**Evidence ·** Brief §Constraints: "base64url never emits `.` or `~`, so an AC1 assertion that reads 'every character is from the unreserved set' would be satisfied over a 64-character subset and could never fail"; Required test 5.  
**Recommendation ·** Add to Required test 5: the validator accepts strings containing `.` and `~` (e.g., a hand-crafted 43-character string that includes them). This closes the only untested valid-character branch.

### 3. Challenge derivation's rejection of an invalid verifier is not listed as a required test
**Issue ·** The brief states under Null handling that "Challenge derivation rejects a verifier that fails validation rather than hashing it." Required tests 1–7 do not explicitly include this case.  
**Severity ·** Low — a malformed verifier passed to the challenge function should not silently produce a challenge.  
**Recommendation ·** Add a required test: calling the challenge function with an invalid verifier (length 42, or containing `+`) rejects with an error or returns a rejected promise. This pins the null-handling rule.

### 4. The exact base64url implementation is not specified
**Issue ·** The brief says the verifier is `base64url(SHA-256(ASCII(verifier)))`, unpadded. It does not say whether the module uses `btoa`, `Buffer`, or a hand-rolled lookup table. `Buffer` is a Node API and would not survive in the browser bundle without a polyfill; `btoa` is browser-only and exists under jsdom. A manual lookup table is the most portable and dependency-free.  
**Severity ·** Low — the choice matters for bundle portability and for whether the source scan needs to allow `Buffer`.  
**Recommendation ·** Lock the frozen brief to a manual base64url encoder (or `btoa` with a documented jsdom assumption). Explicitly forbid `Buffer` in the module source, since the module must run in the browser.

### 5. `crypto.subtle` availability under the test environment is asserted but not guaranteed
**Issue ·** The brief notes that `crypto.getRandomValues` and `crypto.subtle.digest` were verified present under jsdom in phase 0. jsdom historically does not implement `crypto.subtle`; Vitest's jsdom environment may rely on Node's `crypto.webcrypto` being exposed globally. If a future Vitest/jsdom version changes this, the test will fail with a cryptic error.  
**Severity ·** Low — environment-dependent, but the task has no alternative (no npm dependency allowed).  
**Recommendation ·** Add a defensive test setup assertion that `crypto.getRandomValues` and `crypto.subtle.digest` are functions, with a clear message if they are missing. This turns an environment failure into a readable skip/error.

### 6. The tolerance for AC7's character-frequency test is not stated
**Issue ·** AC7 requires "character frequency over a large sample is consistent with uniform within a stated tolerance." The brief never states the tolerance or the sample size. A too-loose tolerance makes the test meaningless; a too-tight one makes it flaky.  
**Severity ·** Low — implementation detail, but should be locked.  
**Evidence ·** AC7; Required test 4.  
**Recommendation ·** Add a locked decision: generate at least 1,000 verifiers (43,000 characters) and assert each of the 64 characters appears within ±10% of the expected mean. Because the scheme is bias-free by construction, this should pass reliably; if it ever fails, it indicates a real defect.

### 7. The baseline `412/414` in AC8 is unverified
**Issue ·** AC8 asserts a whole-suite baseline of 412 passing / 414 total. This cannot be confirmed on this host because dependencies are missing, and it depends on the final counts from T13.  
**Severity ·** Low — typical for a pre-implementation brief, but should be measured rather than asserted.  
**Recommendation ·** Replace the literal baseline with a rule measured during Phase 6: "baseline equals the count from `vitest run` immediately before this task, plus the new T14 cases, and the only failures are the two known environmental T07 cases."

### 8. No guidance on how the validator handles non-string input
**Issue ·** The brief says the validator "accepts an arbitrary `string` and returns a boolean — it never throws." In TypeScript the signature will be `(verifier: string) => boolean`, but callers in JavaScript could pass `null` or a number.  
**Severity ·** Info.  
**Recommendation ·** State whether the validator is required to coerce or reject non-strings. Because the module is authored in TypeScript and the public API is typed, it is reasonable to require only string input and let TypeScript enforce it; document this in the frozen brief so tests do not need to cover `null`/`undefined`.

### 9. The source scan forbids `http` but not `https` or `URL`
**Issue ·** Required test 6 lists forbidden names: `Math.random`, `localStorage`, `document.cookie`, `console`, `http`. It does not include `https`, `URL`, `location`, `history`, or `window.location.href`, all of which could be used to put the verifier into a URL.  
**Severity ·** Low — AC6 already prohibits the outcome, and the module should not touch any URL API.  
**Recommendation ·** Broaden the source scan to include `https`, `URL`, `location`, and `encodeURIComponent` (or justify why they are not needed). Since the verifier must never enter a URL, any URL-construction primitive in the module is suspect.

### 10. The constant for the method name is not tested to prevent accidental lowercasing
**Issue ·** Required test 7 says the method constant is exactly `S256`. This is good. The brief should also lock that the constant is exported and typed as a literal (`export const PKCE_METHOD = 'S256' as const`) so T15 cannot accidentally pass a different string.  
**Severity ·** Info.  
**Recommendation ·** Add a locked decision that the constant is exported as a `const` assertion and that the challenge-derivation function's return type includes `{ method: typeof PKCE_METHOD }`.

### 11. No design for how T15 will consume the pair
**Issue ·** The brief says "publish both for the authorize request that T15 assembles." It does not specify the export shape. A later task could receive `{ verifier, challenge }`, `{ codeVerifier, codeChallenge }`, etc.  
**Severity ·** Low — integration boundary.  
**Recommendation ·** Lock the return shape now, for example:
```ts
export type PkcePair = { verifier: string; challenge: string; method: 'S256' };
export async function generatePkcePair(): Promise<PkcePair>;
```
This avoids a T15 rename and keeps the API stable.

### 12. OQ-1 is correctly carried but will block T15/T16
**Issue ·** T01 forbids `import.meta.env` while §4c permits three public OIDC keys. T14 needs no key, so it is not a blocker here. T15 or T16 will hit it.  
**Severity ·** Info.  
**Recommendation ·** Keep OQ-1 in the frozen brief as an explicit owner decision required before T15/T16 consumes a `VITE_*` key.

---

## Open Questions

- **OQ-C (host dependencies)** remains the only true blocker and must close before Phase 6.
- **OQ-1** is correctly carried for the owner.
- The generation scheme, return shape, base64url implementation, tolerance, and validator edge cases are design decisions that should be locked in the frozen brief.
