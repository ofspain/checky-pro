<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No code. Plans against the FROZEN brief only. -->

# frontend · T45 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**FROZEN**) |
| **Shape** | one catalogue key, one primitive prop, one conditional element in a screen, one line in a sibling suite, one new named suite, one harness |

## Files to create

| | |
|---|---|
| `src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | the named suite (`package.md` §8) |

## Files to modify

| file | change |
|---|---|
| `src/i18n/catalogue.ts` | `register.passwordGuidance`, the package's **first interpolated string**, with `{{min}}` and `{{max}}`; the group's comment stops reserving the copy for a future task and starts explaining the template |
| `src/app/ui/PasswordField.tsx` | one prop: `'aria-describedby'?: string`, rendered on the `<input>` |
| `src/features/account/RegisterScreen.tsx` | render `<Text id={GUIDANCE_ID}>` when `advisory === 'out-of-range'`, and pass `aria-describedby={GUIDANCE_ID}` to the field in the same condition |
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **one line** — `'register.passwordGuidance'` joins the reachable-keys list at `:515-516` |

## Public interfaces

| | before → after |
|---|---|
| `PasswordField` props | `{ label, value, onChange, 'aria-invalid'? }` → **`+ 'aria-describedby'?: string`** |
| `catalogue.en.register` | 6 keys → **7** |
| `RegisterScreen` | no export change; internally one constant `GUIDANCE_ID = 'register-password-guidance'` |

`passwordLength.ts`, the routes, the client and the contract are **read-only**.

## Private helpers (test-local, duplicated by decision F7)

`renderScreen`, `fillAndSubmit`, `type`, `installTransport`, `settle`, `passwordInput`, `emailInput`,
`submitControl`, `guidance` (new — queries by `GUIDANCE_ID`). Copied from T44's suite, **not imported**:
every named suite in this package is self-contained, and FU-T43-2 is recorded for the owner rather than
fixed mid-task.

## Execution order

1. **`catalogue.ts`** — the key first, because everything else refers to it. Two things must be true before
   moving on: the template holds `{{min}}` and `{{max}}` (never literals), and the sentence **names the
   rule without diagnosing the input** (no *short*, *long*, *invalid*, *wrong*).
2. **`PasswordField.tsx`** — the prop, with a header paragraph saying why it is a prop and not a contract
   attribute, and naming `ActionButton` as the precedent rather than inventing a justification.
3. **`RegisterScreen.tsx`** — the conditional `<Text>` and the matching `aria-describedby`, both on the
   **same** condition, placed between the password field and the submit control so the visual order matches
   the reading order.
4. **The one line in T43's suite.** Do it here, not later: the key is unreachable by that suite's own
   measure until it is listed, and a later step would leave the tree failing in between.
5. **`tsc --noEmit`, `oxlint src`, the whole suite** — before the new suite exists, so that any breakage in
   the four edits above is attributed to them and not to a new file.
6. **The named suite**, in AC order, with AC3's control written **in the same case** as AC3.
7. **Commit and push** — the harness reverts with `git checkout` + `git clean` over `frontend/src/`.
8. **The harness**, seven rows (six regressions + one canary), run with the **local Docker stack stopped**
   (T44's standing rule; the stack's load is FU-T42-1).

## Tests required

| # | AC | case |
|---|---|---|
| 1 | AC1 | 11 code points → the guidance is in the document after submit |
| 2 | AC2 | 129 code points → the **same string**, asserted as equality with the interpolated value, not as "some guidance" |
| 3 | AC2 | an **astral** out-of-range password (`'👍'.repeat(129)`) → the same guidance; the counting bug points a different way at each end |
| 4 | AC1 | the **empty string** → the same guidance (F11, null-handling) |
| 5 | AC3 | 12 and 128 → **no** guidance, with the control in the same case: the same query, by the same id, finds it after an out-of-range submit |
| 6 | AC4 | the field's `aria-describedby` **equals the id of the element that holds the guidance**, measured by walking the DOM from the input to the element it names |
| 7 | AC5 | typing 3 characters, and typing 200, without submitting → nothing |
| 8 | AC6 | out of range → the request is still sent, the body carries the typed password, nothing is disabled |
| 9 | AC7 | the rendered sentence contains both **numbers**, and those numbers equal `ADVISORY_MINIMUM_CODE_POINTS` / `ADVISORY_MAXIMUM_CODE_POINTS` — the only assertion that can kill the literal-bounds mutant |
| 10 | AC7 | the screen's **source** reaches `t('register.passwordGuidance')` and spells no part of the sentence |
| 11 | AC7 | no diagnostic verdict in the copy — **word-boundary** regexes, with a two-direction control proving the matcher fires on a sentence that does diagnose |

## Risks this plan accepts

| | |
|---|---|
| **The first interpolated string** changes what a copy assertion means. Case 2 asserts against the interpolated result; nothing in this task compares the DOM to the raw template. |
| **Case 11 polices prose**, which is unusual for this package. It is justified by D-T45-2 and bounded: a short, word-boundary list with a control, not a style guide. If it ever fights a legitimate rewording, the finding is the test's, not the copy's. |
| **The association is only as good as the condition.** Guidance and `aria-describedby` are driven by one expression; if implementation splits them into two conditions, case 6 must fail. The harness's association row is the proof. |
| **jsdom cannot tell us whether a screen reader reads it.** AC4 measures the wiring, not the announcement, and the freeze already accepted that limit (D-T45-1's residual risk). |
