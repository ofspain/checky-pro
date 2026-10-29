# frontend · T27 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. T27 is verification-only and unusual: the value is absence, and the test must prove the SPA never acquires a second factor. The brief is strong on symmetry and structural absence, but the source-scan target list and the hand-over instrumentation have edges a plausible future addition could exploit.

---

## F1 · The hand-over test is keyed on `location.assign` only

**Severity.** Medium.

**Evidence.**
- AC1 requires "one full-page navigation to the authorize URL".
- Required Test 1 says "one `location.assign` with the authorize URL".
- It does not mention `window.location.href = url`, `window.location.replace(url)`, `window.open(url, '_self')`, or a form `submit` to the authorize URL.

**Why it matters.** A future change that uses `window.location.href = authorizeUrl` would still hand over full-page, but the test's spy on `location.assign` would see nothing and the case would fail or, worse, pass vacuously. The architectural claim is about full-page navigation, not about a particular method.

**Recommended brief amendment.** Require the hand-over case to spy on the `location` object itself — or at least on both `assign` and `replace` and the `href` setter — and assert exactly one navigation to the authorize URL, regardless of mechanism. Include a control that a spy installed after the click reports nothing.

**Confidence.** Medium — a real fragility in the instrument.

---

## F2 · The structural absence scan does not include MFA-related package imports

**Severity.** Medium.

**Evidence.**
- AC2/AC3 require no credential input and no second-factor logic.
- Required Test 2 scans for `type="password"`, `autoComplete` one-time-code values, and words for one-time/recovery codes.
- It does not scan for imports of packages such as `otpauth`, `speakeasy`, `totp-generator`, `qrcode`, or `@simplewebauthn/browser`.

**Why it matters.** A module could ship TOTP generation or verification logic purely in code, without rendering an input, and the scan would not see it. An import is the strongest signal that such logic has arrived.

**Recommended brief amendment.** Add a forbidden-import list to the structural scan, with a synthetic file importing one of them as a control. The permitted hits remain `account/mfa` route path and PKCE `code_challenge`.

**Confidence.** Medium — a direct guard against AC3 violations.

---

## F3 · The structural absence scan does not include MFA-related identifiers

**Severity.** Medium.

**Evidence.**
- Required Test 2 scans for input attributes and "the words for a one-time or recovery code".
- It does not explicitly scan for function, class, variable or file names containing `totp`, `mfa`, `2fa`, `otp`, `authenticator`, `recovery`, `challenge`, `verifyMfa`, etc.

**Why it matters.** A component named `TotpChallenge` or a function `verifyRecoveryCode` is exactly the kind of plausible-looking addition the Purpose section warns against. The scan should flag identifiers, not just rendered inputs.

**Recommended brief amendment.** Add identifier patterns to the structural scan, with an allowlist for the PKCE `code_challenge` and `account/mfa` route path. Include a synthetic file carrying `function verifyTotp()` as a control.

**Confidence.** Medium — completes the absence claim.

---

## F4 · The symmetry fixtures should include an MFA-specific claim in the token response

**Severity.** Medium.

**Evidence.**
- Required Test 3 pairs fixtures that differ in `amr`, `acr` and `auth_time`.
- It does not include a fixture where the token response carries an explicit MFA marker such as `mfa_method: "totp"` or `amr: ["pwd","otp"]` with an additional `acr_values` claim.

**Why it matters.** The SPA cannot observe SAS's internal steps, but it can observe claims the server returns. An explicit MFA marker is the most likely future addition a backend might make, and the SPA must remain indifferent.

**Recommended brief amendment.** Add a third fixture to the symmetry set that includes an explicit MFA-related claim in the ID token (e.g., `amr: ["pwd","otp"]` and a custom `mfa_method` claim). Assert the SPA outcome is identical to the non-MFA fixture.

**Confidence.** Medium — strengthens AC4.

---

## F5 · AC7 does not sweep the token response or ID-token claims for MFA-shaped values

**Severity.** Medium.

**Evidence.**
- AC7 says "no MFA-shaped value passes through the SPA: not in a request body or URL, the router location, or either store".
- It omits the response body from the exchange and the decoded ID-token claims.

**Why it matters.** The exchange response is where `amr`/`acr`/MFA markers live. If the callback screen decoded the ID token and stored or logged an MFA claim, that would be an MFA-shaped value passing through the SPA. The request/URL/store sweep would miss it.

**Recommended brief amendment.** Extend AC7 to include "response bodies and decoded ID-token claims". Add a test that drives the callback with an MFA-marked fixture and sweeps the exchange response body, the ID token payload, and any surface the SPA touches for MFA-shaped values.

**Confidence.** Medium — closes the response path.

---

## F6 · The authorize parameter set should be derived, not asserted as eight literals

**Severity.** Low.

**Evidence.**
- AC10 says "exactly the eight T15 owns, asserted as a set".
- Required Test 8 says "exactly the eight T15 owns, asserted as a set, with no MFA parameter".
- The brief does not say the eight names are imported from the module that owns them.

**Why it matters.** A literal list of eight parameter names in the test file would keep passing after T15 renames one or adds a legitimate parameter. The test should derive the expected set from T15's exports or from the actual request the authorize builder produces under a controlled fixture.

**Recommended brief amendment.** Require the test to import or derive the expected parameter set from `authorizeRequest.ts` (e.g., by calling `buildAuthorizeRequest` and reading its URL search params), then assert the callback-driven request matches it. Include a control that a synthetic `acr_values=mfa:` parameter fails.

**Confidence.** Low — maintainability and spec alignment.

---

## F7 · AC9 does not name the focus target

**Severity.** Low.

**Evidence.**
- AC9 says "focus is managed at the point of resumption and the status is announced, not only rendered".
- It does not say which element receives focus: the status live region, the heading, the main container, or the route's first focusable element.

**Why it matters.** "Managed focus" is only testable if the target is specified. A test that merely asserts focus moved somewhere would pass against many implementations, some of which might strand focus at the body.

**Recommended brief amendment.** Specify the focus target in the brief: e.g., the status live region or the route's primary heading. The test then asserts `document.activeElement` is that element.

**Confidence.** Low — clarity for implementation.

---

## F8 · The refusal case should also check the sign-in notice value

**Severity.** Low.

**Evidence.**
- AC6 requires "uniform notice, the sign-in start, and no copy anywhere mentioning enrollment, MFA, authenticator or a code".
- Required Test 5 covers the destination and rendered text.

**Why it matters.** The sign-in notice is a separate surface from rendered text. A future change could put MFA-specific copy in the notice while keeping the screen text uniform. The test should assert the notice value is the same uniform message used for a wrong-password discard.

**Recommended brief amendment.** Add an assertion that `takeSignInNotice()` after the refusal equals the notice set by a wrong-password discard, and that neither contains MFA-related words.

**Confidence.** Low — completeness.

---

## F9 · No required test covers a second click while a navigation is already in flight

**Severity.** Low.

**Evidence.**
- Required Test 1 says "the second click suppressed by the `inFlight` ref".
- It does not specify how the test proves suppression: a second `location.assign` call, a second fetch, or something else.

**Why it matters.** The suppression mechanism matters. A buggy implementation might start a second authorize request (writing a second set of PKCE items) without navigating. The test should spy on both `location.assign` and the authorize-builder/storage writes and assert no second set is produced.

**Recommended brief amendment.** Require the second-click case to assert both that no second navigation occurs and that the store does not grow beyond the three PKCE items (or four, if a target is remembered).

**Confidence.** Low — clarity.

---

## Summary

F1–F5 are the important findings: they close gaps in the hand-over instrument, the structural absence scan, the symmetry fixtures, and the response sweep. F6–F9 are smaller clarity and completeness items. No production change is anticipated; all recommendations are additions to the test plan.
