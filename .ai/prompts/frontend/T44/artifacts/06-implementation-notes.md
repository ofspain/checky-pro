<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). Test-only task: the tests ARE the implementation. -->

# frontend · T44 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md` |
| **Production files changed** | **none** |
| **Test files changed** | 2 |

## Step 1 first: the bound was already wired, so nothing production changed

The plan's step 1 was to stop if it was not. `RegisterScreen.tsx:59-61` reads:

```tsx
const length = countCodePoints(password);
setAdvisory(length >= ADVISORY_MINIMUM_CODE_POINTS && length <= ADVISORY_MAXIMUM_CODE_POINTS
  ? 'in-range' : 'out-of-range');
```

and `passwordLength.ts` still exports `ADVISORY_MAXIMUM_CODE_POINTS = 128`. **The clause this task exists to
measure was written by T43 and had never been executed against a value that could falsify it.** No
production file is touched, as F6 requires.

## What changed

### `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`

| # | change | AC |
|---|---|---|
| 1 | row `'128 ASCII characters, the upper bound itself'` — `'a'.repeat(128)`, 128, **in** | AC1 |
| 2 | row `'128 astral code points, which are 256 UTF-16 units'` — `'👍'.repeat(128)`, 128, **in** | AC2 |
| 3 | row `'129 ASCII characters, one past the upper bound'` — `'a'.repeat(129)`, 129, **out** | AC3 |
| 4 | case `T44 AC2 — 128 emoji are 256 UTF-16 units in the DOM and 128 code points to the check` | AC2 |
| 5 | case `T44 AC4 — neither field caps its own length, which would count the wrong unit and truncate` | AC4 |
| 6 | the table control gains two assertions: a row **on** `ADVISORY_MAXIMUM_CODE_POINTS`, and a row **above** it | AC5 |
| 7 | three comment blocks this task falsified, rewritten | — |

The rows carry their reasons where the next reader meets them. The astral row's comment states the factor
explicitly — *"one astral character is one code point and two units, 24 for the row above and 256 for this
one. Phases 0 to 3 all said 512 until phase 4 measured it."* — because the error survived four phases and
the row is the only place a future reader will think to check it.

The 129 row's comment records why it marks and says nothing: *"Task 45 owns the advisory guidance for 11 and
129 and should choose its own words"*, and why it has to be here at all: *"a bound of `<= 999` passes every
other row in this table."*

### `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`

One case beside the existing field-pair exception: `EmailField.tsx` and `PasswordField.tsx` go through
`withoutComments` and must not match `/maxLength/i` or `/minLength/i`. Its comment carries the unit argument
— the attribute **cannot express R1's rule**, because R1 counts code points and `maxLength` counts UTF-16
units, so `maxLength={128}` truncates a valid 128-code-point passphrase to 64 characters **before any
handler runs**, and then the advisory check measures a value nobody typed.

## Why AC4 needed two instruments, which is the one thing this task learned by measuring

Phase 3's F4 said the table's body-equality assertion would catch a `maxLength={128}`. **It will not**, and
the freeze measured why: `maxLength` constrains **user editing**, not a programmatic value set, and every
case in this file types through `HTMLInputElement.prototype`'s `value` setter. With `maxlength=128` on a
real jsdom element, a 256-unit value went in and 256 units came back untouched.

So the mutant is invisible to every behavioural case in the suite, and the two new pins are the only things
that can see it:

- the **rendered** pin (case 5) — catches an attribute that reaches the live field by any path;
- the **source** scan — catches it in a primitive no screen in that suite renders, or in a field a later
  task adds.

Both carry the same positive control in their comments: **the harness's `maxLength={128}` mutant**. If it
survives, both pins are vacuous, and the comment says so in those words, so that a future reader who sees it
survive knows immediately what it means.

## Verification

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| named suite + design-system suite | **109 / 109** |
| whole suite | **1944 / 1945** — the single failure is the known environmental one in `src/api/lintRejectsHandWrittenBackendFetch.test.ts` (this host's `oxlint@1.87.0` prints no summary line), unchanged |
| case count | **1939 → 1945**, exactly the plan's +6 (3 rows + 2 cases + 1 scan); named suite **40 → 45** |

## Deviations

**None.** Every change traces to a frozen AC and to a planned file. Nothing was added that the plan did not
name, and nothing the plan named was dropped.

One thing the plan did not foresee: a **third** comment needed rewriting. The `PASSWORDS` table header
itself predicted *"TASK 44 ADDS ROWS AND NOTHING ELSE: `'a'.repeat(128)` in range, `'a'.repeat(129)` out of
it"* — two rows, where three landed. It now says so, and why: *"nobody had looked at which way it points at
128 when the prediction was written."*
