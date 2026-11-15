<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). -->

# frontend · T46 · Phase 2 — Task Implementation Brief

## Task

**T46 — Code points, not UTF-16.** `spec/frontend/tasks.md:80`. Verifies `passwordLengthCountsCodePoints`
(`package.md:183`). Cites **R1**.

## Purpose

Make R1's counting unit falsifiable on its own. The clause *"12 to 128 Unicode code points"* has been
enforced since task 43 and measured only in passing — each of tasks 43, 44 and 45 asserted the unit where it
happened to travel through a screen, and `passwordLength.ts`'s header has named this task as the one that
would come back and assert it in general. Today, replacing `[...value].length` with `value.length` is caught
by rows in three *screen* suites and by nothing that is about the function.

## Scope

**In**

- A new named suite for `countCodePoints`, asserting the counting unit directly.
- The two bounds expressed in **both** units, and the two strings where the units disagree *across* a bound.
- Exactness: expectations derived independently of the implementation.
- The unit's **other** neighbour — grapheme clusters — named and pinned.
- Purity: no environment, no side effects.
- A mutation harness, re-aimed from T45's.

**Out**

- Any change to `passwordLength.ts`. It is correct; this task measures it. A change is a deviation to be
  logged, not a fix to be made.
- Any change to the three suites that already consume the function (**except** a one-line addition if, and
  only if, an instrument there must grow — the shape tasks 44 and 45 both hit).
- Normalisation behaviour as a *decision*. The current behaviour (none) may be pinned; changing it is not
  this task's (OQ-T46-2).
- Rendering, copy, routes, storage, the client.

## Business Rules

| ID | line |
|---|---|
| **R1** | The password is checked as **12 to 128 Unicode code points**, advisory only, and the server is the authority — this task owns the words *"Unicode code points"*. |

## Locked Decisions

| ID | line |
|---|---|
| **L12** | Phase additivity — add a suite; restructure nothing. |
| **L11** | Engaged only if a case renders. It need not. |
| **L3 / L16** | No storage; a pure function reads none, and one case says so. |
| **L6, T173 rules 1–3, T44 AC4** | Not engaged; T44's AC4 exists *because* `maxLength` counts this task's wrong unit. |

## Dependencies

`frontend/src/features/account/passwordLength.ts` — `countCodePoints(value: string): number`,
`ADVISORY_MINIMUM_CODE_POINTS = 12`, `ADVISORY_MAXIMUM_CODE_POINTS = 128`. Vitest 5 on jsdom; `target`/`lib`
ES2022. No globals, no DOM, no filesystem required by the subject.

## Inputs

Strings. The ones that matter: ASCII; astral characters (one code point, two UTF-16 units); regional-indicator
pairs and ZWJ sequences (several code points, one perceived character); composed and decomposed accents; lone
surrogates; the empty string.

**Measured, not assumed** (`node`, this host):

| string | code points | UTF-16 units |
|---|---|---|
| `'👍'` | 1 | 2 |
| `'👍🏽'` (skin tone) | 2 | 4 |
| `'🇬🇧'` (flag) | 2 | 4 |
| `'👩‍👩‍👧'` (family, two ZWJ) | 5 | 8 |
| `'é'` composed U+00E9 | 1 | 1 |
| `'é'` decomposed U+0065 U+0301 | 2 | 2 |
| `'a\uD83Db'` (lone surrogate) | 3 | 3 |
| `'👍'.repeat(128)` | 128 | 256 |

## Outputs

A number. Nothing else — no verdict, no throw, no side effect.

## State Changes

**None.**

## Files to Create

| | |
|---|---|
| `frontend/src/features/account/passwordLengthCountsCodePoints.test.*` | the named suite. **Extension is a decision, not a default**: every suite in this package is `.tsx` because every suite renders; if this one renders nothing, `.ts` is honest and would be the package's first. |
| `.ai/prompts/frontend/T46/artifacts/07-mutation-harness.py` | a fresh copy of T45's 48 rows, re-aimed |

## Files to Modify

None expected. If an instrument elsewhere must grow, it is **one line**, and it is a logged deviation.

## Files NOT to Modify

`passwordLength.ts` · `RegisterScreen.tsx` · `catalogue.ts` · `PasswordField.tsx` ·
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` ·
`registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` ·
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` · `spec/**` · `contracts/**`

## Acceptance Criteria

| AC | claim | R |
|---|---|---|
| **AC1** | An astral character is **one** to `countCodePoints` and **two** to `String.length`, asserted as both numbers for the same string. | R1 |
| **AC2** | The units are shown disagreeing **across a bound in both directions**: a string `.length` would push *into* range at the minimum (11 astral code points = 22 units) and one it would push *out* at the maximum (128 astral = 256 units). | R1 |
| **AC3** | The count is exact for a table of strings whose expected count is stated **independently of the implementation** — never computed with `[...s].length`. | R1 |
| **AC4** | The function is total: the empty string, a lone high surrogate, a lone low surrogate and a long value all return a number and nothing throws. Task 43's D16 case holds part of this; this AC cites it and covers what it does not. | R1 |
| **AC5** | A **grapheme cluster is not one code point**: a flag is 2, a skin-toned emoji is 2, a family emoji is 5, and R1's count says so. This is the unit on the *other* side of a code point, and nothing in the package measures it. | R1 |
| **AC6** | The function depends on its argument alone — no storage, no DOM, no clock, no global — asserted the way this package has learnt to assert an absence. | R1, L3 |
| **AC7** | The module exports exactly the three names its consumers use, and the two constants are 12 and 128. | R1 |

## Required Tests

| | |
|---|---|
| **Named suite** | `passwordLengthCountsCodePoints` |
| **Boundary** | 12 and 128 in both units; 11 astral (22 units) and 128 astral (256 units) — the two crossings |
| **Exactness** | a table whose expectations are literals or built with `String.fromCodePoint`, with a **control** proving the table would catch a wrong number |
| **Totality** | empty, lone high surrogate, lone low surrogate, split pair, long value |
| **Graphemes** | flag, skin tone, ZWJ family |
| **Normalisation** | composed vs decomposed — pinned as it behaves today, with the open question named in the comment, not decided |
| **Purity** | a source-level assertion, because a behavioural claim about an absent side effect is the vacuous shape T43 found in T42's storage spy |
| **Harness** | re-aimed from T45's 48 rows. `[...value]` → `value` is the row this task exists for. The rows that are **not** obvious are the design problem, and the brief does not pretend to know them. |

## Constraints

| | |
|---|---|
| **Performance** | One pass. A long value must not be quadratic; the suite may use a large string but must not make the run slow. |
| **Security** | Test values are literal and non-secret. No real password, no fixture, no randomness. |
| **Thread-safety / transactions** | Not applicable. |
| **Module boundaries** | The suite imports `passwordLength.ts` and nothing from `app/`, `api/` or `auth/`. If it renders, it inherits L11 and the design-system rules — an argument for not rendering. |
| **Null handling** | The parameter is typed `string`; `null`/`undefined` are a **type error**, not a runtime case. A case that casts them in to see what happens is testing TypeScript, not R1. Totality means total over *strings*. |

## Open Questions

**No blockers.** Three questions are recorded, all answerable inside the task:

- **OQ-T46-1** — do the constants belong to this task or to the three cases that already pin them?
- **OQ-T46-2** — is R1 meant to be normalisation-sensitive? (Behaviour is unambiguous and pinnable either way; the question is the owner's.)
- **OQ-T46-3** — what can the harness measure on a function with no branches, no state and no environment?

**Phase 2 complete.**
