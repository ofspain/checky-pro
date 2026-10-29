# frontend · T27 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 manifest and the updated `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`. Every Phase 3 finding is implemented and justified: the hand-over counter watches four mechanisms, the source scan covers identifiers and forbidden imports, the symmetry fixtures include an explicit MFA marker, the response and decoded-claim sweeps are present, and the parameter set is asserted as eight literals with a clear rationale. The mutation score is 22/22 killed by T27's own file. The gaps below are minor future-proofing and completeness items.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## Gap 1 · The forbidden-import scan does not catch `require()` or dynamic `import()`

**Why it matters.**
- `importedSpecifiers` matches `import ... from 'x'` and `import('x')` forms.
- It does not match CommonJS `require('otpauth')`.
- No production file uses `require` today, and ESM is the project's style, but a guard whose job is structural absence should not assume the module system.

**Suggested test.**
Add a control case: a synthetic file containing `const otp = require('otpauth');` should be flagged by the import scan. If the project forbids `require` elsewhere, that can be a separate scan; either way, this gap should be documented or closed.

---

## Gap 2 · The MFA identifier list omits a few common synonyms

**Why it matters.**
- `MFA_IDENTIFIER_SOURCE` covers `mfa`, `totp`, `recovery`, `authenticator`, `one-time`, `otp`, `2fa`, `webauthn`, `passkey`.
- It does not cover `security key`, `backup code`, `verification code`, `TFA`, `multi-factor`, or `FIDO`.
- The list is explicitly enumerative, so this is a coverage choice, not a defect — but `security key` and `backup code` are common enough that a future screen could use them without matching any current pattern.

**Suggested test.**
Add the synonyms to the identifier source, or document in a comment why they are excluded. Include one synthetic control with `security key` to prove the expanded list fires.

---

## Gap 3 · `productionSources()` does not exclude Storybook, spec or mock files

**Why it matters.**
- The walker includes every `.ts/.tsx` under `src/` except `.test.tsx?`.
- If `.stories.tsx`, `.spec.tsx`, `.mock.ts` or `__mocks__/` files are added later, they will be scanned as production source and could fail the credential or MFA scans even though they are not shipped code.

**Suggested test.**
Narrow the walker to exclude `*.stories.tsx`, `*.stories.ts`, `*.spec.tsx`, `*.spec.ts`, `*.mock.ts`, `*.mock.tsx`, and files under `__mocks__`. Add a control that proves a synthetic story file is excluded.

---

## Gap 4 · The catalogue scan only covers the English catalogue

**Why it matters.**
- `catalogueStrings()` flattens `en` only.
- The project currently has one catalogue file (`catalogue.ts`) exporting `en`, so this is not a current defect.
- If a second locale is added, a translated MFA string would not be caught unless the scan is updated.

**Suggested test.**
No change needed now, but add a comment in `catalogueStrings()` stating that new locales must be included in the scan. When a second locale lands, refactor the function to iterate every exported locale.

---

## Gap 5 · The credential source scan flags any `<form>` element

**Why it matters.**
- `CREDENTIAL_SOURCE_PATTERNS` includes `/<\s*form\b/gi`.
- A future non-credential form — e.g., a search form or preferences form — would fail the scan.
- The brief's intent is to forbid credential-collecting surfaces, and a form is one, but the broad pattern could create friction.

**Suggested test.**
No test change. Document in the pattern's `what` field or in a comment that any production form requires an explicit allowlist entry, and that the broad pattern is intentional because L2 places all credential collection on the server.

---

## Gap 6 · The AC11 instrument table is documentation, not measurement

**Why it matters.**
- The self-review S4 already identifies this and argues it should stay as a signpost.
- The table cannot fail for the right reason; it only fails if a row is deleted without updating the count.
- A future maintainer might mistake the table for evidence.

**Suggested test.**
No code change. Add a comment above the case stating explicitly that the table is a signpost and that the real proof is in the eleven control cases it names. This reinforces the self-review's intent.

---

## Gap 7 · No test verifies the sign-in button is keyboard-activatable before the hand-over

**Why it matters.**
- AC9's focus case asserts focus stays on the button after click.
- It does not assert that the button is focusable or that pressing Enter/Space activates it.
- A disabled button (as opposed to `aria-disabled`) would not be activatable and would strand focus.

**Suggested test.**
Add one case: `button` is focusable (`button.tabIndex` is not negative and `button.disabled` is false before click), and a `keydown` event for Enter or Space triggers the hand-over. This is small but would catch a regression from `aria-disabled` back to `disabled`.

---

## Summary

Gaps 1–3 are minor structural completeness items. Gap 4 is future-proofing. Gap 5 and Gap 6 are documentation. Gap 7 is a small accessibility reinforcement. None of them weaken the file's core claim; the file is already a strong instrument for proving the SPA contains no second factor.
