<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T45 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — `PasswordField` must accept a description id, and the prop should be `aria-describedby`
· **Severity:** **Blocker/Major**
· **Evidence:** `frontend/src/app/ui/PasswordField.tsx:42-56` currently sets `aria-invalid` but has no prop to point at an external message. `frontend/src/app/ui/Text.tsx:11` already accepts `id` for exactly this association pattern. D-T45-3 requires a DOM-measurable id the field points at.
· **Assessment:** The field owns its own input `id` via `useId()`, and it would be wrong to leak that id or force the screen to coordinate ids. But the association direction is the opposite: the screen creates the message, gives it an `id`, and the field must reference it. This is a relationship the screen owns, not a contract attribute of the control, so passing it as a prop does not violate rule 2. The precedent is `Text`'s `id` prop. The clearest name is `aria-describedby` (the actual ARIA attribute), not `descriptionId`, because it signals exactly what the DOM will contain and avoids inventing a layer-specific synonym.
· **Recommended brief amendment:** Add to `PasswordField`'s prop type: `'aria-describedby'?: string`. The field renders it on the `<input>` unchanged. The screen renders `<Text id="register-password-guidance">{t('register.passwordGuidance')}</Text>` and passes `aria-describedby="register-password-guidance"` to `PasswordField` when `advisory === 'out-of-range'`.

### F2 — The guidance string must name the bounds using the constants, not two literals
· **Severity:** **Major/Blocker**
· **Evidence:** D-T45-2 decides on one string naming the rule, and that the string must name the bounds by the constants so the two ends cannot drift.
· **Assessment:** The catalogue is plain strings, so interpolation must happen in the screen: `t('register.passwordGuidance', { min: ADVISORY_MINIMUM_CODE_POINTS, max: ADVISORY_MAXIMUM_CODE_POINTS })`. This requires i18next interpolation support (already used). The catalogue entry becomes a template like `'Password must be {{min}} to {{max}} Unicode code points.'`. The test asserts the rendered text contains the numbers and that they equal the constants, preventing drift between `passwordLength.ts` and the catalogue.
· **Recommended brief amendment:** Specify the catalogue key `register.passwordGuidance` with interpolation variables `min` and `max`, and require the test to assert the rendered numbers match `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS`.

### F3 — The guidance is a field description, not a live-region announcement
· **Severity:** **Major**
· **Evidence:** D-T45-1 decides the guidance is described on the field, not announced in `StatusRegion`, because the live region is immediately overwritten by "Creating your account…".
· **Assessment:** This is the correct decision under WCAG 3.3.1 and R1. The residual risk — a screen-reader user who does not return to the field may not hear it — is accepted and documented as a consequence of T43's OQ-T43-10 (no focus move on submit). The implementation must not move focus to the guidance, because that is explicitly out of scope.
· **Recommended brief amendment:** Confirm: render the guidance as a persistent `Text` element associated with the password field via `aria-describedby`; do not use `StatusRegion` or move focus.

### F4 — AC3 requires a control proving the guidance query can find it when present
· **Severity:** **Major**
· **Evidence:** AC3 says a password inside the range shows no guidance, "with a control proving the query can find it when it is there".
· **Assessment:** The simplest control is to query for the guidance element by its stable id in both states: expect it to exist after an out-of-range submit and not exist after an in-range submit. The "control" is the same query used in both directions, rather than two different selectors. Using a stable id (`register-password-guidance`) makes this honest.
· **Recommended brief amendment:** Use a stable id on the guidance `Text` and query by that id in the suite. Add a control case that asserts the id attribute is present when the guidance renders and absent when it does not.

### F5 — The guidance must not appear before the first submit
· **Severity:** **Major**
· **Evidence:** AC5 requires nothing is shown before the first submit, however long or short the typing.
· **Assessment:** The `advisory` state starts as `'unchecked'` and only changes to `'out-of-range'` on submit. The screen should render the guidance only when `advisory === 'out-of-range'`. A case that types 3 characters (without submitting) and asserts no guidance element exists will hold this.
· **Recommended brief amendment:** Add an AC5 case: type into the password field without submitting and assert the guidance element is not in the document.

### F6 — The request must still be sent when the guidance is showing
· **Severity:** **Major**
· **Evidence:** AC6 requires the request is still sent and nothing is disabled while the guidance is up.
· **Assessment:** This is already the behavior in T43/T44, but the new named suite should assert it independently for 11 and 129 code points. The table-driven case can assert `sent` has length 1 and the submit control is not disabled.
· **Recommended brief amendment:** Include request-still-sent assertions in the out-of-range rows of the new named suite.

### F7 — The new named suite should reuse T44's helpers but not duplicate its claims
· **Severity:** **Major**
· **Evidence:** The brief creates a new file `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx`.
· **Assessment:** The suite should focus on the guidance: when it appears, when it does not, its association, its source, and that it does not block submission. It should not re-prove the entire T44 table, but it should cover 11, 129, and an astral out-of-range password to ensure the guidance works at both ends and with astral characters. Reusing `renderScreen`, `fillAndSubmit`, `installTransport`, etc., from T44's suite (or a shared helper) is appropriate, but those helpers are currently private to T44's test file.
· **Recommended brief amendment:** Decide whether to extract shared helpers into a `features/account/test-helpers.ts` file or to duplicate the small helpers. Extraction respects DRY but touches more files; duplication respects task boundaries. Recommendation: extract only if the helpers are large; otherwise duplicate the ~20 lines needed.

### F8 — T43's AC6 reachable-keys case must include the new key
· **Severity:** **Major**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:402-405` asserts a fixed set of reachable keys. The brief lists this file under "Files to Modify".
· **Assessment:** Adding `register.passwordGuidance` to the list is a one-line change. Failure to do so would make the new key unreachable according to the suite that owns copy reachability.
· **Recommended brief amendment:** Add `'register.passwordGuidance'` to the reachable-keys list in T43's AC6 case.

### F9 — The design-system suite needs no new primitive but may need a prop-type pin
· **Severity:** **Minor/Major**
· **Evidence:** `PasswordField` will gain a new prop; `Text` already supports `id`. `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` pins primitive prop types to have no `className` or `style`.
· **Assessment:** No new primitive is added, so `PRIMITIVES` and `RENDER_SITES` stay the same size. `PasswordField` still renders exactly two elements. The new prop is a relationship attribute, not a style prop, so the existing prop-type pins are satisfied. The suite may want to assert that `PasswordField`'s prop type includes `aria-describedby` if the task wants to pin the mechanism, but that is optional.
· **Recommended brief amendment:** None required for the design-system suite unless the task chooses to pin the new prop explicitly.

### F10 — Mutation harness rows for the guidance path
· **Severity:** **Major**
· **Evidence:** The brief requires a harness with rows: guidance suppressed, always shown, shown while typing, association dropped, copy hardcoded, bound named wrongly.
· **Assessment:** Suggested mutants:
  - **Suppress:** delete the `advisory === 'out-of-range'` guard so guidance never renders → killed by AC1/AC2.
  - **Always show:** render guidance unconditionally (even in-range) → killed by AC3.
  - **Shown while typing:** render guidance when `advisory !== 'unchecked'` (i.e., on any typing) → killed by AC5.
  - **Association dropped:** omit `aria-describedby` from `PasswordField` → killed by AC4.
  - **Copy hardcoded:** spell the sentence in `RegisterScreen.tsx` instead of `t()` → killed by AC7 source scan.
  - **Bound named wrongly:** hardcode "12 to 999" or "1 to 128" in the catalogue instead of interpolating constants → killed by AC7's constant-equality assertion.
· **Recommended brief amendment:** List these six mutants and the cases that kill them.

### F11 — Empty string must show the same guidance as 11
· **Severity:** **Minor/Major**
· **Evidence:** The brief's constraints say the empty string is out of range and must produce the same guidance.
· **Assessment:** Because the guidance names the rule ("12 to 128"), the empty string naturally gets the same message as 11 and 129. A dedicated row is not strictly required if 11 and 129 are covered, but adding it closes the null-handling constraint explicitly.
· **Recommended brief amendment:** Optional: add an empty-string row to the new suite's out-of-range cases.

### F12 — The guidance must describe the rule, not diagnose the input
· **Severity:** **Minor**
· **Evidence:** D-T45-2 says guidance that names the rule reads as help; a message that diagnoses the input ("too short") reads as a verdict, which an advisory check has no standing to deliver.
· **Assessment:** The catalogue string should be something like "Password must be 12 to 128 Unicode code points." It should not say "too short", "too long", "Password is invalid", or anything that sounds like a verdict. The test can assert the rendered text does not contain words like "short", "long", "invalid", or "wrong".
· **Recommended brief amendment:** Include in AC7 a negative assertion that the guidance text does not contain diagnostic words.

---

## Summary

T45's central design challenge is how to make `PasswordField` point at a screen-owned guidance message without violating the design-system rules. The answer is to give `PasswordField` an `aria-describedby` prop (**F1**), use `Text`'s existing `id` prop for the message, and interpolate the bounds from `passwordLength.ts` into a single catalogue string (**F2**). The guidance lives as a field description, not a live-region announcement (**F3**), and appears only after submit only when out of range (**F5**). The new named suite focuses on guidance behavior, reuses T44's helpers where sensible (**F7**), updates T43's reachable-keys case (**F8**), and includes a mutation harness targeting the guidance path (**F10**). No new primitive is needed and the design-system instrument sizes stay fixed (**F9**). The empty string gets the same rule-named guidance as other out-of-range values (**F11**), and the copy avoids diagnostic language (**F12**).

**Phase 3 complete.** Twelve findings returned; no code changed.
