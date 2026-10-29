# frontend · T28 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. T28 is verification-only and its value is silence: the SPA must not know about enrollment, must not gate on role or account state, and must treat every failure the same. The brief is tight, but the error-response surface and the structural scan have edges a future breach could use.

---

## F1 · No required test covers an `error=` callback with a missing or mismatched `state`

**Severity.** Medium.

**Evidence.**
- Required Test 1 covers `/app/callback?error=access_denied&state=…`.
- Required Test 2 compares the `error=` arrival to mismatched `state`, refused exchange and bad `nonce`.
- There is no row for `error=` without `state`, or with a `state` that does not match the stored one.

**Why it matters.** SAS might return an error without a `state` parameter, or with a `state` the tab did not store. The SPA must still route to the single discard closure. T17 owns missing-state handling, but T28 needs to assert that the enrollment-related error path behaves identically in those cases too.

**Recommended brief amendment.** Add two rows to the `error=` table: one with no `state` parameter and one with a mismatched `state`, both asserting the same discard outcome.

**Confidence.** Medium — a realistic OIDC error-response shape.

---

## F2 · The named-causes table omits `error_description` and `error_uri` that mention enrollment

**Severity.** Medium.

**Evidence.**
- AC3 lists error codes including a vendor code spelling enrollment.
- AC4 says the error code and description never surface.
- Required Test 3 is a table of error codes; it does not include an `error_description` such as "Two-factor authentication required" or an `error_uri` pointing to an enrollment page.

**Why it matters.** A backend might return a generic code like `access_denied` with a description that discloses enrollment. The SPA must not render, store, log or forward the description either. The outcome must be identical and the leak sweep must catch the description text.

**Recommended brief amendment.** Extend Required Test 3 to include rows with `error_description` and `error_uri` values that mention enrollment/MFA, and assert both that the outcome is unchanged and that the description/URI text does not appear on any swept surface.

**Confidence.** Medium — OIDC standard fields.

---

## F3 · The structural silence scan does not look for server-error message strings

**Severity.** Medium.

**Evidence.**
- AC5 scans for enrollment vocabulary: `enroll`, `enrolment`, `set up two-factor`, `register your authenticator`, `activate MFA`, and the T27 synonym set.
- It does not scan for strings a backend might return that would leak the same information if hard-coded in the SPA, such as "Bad credentials", "Two-factor authentication required", "MFA setup incomplete", or "please enroll".

**Why it matters.** The failure mode this task guards against is a developer adding a helpful sentence like "Your account needs two-factor authentication set up before you can sign in." That sentence may not contain the literal word "enroll" but contains enrollment intent. The scan should catch it.

**Recommended brief amendment.** Add a second pattern group for server-shaped error messages, including "two-factor", "MFA", "authenticator", "recovery code", "set up", "register your", "activate", "required", "incomplete", and "please" near any of the above. Include a synthetic control with `"Two-factor authentication is required"`.

**Confidence.** Medium — closes the natural-language hole.

---

## F4 · The outcome struct in AC2 does not include `historyAction`

**Severity.** Low.

**Evidence.**
- AC1 says the error callback ends with "a replaced history entry".
- AC2 lists the outcome struct fields as destination, rendered text, notice, stored keys, live-region count.
- `historyAction` is not in the list.

**Why it matters.** A mutant that pushes instead of replacing would leave the callback reachable with the back button, breaking R8's discard semantics. The struct comparison should catch it.

**Recommended brief amendment.** Add `historyAction` to the `Outcome` struct and assert it is `'REPLACE'` for all four failure kinds.

**Confidence.** Low — symmetry strengthener.

---

## F5 · The source scan does not inspect file or directory names for enrollment

**Severity.** Low.

**Evidence.**
- AC5 scans comment-stripped source text.
- It does not scan file paths or directory names.

**Why it matters.** A future module could be named `EnrollMfaScreen.tsx` or live under `src/auth/enrollment/` while containing no enrollment vocabulary in its code. The text scan would miss it. The import/dependency scan would catch an import of it, but only if something imports it.

**Recommended brief amendment.** Add a path scan over `src/**` for enrollment-shaped file and directory names, with a synthetic file path as a control. Alternatively, extend the source scan to include the file path above each file's code.

**Confidence.** Low — coverage completion.

---

## F6 · The role-gate control should include an account-state gate

**Severity.** Low.

**Evidence.**
- AC6 forbids a branch on a role or account state in the sign-in and callback path.
- Required Test 6 mentions a synthetic `if (role === 'MERCHANT')`.

**Why it matters.** A gate on `account.mfaEnrolled`, `user.requiresMfa`, or `account.state` would break the same rule as a role gate but would not be caught by the role-only control.

**Recommended brief amendment.** Add a synthetic account-state gate control, e.g., `if (account.mfaEnrolled === false)`, and scan for patterns like `\.mfaEnrolled`, `requiresMfa`, `accountState`, `state === 'PENDING_ENROLLMENT'`.

**Confidence.** Low — completeness.

---

## F7 · No mechanism is specified for enumerating failure sentences

**Severity.** Low.

**Evidence.**
- AC8 says the SPA renders one failure sentence for a failed sign-in, and `signIn.failed` is distinct from `signIn.discarded`.
- Required Test 8 says "one sentence per outcome".
- It does not say how the test enumerates failure sentences (e.g., all catalogue keys under `signIn.*`).

**Why it matters.** Without a specified enumeration, a future addition of a third failure sentence could be missed. The test should derive the list from the catalogue and assert exactly two keys are used for failed sign-in outcomes.

**Recommended brief amendment.** Require the test to collect every catalogue key whose value appears on a failed sign-in screen, and assert the set equals `{ 'signIn.failed', 'signIn.discarded' }` (or whatever keys the owning module exports). Add a control that a synthetic `signIn.mfaRequired` key fails.

**Confidence.** Low — clarity.

---

## F8 · The mutation harness should include a description-leak mutant

**Severity.** Low.

**Evidence.**
- The Constraints section lists mutants: surfacing the error code; a refusal-specific sentence; a role gate; an enrollment string; an error-code branch for an unused code.
- It does not list a mutant that renders or logs `error_description`.

**Why it matters.** The description is the most likely place for enrollment text to leak, because the code itself is often generic. A mutant that copies `error_description` into the discard screen would breach R10 without changing the code table.

**Recommended brief amendment.** Add to the mutation list: a mutant that writes `error_description` to the document, a store, the console or the notice.

**Confidence.** Low — completeness.

---

## Summary

F1–F3 are the most important because they close gaps in the error-response handling and the structural silence scan. F4–F8 are smaller completeness and clarity items. No production change is anticipated; all recommendations are additions to the test plan.
