# frontend · T28 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 manifest and the updated `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`. Every accepted finding is implemented and justified: the document surface reaches title and head, `window.name` is swept, the copy pattern is split into global and sign-in-scoped constants, the key grammar is written out, the exchange count is asserted per-kind outside the struct, and the harness is documented. The mutation score is 23/23 killed. The gaps below are minor: one missing synonym in the enrollment pattern, and an incomplete cleanup between arrivals that could cross-contaminate the new global surfaces.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## Gap 1 · `multi-factor` is not caught by the enrollment source pattern

**Why it matters.**
- `ENROLLMENT_SOURCE` matches `two[-_ ]?factor`, `second[-_ ]?factor` and other phrasings.
- It does not match `multi-factor`, `multifactor` or `multi factor`.
- A future module or catalogue entry describing MFA as "multi-factor authentication" would pass the source scan and the value scan (which builds on `ENROLLMENT_SOURCE`).

**Suggested test.**
Add `multi[-_ ]?factor|multifactor` to `ENROLLMENT_SOURCE`, and include a control in the AC5 "CONTROL: the scan flags each enrollment phrasing on its own" case. Also include a control that proves the broadened pattern still does not flag the permitted form-validation copy (e.g., "Please enter an amount").

---

## Gap 2 · `measure()` does not reset the global surfaces it now sweeps

**Why it matters.**
- Phase 10 added `document.title` and `window.name` to the leak sweep, and reset them in `afterEach`.
- The shared `measure()` helper and the exchange-count inline cleanup clear `document.body.innerHTML`, `sessionStorage`, `localStorage`, the access token and the sign-in notice, but they do **not** reset `document.title` or `window.name`.
- A test that calls `measure()` twice (e.g., AC2's four-way comparison) could carry a title or `window.name` value from the first arrival into the second. If production ever sets them differently per failure kind, the comparison is either contaminated or silently masked.

**Suggested test.**
Reset `document.title = ''` and `window.name = ''` inside `measure()` and inside the exchange-count cleanup block, mirroring `afterEach`. Add a control that plants a title/window.name, calls `measure()` for one failure kind, and asserts the next `measure()` starts with empty global surfaces.

---

## Gap 3 · The exchange-count case does not cover the no-state and mismatched-state error responses

**Why it matters.**
- AC2 tests three state shapes for `error=` callbacks: no state, mismatched state, and matching state.
- The exchange-count case only covers the four `FAILURE_KINDS`, where `error-response` uses the matching-state query.
- All three state shapes should exchange nothing (no `code` is present), but the case only proves it for one of them.

**Suggested test.**
Extend the exchange-count table to include the two additional `error=` state shapes from AC2, asserting each has `exchanges: 0`.

---

## Gap 4 · The gating scan may miss array-method and alternative-property role gates

**Why it matters.**
- `ACCOUNT_STATE_SOURCE` catches direct comparisons like `account.role === 'MERCHANT'` and `roles.includes` is not in the pattern.
- A future gate such as `if (roles.includes('MERCHANT'))` or `if (account.type === 'MERCHANT')` would not be flagged.

**Suggested test.**
Add two controls: one with `roles.includes('MERCHANT')` and one with `account.type === 'MERCHANT'`, and either extend the pattern to catch them or document that the scan is enumerative and relies on the structural naming convention. Given the file's design of keying on names rather than syntax, adding `\.type\s*===|\.includes\s*\(\s*['"](?:MERCHANT|ADMIN)` may be appropriate.

---

## Gap 5 · The `Outcome` struct does not explicitly assert empty `search` and `hash`

**Why it matters.**
- The struct includes `search` and `hash`, so the four-way comparison would fail if any failure kind left query parameters.
- But if all four failure kinds left query parameters in the same way, the comparison would still pass.
- A discard that fails to strip the `error`, `state`, `code` or `error_description` parameters would leave enrollment-sensitive values in the URL.

**Suggested test.**
Add an explicit assertion in the AC2 equivalence case or in AC1: `expect(outcome.search).toBe('')` and `expect(outcome.hash).toBe('')` for at least the `error-response` failure kind.

---

## Gap 6 · The catalogue scan does not handle arrays of strings

**Why it matters.**
- `catalogueStrings()` recursively visits object entries and string values.
- If the catalogue ever contains an array of strings (e.g., a list of help bullets), those strings would be skipped.

**Suggested test.**
No change needed today, but add a comment in `catalogueStrings()` stating that arrays are not flattened and that any future array-valued catalogue entries must be handled. Alternatively, extend `visit` to handle arrays now.

---

## Gap 7 · The mutation harness is documented in the header but not inside the file

**Why it matters.**
- The header now names the harness path and run command.
- A reader jumping to the AC11 block or the mutations table would not see the command without scrolling to the top.

**Suggested test.**
No test change. Add a one-line comment near the AC11 block or the mutation-related cases repeating the harness path and the 23/23 result.

---

## Summary

Gap 1 and Gap 2 are the most important: one closes a vocabulary hole in the enrollment scan, and the other prevents cross-test contamination of the global surfaces. Gap 3 is a small completeness item for the exchange count. Gap 4 is a coverage strengthener for the gating scan. Gap 5 makes the URL-cleanliness claim explicit. Gaps 6 and 7 are documentation. The file is already very strong; these changes are polish.
