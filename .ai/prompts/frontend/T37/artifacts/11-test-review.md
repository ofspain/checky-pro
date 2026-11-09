<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T37 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **28/28 passed** |
| `python3 -I .ai/prompts/frontend/T37/artifacts/07-mutation-harness.py` | **29/29 regressions killed, 0 survivors, 0 broken, 0 canaries** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1690/1691 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### F1 — The `accessToken` source scan only forbids `accessToken(`, not `accessToken.current()`, `.adopt()` or `.renew()`
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:111` lists `[\baccessToken\s*\(`, 'void accessToken();']`. `frontend/src/auth/accessToken.ts:45-68` exports `accessToken` as a `LocalSession` object whose methods are `current()`, `adopt()` and `renew()`.
· **Assessment:** A screen could read the session by calling `accessToken.current()` or mutate it with `accessToken.adopt('x')` / `accessToken.renew()` without matching the `/\baccessToken\s*\(/` pattern. The output-equality cases (AC6, AC13) only catch reads that change the rendered markup; a read that branches to identical markup would pass. The central constraint — that the screen makes no claim about session state — is therefore not fully pinned by the source sweep.
· **Recommendation:** Replace `/\baccessToken\s*\(/` with `/\baccessToken\b/` so any use of the identifier is forbidden, or add explicit entries for `/accessToken\.current\s*\(/`, `/accessToken\.adopt\s*\(/`, and `/accessToken\.renew\s*\(/`. Add a control for each planted pattern. Verify the new mutant SL25 variant using `accessToken.current()` is killed.

### F2 — The `accessToken` import scan assumes single-quoted paths and will miss double-quoted imports
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:116` uses `/from '\.\/(?:accessToken|idToken)'/`.
· **Assessment:** The codebase consistently uses single quotes, and oxlint probably enforces this, but a source scan should not rely on formatter discipline to detect a token read. A double-quoted import would survive the import scan; whether the function-call scan catches it depends on the call shape, and F1 shows even the direct-call scan is incomplete.
· **Recommendation:** Make the import scan quote-agnostic: `/from ["']\.\/(?:accessToken|idToken)["']/`. Add a control with double quotes.

### F3 — The `FORBIDDEN_REACHES` sweep does not forbid `useReducer`, `useMemo`, `useCallback`, `useId` or `useContext`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:105-107` forbids `useEffect`, `useState`, `useRef`. It omits other React hooks.
· **Assessment:** `useReducer` is stateful; `useMemo`/`useCallback` could cache a token read or a side effect; `useContext` could pull session state from a provider; `useId` is harmless but unnecessary. The screen is deliberately static, so any hook is a deviation worth flagging.
· **Recommendation:** Add `/\buseReducer\b/`, `/\buseMemo\b/`, `/\buseCallback\b/`, and `/\buseContext\b/` to the sweep. `useId` can be excluded unless the project wants to forbid all hooks.

### F4 — The structural case (FU-T37-1) checks tag names but not that `<main>` has no conflicting ARIA role
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:513-525` asserts one `<main>`, one `<h1>`, no subheadings, and children `['H1', 'P', 'A']` in order.
· **Assessment:** A mutant could render `<main role="region">` or `<h1 role="heading" aria-level="2">`. The tag-name assertion would still pass, but the semantic landmark/heading level would be wrong. This is unlikely but within the scope of "structural regressions."
· **Recommendation:** Assert `main!.getAttribute('role')` is null and the `<h1>` has no `aria-level` attribute. Low priority.

### F5 — The file name still promises an exhaustive public-route claim T37 does not make
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:1-12` carries a long header explaining the shared ownership. The file tests one route out of nine.
· **Assessment:** Phase 8 raised this as I4. Phase 10 did not rename. The header is honest, but the name remains misleading for anyone grepping for the task-40 claim.
· **Recommendation:** Rename the file to `signedOutRouteIsPublicAndRenders.test.tsx` (or similar) for T37, and let T40 create or rename the exhaustive file. If the shared name must stay, add a comment in the `describe` block itself, not just the file header.

### F6 — AC10 does not verify `SIGNED_OUT_PATH` is the *only* literal route reference in `endSession.ts`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:439-444` scans `postLogoutRedirectUri()` for `SIGNED_OUT_PATH` and the absence of `'signed-out'`.
· **Assessment:** This is good, but a mutant could spell `APP_BASENAME` literally or duplicate the route-building logic elsewhere in the file. Not a gap that has been observed.
· **Recommendation:** None, unless a future mutant demonstrates it.

### F7 — The mutation harness numbering skips SL24
· **Severity:** **Informational**
· **Evidence:** The harness output lists SL01-SL23, then SL25-SL30, with no SL24.
· **Assessment:** This does not affect correctness. It may confuse a reader counting mutants.
· **Recommendation:** Either renumber to remove the gap or add a comment explaining SL24 was removed/renamed.

### F8 — No test verifies the `Link`'s `to` prop is built from `SIGN_IN_PATH` rather than a literal
· **Severity:** **Minor**
· **Evidence:** AC5 checks the rendered `href` equals `${APP_BASENAME}/${SIGN_IN_PATH}`. AC13 checks `SIGN_IN_PATH` is read inside the component, not at module scope.
· **Assessment:** The combination is strong: if the component reads `SIGN_IN_PATH` inside the render and the href matches, the link is built from the constant. A source scan for `\`\$\{SIGN_IN_PATH\}\`` or `/to={\`\/\$\{SIGN_IN_PATH\}\`}/` would make it explicit.
· **Recommendation:** Add a source scan asserting `SIGN_IN_PATH` appears in the JSX expression for the `Link`'s `to` prop. Low priority.

### F9 — The `AC7` "sends no request" runtime test stubs `fetch` but does not stub `XMLHttpRequest`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:327-336` stubs `fetch` and `setTimeout`. The source scan forbids `XMLHttpRequest`.
· **Assessment:** The source scan is the real guard. The runtime test is a belt-and-suspenders check for `fetch`. This is acceptable.
· **Recommendation:** None.

### F10 — No test asserts the screen renders no className or style attributes that might leak state
· **Severity:** **Minor**
· **Evidence:** AC11 asserts the rendered text contains no identifying strings. It does not inspect attributes.
· **Assessment:** A mutant could render a data attribute or class name that encodes the token. Unlikely and out of scope for the current criteria.
· **Recommendation:** None.

### F11 — The `FORBIDDEN_REACHES` probe count control only checks the count, not that each probe is distinct
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:355` asserts `FORBIDDEN_REACHES` has length 22.
· **Assessment:** This prevents accidental deletion of a row. It does not prevent two rows from having the same regex (which would be a mistake).
· **Recommendation:** Add a control asserting all 22 regexes are distinct. Low priority.

### F12 — The screen has no production caller path from sign-out
· **Severity:** **Blocker** (testability / process)
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:543` states explicitly that nothing calls `endSession()`.
· **Assessment:** This is the same limit T36 had. The screen is verified only by direct arrival. It is recorded honestly, but it remains a testability gap that no test in this task can close.
· **Recommendation:** None for T37. Close when the sign-out affordance is built.

### F13 — `routes.tsx:5`'s false comment is still not fixed
· **Severity:** **Minor**
· **Evidence:** The self-review and Phase 8 noted this as FU-T37-2.
· **Assessment:** It is in T16's closed file and is a comment, not an instrument. The Phase 10 suite has a correct scan for module-scope reads.
· **Recommendation:** Leave as a follow-up for T16's owner.

---

## What the test suite gets right

- **S1 — Phase 10 closed FU-T37-1 properly.** The structural case is now a named criterion with a control, not a side effect.
- **S2 — Phase 8's I1 and I2 were accepted.** The `FORBIDDEN_REACHES` sweep now covers token-reading and document/window surfaces, with individual controls for each of the 22 patterns.
- **S3 — Phase 8's I3 was accepted.** AC10 now verifies `SIGNED_OUT_PATH` is actually used in `postLogoutRedirectUri`, not just imported.
- **S4 — Phase 8's I5 and I12 were accepted.** `role="button"` and `NavLink` are now forbidden and mutants verify each is killed.
- **S5 — The mutation harness is honest and independently reproducible:** 29/29 killed, 0 broken, with every mutant's intended case verified narrowly.

---

## Summary

Phase 10 produced a strong, corrected test suite. The most important remaining gap is **F1**: the `accessToken` source scan only forbids `accessToken(` and misses `accessToken.current()`, `.adopt()` and `.renew()`. Because the screen must not read or assert session state, any method on the `LocalSession` object is a reach that should be forbidden. **F2** is a smaller precision issue with quote-agnostic imports. **F5** is the lingering naming concern from Phase 3/8. The suite and gates are independently reproducible, and the 29 mutants are all killed.

**Phase 11 complete.**
