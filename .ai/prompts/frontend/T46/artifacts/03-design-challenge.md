<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T46 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The test file should be `.test.ts`, not `.tsx`
· **Severity:** **Minor/Major**
· **Evidence:** The brief notes that `.tsx` is the default because every suite so far renders, and that `.ts` would be honest if this suite renders nothing. There are already `.test.ts` files in the package (`frontend/src/api/*.test.ts`, `frontend/src/auth/*.test.ts`, etc.).
· **Assessment:** `countCodePoints` is a pure function of a string. It needs no DOM, no React, no jsdom environment. A `.ts` file signals that this suite is about a unit of logic, not a screen, and keeps it from inheriting L11/design-system concerns it does not need. Vitest runs `.ts` files without issue.
· **Recommended brief amendment:** Specify `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts` as the named suite file.

### F2 — Expected code-point counts must be stated as literals, never computed with `[...s].length`
· **Severity:** **Blocker/Major**
· **Evidence:** AC3 requires expectations derived independently of the implementation. The implementation is `[...value].length`. Any expectation built the same way would be circular.
· **Assessment:** For each test string, the expected count is a Unicode fact that can be stated directly: `'👍'` is 1 code point, `'🇬🇧'` is 2, `'👩‍👩‍👧'` is 5, the empty string is 0, etc. The assertion should compare `countCodePoints(input)` against the literal number. A table is the natural shape, with `name`, `input`, and `codePoints` columns.
· **Recommended brief amendment:** Require a `CASES` table whose `codePoints` column holds literal numbers, and add a control proving the table is not derived from the function under test.

### F3 — The two bound-crossings are the central falsifiable claim
· **Severity:** **Blocker/Major**
· **Evidence:** AC2 asks for strings where `.length` and code points disagree **across a bound in both directions**.
· **Assessment:**
  - **Lower-bound crossing:** 11 astral code points (`'👍'.repeat(11)`) = 22 UTF-16 units. `String.length` reads 22 (≥ 12, so `.length` would accept), but code points read 11 (< 12, so the correct count rejects). The case asserts `countCodePoints(password) === 11` and `password.length === 22`.
  - **Upper-bound crossing:** 128 astral code points (`'👍'.repeat(128)`) = 256 UTF-16 units. `String.length` reads 256 (> 128, so `.length` would reject), but code points read 128 (in range). The case asserts `countCodePoints(password) === 128` and `password.length === 256`.
· **Recommended brief amendment:** Add dedicated cases for these two crossings, asserting both numbers for the same string.

### F4 — Grapheme clusters must be pinned on the far side of a code point
· **Severity:** **Major**
· **Evidence:** AC5 requires that a flag, skin-toned emoji, and ZWJ family are counted by code points, not by perceived character. The brief provides the measured table.
· **Assessment:** These are the cases where "one character" and "one code point" part company. A visitor might reasonably expect `'🇬🇧'` to be one character, but R1 counts code points, so it is 2. The suite must assert this explicitly, with a comment explaining that the task is pinning the unit R1 chose, not the unit a user might expect.
· **Recommended brief amendment:** Add table rows for `'👍🏽'` (2), `'🇬🇧'` (2), and `'👩‍👩‍👧'` (5), and assert `countCodePoints` returns those values.

### F5 — Totality must cover the edge cases the header claims
· **Severity:** **Major**
· **Evidence:** `passwordLength.ts:15-17` claims the function is total over empty strings, lone surrogates, and very long pastes. Task 43's D16 case covers empty, astral, and lone surrogate partially; T46 must cover the rest.
· **Assessment:** Cases needed:
  - `''` → 0
  - `'\uD83D'` (lone high surrogate) → 1
  - `'\uDC4D'` (lone low surrogate) → 1
  - `'a\uD83Db'` (lone surrogate in text) → 3
  - `'👍'.repeat(128)` → 128 (long value, also serves AC2)
  - A surrogate pair split across two strings and concatenated? Not needed; the function takes one string.
· **Recommended brief amendment:** Add a totality describe block with these cases, asserting no throw and the expected count.

### F6 — Purity must be asserted at the source level
· **Severity:** **Major/Blocker**
· **Evidence:** AC6 requires the function depend on its argument alone — no storage, DOM, clock, or global. The brief cites T43's lesson that a behavioral negative assertion about an absent side effect is vacuous.
· **Assessment:** A source scan of `passwordLength.ts` is the only honest way. Strip comments and assert the file contains no occurrences of `localStorage`, `sessionStorage`, `document`, `window`, `fetch`, `Date`, `setTimeout`, `console`, etc. This is the same shape as T43's storage-spy control.
· **Recommended brief amendment:** Add an AC6 case that reads `passwordLength.ts`, strips comments, and asserts it reaches none of the environment APIs.

### F7 — Normalization behavior should be pinned as it is today, with OQ-T46-2 named
· **Severity:** **Major**
· **Evidence:** The brief lists composed vs decomposed accents as inputs and names OQ-T46-2 about whether R1 is normalization-sensitive. The current implementation does not normalize.
· **Assessment:** `'é'` (U+00E9, composed) is 1 code point; `'é'` (U+0065 U+0301, decomposed) is 2 code points. The suite should assert both as literals and include a comment that this is the current behavior, not a decision. This pins the behavior so a future change to normalize is deliberate.
· **Recommended brief amendment:** Add rows for composed and decomposed `é`, with a comment referencing OQ-T46-2.

### F8 — The module export shape should be pinned
· **Severity:** **Minor/Major**
· **Evidence:** AC7 requires the module exports exactly the three names its consumers use, and the constants are 12 and 128.
· **Assessment:** Two assertions:
  1. `Object.keys` of the imported module equals `['countCodePoints', 'ADVISORY_MINIMUM_CODE_POINTS', 'ADVISORY_MAXIMUM_CODE_POINTS']`.
  2. `ADVISORY_MINIMUM_CODE_POINTS === 12` and `ADVISORY_MAXIMUM_CODE_POINTS === 128`.
· **Recommended brief amendment:** Add an AC7 case for export shape and constant values.

### F9 — The exactness control must prove the table is alive
· **Severity:** **Major**
· **Evidence:** AC3 requires expectations stated independently, and a control is required.
· **Assessment:** The control can take two forms:
  - A synthetic row injected into the table at test time that has a deliberately wrong expected count, and asserting the test would fail. This is awkward in `it.each`.
  - A simpler control: assert that the `CASES` table's `codePoints` column is not empty, contains at least one value other than 0, and that the maximum expected count is 128. The strongest control is a source-level assertion that the test file does not contain `[...` or `Array.from(...).length` when building expectations.
· **Recommended brief amendment:** Add a source-scan control asserting the test file does not compute expected counts with `[...input].length`, `Array.from(input).length`, or `countCodePoints(input)`.

### F10 — The harness must target the implementation's only meaningful mutant
· **Severity:** **Major**
· **Evidence:** The brief asks what a harness can measure on a pure function with no branches, state, or environment.
· **Assessment:** The central mutant is `[...value]` → `value`, turning the function into `value.length`. This is the bug R1 forbids. Other useful mutants:
  - Replace `[...value]` with `Array.from(value)` — same behavior, should survive (not a regression). This is a control for the harness itself.
  - Return `0` for all inputs — killed by every positive case.
  - Throw on empty string — killed by totality.
  - Add normalization (`value.normalize().length` or similar) — changes the grapheme/normalization cases, surfacing OQ-T46-2.
  - Export an extra name — killed by AC7 export shape.
  - Change a constant value — killed by AC7.
· **Recommended brief amendment:** List the harness rows and their expected outcomes. Keep the harness small; most rows will be killed by the same cases.

### F11 — The spread-vs-`Array.from` distinction is not a regression
· **Severity:** **Minor**
· **Evidence:** `[...value]` and `Array.from(value)` both iterate code points for strings.
· **Assessment:** A harness row that replaces `[...value]` with `Array.from(value)` should survive, because both are correct. This is a useful control: it proves the harness does not over-fit to the spread syntax.
· **Recommended brief amendment:** Include an `Array.from(value)` mutant classified as expected survivor / same behavior.

### F12 — No production file should change
· **Severity:** **Major**
· **Evidence:** The brief explicitly states `passwordLength.ts` is correct and should not be modified.
· **Assessment:** T46 is a measurement task. If the implementation needs to change to make tests pass, that is a deviation to report, not a fix to make. The design challenge should reinforce this.
· **Recommended brief amendment:** Reiterate: "If a test fails against the current `passwordLength.ts`, the test is wrong or the finding is against T43; do not modify production source."

---

## Summary

T46 is a pure unit-test task whose design challenge is to make R1's counting unit falsifiable without re-implementing the function. The key decisions are:

1. Use `.test.ts` (**F1**) because no rendering is needed.
2. State expected counts as literals (**F2**) and prove the table is independent of the implementation (**F9**).
3. Pin the two bound-crossings where `.length` and code points disagree (**F3**).
4. Pin grapheme clusters as the unit on the other side of a code point (**F4**).
5. Cover totality explicitly (**F5**).
6. Assert purity at the source level, not behaviorally (**F6**).
7. Pin normalization behavior today without deciding OQ-T46-2 (**F7**).
8. Pin export shape and constant values (**F8**).
9. Keep the harness small and focused on the central `[...value]` → `value` mutant, with `Array.from` as a same-behavior control (**F10-F11**).
10. Leave `passwordLength.ts` untouched (**F12**).

**Phase 3 complete.** Twelve findings returned; no code changed.
