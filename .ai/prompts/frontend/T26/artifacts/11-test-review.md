# frontend · T26 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 manifest and the updated `sessionStorageHoldsOnlyTheL16Items.test.tsx`. The accepted findings are all implemented: the third namespace instrument is in place, `localStorage` is narrowed to the rule `design.md` actually permits, the repetition cases unmount properly, the discard rows assert exact key sets, and the mutation harness is committed. The remaining gaps are minor: the secret sweep should include localStorage, and the namespace regex could be tightened against URL false-positives.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## Gap 1 · The secret-value sweep (AC5) is never applied to localStorage

**Why it matters.**
- AC5 says no token, token segment, decoded claim, password, recovery code or API key may appear as "any stored value or inside any stored key". That wording covers localStorage as well as sessionStorage.
- The file calls `expectNoSecretIn(valuesIn(sweepSession()), …)` and `expectNoSecretIn(keysIn(sweepSession()), …)` but never calls it on `sweepLocal()`.
- The behavioural cases assert `keysIn(sweepLocal()).toEqual([])`, which does catch any localStorage write during a tested flow. But if a regression ever puts a token in localStorage, the failure would be "expected [] to equal [ 'debug' ]" rather than "a value R29 forbids was found in a stored value". The latter is what AC5 promises and is the failure a security reviewer wants to read.

**Suggested test.**
Add one line to the value-sweep block: after asserting `keysIn(sweepLocal())` is empty, also run `expectNoSecretIn(valuesIn(sweepLocal()), secretsOf(body), 'in localStorage')` in the same cases that sweep sessionStorage. Because the store is empty today the assertion is trivially true, but the instrument is then in place for the day it is not.

---

## Gap 2 · The namespace regex can false-positive on a URL hostname

**Why it matters.**
- `namespaceMatches` uses `(?<!@)\b${namespaceRoot()}(?:[.\w-]+)?`.
- The negative lookbehind excludes only `@` (npm package scope). It does not exclude `://`, so a URL such as `https://themistra.example.com/api` would match `themistra.example.com` and be flagged as a namespace stranger.
- No production URL like this exists today, but the regex is the kind of instrument that should be proved against its own false-positive controls.

**Suggested test.**
Add a control case: a synthetic file containing `const url = 'https://themistra.example.com/api';` should produce zero namespace matches, alongside the existing `@themistra/api-client` control.

---

## Gap 3 · No control covers a namespace literal inside a template literal

**Why it matters.**
- The namespace scan is text-based. A namespace name inside a template literal — `` const key = `${namespaceRoot()}.auth.evil`; `` — uses `namespaceRoot()` rather than the literal `themistra`, but a hard-coded template like `` const key = `themistra.auth.evil`; `` could exist.
- The current controls cover string literals and the MA22 computed-method case. A template-literal control would document whether the regex handles backticks correctly.

**Suggested test.**
Add a synthetic control: `` const k = `themistra.auth.evil`; `` should be flagged, and `` const k = `${namespaceRoot()}.auth.state`; `` should be permitted because it resolves to an inventory key (or at least should not be flagged as a stranger if the test evaluates it).

---

## Gap 4 · `productionSources()` does not exclude Storybook or mock files

**Why it matters.**
- `productionSources()` walks `src/` and includes every `.ts/.tsx` file except `.test.tsx?`.
- If `.stories.tsx` or `.mock.ts` files are added later, they will be scanned as production source. A story that demonstrates a storage write, or a mock that names `indexedDB`, could fail the scan even though it is not production code.

**Suggested test.**
Narrow the walker to exclude `*.stories.tsx`, `*.stories.ts`, `*.mock.ts`, `*.mock.tsx`, or any file under a `__mocks__` directory. Add a control that creates a synthetic tree containing such a file and asserts the scan excludes it.

---

## Gap 5 · The localStorage narrowing case does not inspect values for secrets

**Why it matters.**
- The structural case (lines 1003–1015) checks that files mentioning `localStorage` do not contain namespace names. It does not inspect the values being written.
- A file could write a token to `localStorage.setItem('debug', ACCESS_TOKEN)` and pass both the namespace scan and the behavioural empty-store assertions (if the write happens on an unexercised path).

**Suggested test.**
This is the structural counterpart to Gap 1. Add a synthetic offender: `globalThis.localStorage.setItem('debug', ACCESS_TOKEN);`. The test should assert that this file is flagged — but only if the scan can reasonably detect it. A simpler, more robust fix is Gap 1: sweep localStorage values for secrets after every flow, which catches the value regardless of key or code path.

---

## Gap 6 · The mutation harness is committed, but its output format is not self-describing

**Why it matters.**
- The harness path and run command are recorded in the Phase 10 artifact.
- A future maintainer running the harness will see pass/fail counts but no explanation of what each mutant family means unless they read the artifact.

**Suggested test.**
No test change. Add a header comment inside `07-mutation-harness.py` that explains the families and the expected baseline, so the script is readable on its own.

---

## Summary

Gap 1 is the most important because it brings the localStorage value sweep into line with AC5's wording. Gap 2 is a regex-hygiene control. Gap 3 is a template-literal completeness item. Gap 4 is future-proofing. Gap 5 is the structural side of Gap 1 and may be impractical to implement statically. Gap 6 is documentation. The file is already very strong; these are polish and alignment items.
