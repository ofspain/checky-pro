<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No code. Plans against the FROZEN brief only. -->

# frontend · T44 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**FROZEN**) |
| **Shape of the work** | A measurement task. **No production file is planned.** Two test files change. |

## Files to create

**None.** The frozen brief authorizes none, and F6 binds: *"If a production change appears necessary, pause
and file a finding against T43; do not modify production source to make the tests pass."*

## Files to modify

| file | what changes |
|---|---|
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | 3 table rows · 1 new DOM case (AC2) · 1 new structural case (AC4's rendered half) · the control extended (AC5) · 2 comment blocks that describe the file as half-finished |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | 1 new case: the `maxLength`/`minLength` source scan over both field primitives (AC4's structural half) |

Both are authorized by §6 of the frozen brief. Nothing else is touched.

## A numbering hazard to settle before the first edit

The named suite is organised by **T43's** AC numbers (`AC1` at `:177`, `AC2/AC3/AC4` at `:249`, through
`AC10`). T44 has its own AC1–AC5. A file holding two tasks' AC numbering has bitten this project before, so:

- **New rows** need no label — they are data in an existing table.
- **New cases** are labelled `T44 AC2`, `T44 AC4` in their `it(...)` strings. The surrounding `describe`
  keeps T43's numbering untouched.
- The section banner at `:222-224` is updated to say the row was added, not that it is awaited.

## Public methods (signatures)

**None.** No production export is created, changed or removed. `countCodePoints`,
`ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS` are consumed exactly as they stand.

## Private methods (test-local)

No new helper is planned. Everything needed already exists in the two files:

| helper | file:line | use here |
|---|---|---|
| `type(el, value)` | named suite | types the 128/129 values through `HTMLInputElement.prototype`'s setter |
| `fillAndSubmit`, `renderScreen`, `settle`, `installTransport`, `ACCEPTED` | named suite | the `it.each` path, unchanged |
| `passwordInput`, `emailInput`, `submitControl` | named suite | element access |
| `read(file)`, `withoutComments(code)` | `screensRender…:171` | AC4's source scan, with its control already at `:388` |

If a new helper turns out to be needed, that is a signal the table is being restructured — **L12** — and it
stops for a finding instead.

## Entities / Repositories / Services used

Not applicable (browser SPA, no persistence layer). The equivalents:

| | |
|---|---|
| **Component under test** | `RegisterScreen`, through the real route table in the arrival case |
| **Client** | `createPublicAuthApi()` → generated `createAuthClient()` → `POST /accounts` |
| **Primitives** | `EmailField`, `PasswordField` (AC4's subjects), `Form`, `SubmitButton` |
| **Pure module** | `features/account/passwordLength.ts` — read, never written |

## Unit / integration tests required

| # | AC | case | kills |
|---|---|---|---|
| 1 | AC1 | row `'128 ASCII characters, the upper bound itself'` — `'a'.repeat(128)`, 128, **in** | `<=` → `<` |
| 2 | AC2 | row `'128 astral code points, which are 256 UTF-16 units'` — `'👍'.repeat(128)`, 128, **in** | `countCodePoints` → `.length` at the **upper** bound (false reject) |
| 3 | AC3 | row `'129 ASCII characters, one past the upper bound'` — `'a'.repeat(129)`, 129, **out** | the maximum widened to 999; the `<=` half deleted |
| 4 | AC2 | `T44 AC2 — 128 emoji are 256 UTF-16 units in the DOM and 128 code points to the check`: types `'👍'.repeat(128)`, asserts `value.length === 256`, `countCodePoints(value) === 128`, and that the submitted body equals the typed value. The mirror of `:276-286`, at the other end | the same `.length` mutant, through the DOM rather than the helper |
| 5 | AC4 | `T44 AC4 — the password field imposes no length limit of its own`: on the rendered field, `hasAttribute('maxlength')` and `hasAttribute('minlength')` are both false, for both fields | `maxLength={128}` added to a primitive |
| 6 | AC4 | in `screensRender…`, beside the field-pair exception at `:405-417`: scan `EmailField.tsx` and `PasswordField.tsx` through `withoutComments` and assert neither names `maxLength` or `minLength`, with the reason in the comment — **UTF-16 units, user-editing only** | the same mutant, before it reaches a render |
| 7 | AC5 | the control at `:337-344` gains: a row sits **on** `ADVISORY_MAXIMUM_CODE_POINTS`, and a row sits **above** it | a future task deleting the 128 or the 129 row |

**Case 5 and case 6 are not duplicates and both are required.** Case 6 cannot see a limit introduced by a
render-time path; case 5 cannot see one in a primitive no screen in this suite renders. Neither can be
replaced by the body-equality assertion — measured at the freeze: `maxLength` constrains **user editing**,
not a programmatic value set, so a `maxLength={128}` mutant leaves every existing behavioural case passing.

**Expected totals:** the named suite **40 → 45** (3 rows × 1 `it.each` = 3, plus cases 4 and 5);
`screensRender…` +1; whole suite **1939 → 1945**, with the one known environmental failure in
`lintRejectsHandWrittenBackendFetch.test.ts` unchanged.

## Execution order

The backend's *schema → dao → service → api → tests* order has no analogue here; the governing order is
**commit before mutate**, and **widen the instrument before the thing it measures**.

1. **Read first, change nothing.** Confirm `RegisterScreen.tsx:60` still evaluates the upper half and
   `ADVISORY_MAXIMUM_CODE_POINTS` is still 128. If either is false, stop — that is the T43 finding F6 names.
2. **The three rows** into `PASSWORDS` (`:233-246`), in the table's existing shape and order convention
   (in-range rows, then out-of-range).
3. **The control** (`:337-344`) — extended in the same commit as the rows, because a row added without the
   control's pin is a row the next task can silently delete.
4. **Case 4** (the astral DOM case) beside `:276`.
5. **Case 5** (the rendered no-limit pin).
6. **Case 6** in `screensRender…`, beside the existing field-pair exception, reusing `withoutComments`.
7. **The two comment blocks**: the file header's *"THE NAME IS HALF A PROMISE TODAY"* (`:4-7`) and the
   section banner at `:222-224`. Both state the 128 end is task 44's to come; after step 6 they are false,
   and a comment the task falsifies is a comment the task must fix.
8. **`tsc --noEmit`, `oxlint src`, the whole suite** — all three before any mutant runs.
9. **Commit and push.** `revert()` is `git checkout --` + `git clean -fq` over `frontend/src/`; an
   uncommitted test edit is destroyed silently.
10. **Phase 7's harness** — T43's 28 rows copied and re-aimed, with the six frozen upper-bound mutants of
    §5 and their predicted killers. The harness asserts its own baseline; a BROKEN mutant (`total <
    base_total`) is not a kill.

## Risks this plan accepts

| | |
|---|---|
| **The astral row's name says 256** — a number that was wrong in three artifacts until it was measured. The row's `codePoints: 128` is what the case asserts; the 256 appears in the dedicated case, where `value.length` measures it rather than the author claiming it. |
| **Case 5 asserts a negative.** T42's AC11 was vacuous from birth because nobody proved its spy could see a write. Here the negative is cheap to control: the design-system suite's scan control at `:388` already proves `withoutComments` does not eat what it is looking for, and the harness's `maxLength={128}` mutant is the positive proof for both instruments — if it survives, both are vacuous. |
| **No production change** is the expectation, not a guarantee. If step 1 finds the bound unwired, the plan stops at step 1. |
