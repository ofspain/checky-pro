<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T45 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md` |
| **Result** | whole suite **1961 / 1962** · `tsc` 0 · `oxlint` 0 · named suite **16 / 16** |

## What changed

| # | file | change | AC |
|---|---|---|---|
| 1 | `i18n/catalogue.ts` | `register.passwordGuidance: 'Use a password of {{min}} to {{max}} characters.'` — **the first interpolated string in this application** | AC7 |
| 2 | `app/ui/PasswordField.tsx` | `'aria-describedby'?: string`, rendered on the `<input>` | AC4 |
| 3 | `features/account/RegisterScreen.tsx` | `GUIDANCE_ID`, the interpolated lookup, and `<Text id={GUIDANCE_ID}>` plus the field's `aria-describedby` **on one condition** | AC1–AC6 |
| 4 | `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | one line — the new key joins the reachable list | AC7 |
| 5 | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | **unplanned** — the catalogue key-set pin (see deviations) | — |
| 6 | `registrationRejects…CodePoints.test.tsx` | **new**, 16 cases | all |

The copy: **"Use a password of 12 to 128 characters."** It names the rule, delivers no verdict, and serves
both ends of the range, so the screen never branches on which bound was missed (D-T45-2).

Three things the catalogue comment records rather than leaves to be rediscovered: why the value is a
template (the bound lives in `passwordLength.ts` and a literal here would be a second copy that drifts);
why the sentence says **characters** rather than *code points* (`tasks.md` itself says "128 characters",
and the two part only for a grapheme cluster built from several code points — naming the unit precisely
would cost every visitor a term they do not know to save a few a surprise the server resolves anyway); and
that one condition drives both the mark and the sentence.

## Deviations

**D-1 — the design-system suite had to change, and the freeze said it would not.** Phase 3's F9 concluded
that file needed no edit, reasoning about element counts, `RENDER_SITES` and prop-type pins. It missed —
and the freeze accepted — that the same file holds `keeps the catalogue's key set exactly as it was
(AC11)`, an **exact sorted list of every catalogue path**. Adding a key failed it within a minute of the
first run. The key is now admitted there with a comment, on the same terms as tasks 174, 43 and 42 before
it, and the comment says plainly that F9 was wrong and why, so the next task that adds a word meets the pin
instead of the surprise.

**D-2 — the copy scan asserts the SENTENCE, not its words.** The plan's case 10 said *"spells no part of
the sentence"*, and the first draft looped over every word longer than three characters. It failed
immediately on **`password`** — which this screen legitimately holds as a state variable, a setter name, a
prop and part of a catalogue key. The claim was never that a screen may not contain the words; it is that
it must not spell the sentence instead of looking it up, which is the shape task 43 settled on. Both the
rendered sentence and the raw template are checked.

## Two cases the plan did not ask for

| | |
|---|---|
| `removes both the mark and the sentence when a later submit is in range` | The `advisory` state has a **way back** and nothing measured it. If the sentence outlived the mark, a field would be described as invalid while reading as acceptable — the worst of the three possible disagreements, because assistive technology would announce a rule the screen no longer holds. |
| `CONTROL: the verdict scan fires on a sentence that DOES deliver one` | The verdict scan is a forbidden-word list, which fails in two directions. The control proves it sees *"too short"* and *"invalid"*, and that it does **not** read **"belongs"** as *long* — the substring trap, pinned in the one case that could reintroduce it. |

## Verification

| | |
|---|---|
| named suite | **16 / 16** |
| whole suite | **1961 / 1962** — the known environmental lint-script failure |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| case count | 1946 → **1962** (+16) |
