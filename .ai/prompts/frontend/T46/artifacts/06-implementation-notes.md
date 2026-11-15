<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T46 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md` |
| **Result** | whole suite **1989 / 1990** · `tsc` 0 · `oxlint` 0 · named suite **25 / 25** |

## What changed

| # | file | change |
|---|---|---|
| 1 | `features/account/passwordLengthCountsCodePoints.test.ts` | **new**, 25 cases — the package's first `.test.ts` in `features/`, and its first suite with nothing to arrange |

**No production file changed. No other test file changed.** Freeze §4 predicted this by enumeration and the
prediction held: tasks 43, 44 and 45 each discovered an instrument that had to grow, and this task found
none, because every file-enumerating instrument in the package filters out `.test.tsx?`.

The suite is 15 table rows plus ten cases, three of which are controls.

## The two probes that say the suite works

Run by hand during implementation, and reverted immediately — the harness will repeat them as rows in
phase 7, but a suite that has not been shown to fail is a suite nobody has measured:

| mutant | result |
|---|---|
| `[...value].length` → `value.length` | **11 of 25 cases fail** — every astral row, both grapheme rows, both bound-crossings, the grapheme case and the non-additivity case |
| `[...value].length` → `Array.from(value).length` | **25 / 25 pass** |

The second is as important as the first and is the reason the freeze singled it out. `Array.from` is
*correct* — it iterates code points exactly as the spread does — so a suite that killed it would be pinning
the **syntax** of today's implementation rather than the **unit** R1 names, and would obstruct the next
person who touches the file for a good reason.

## Three things worth recording

**The second number in every row is what makes it a claim.** Each row carries `codePoints` *and*
`utf16Units`, both literal. Without the second, a row saying 11 is satisfied by any function that happens to
return 11 for that string — including `value.length` on an eleven-unit string. The disagreement between the
two numbers is the subject of the task, so both are stated and both are asserted.

**The table control is three clauses, not one.** "At least one row where the units differ" is not enough:
the two **crossings** are what the screens depend on, and they are stated as properties of the table
(`codePoints < MIN && utf16Units >= MIN`, and `codePoints <= MAX && utf16Units > MAX`) rather than as two
cases someone could delete as duplicates. This is T44's table lesson and T45's, applied before the review
phases could find it missing.

**The circularity guard is aimed at the table region, not the file.** The plan flagged this as the one
fiddly instrument and it was: the AC6 scans legitimately mention `Array.from`-shaped patterns, so a
whole-file scan for `[...` would trip on the suite's own honest machinery. The guard extracts
`/const CASES[\s\S]*?\n\];/` and asserts *that* region contains no `[...`, no `Array.from(` and no
`countCodePoints(` — and asserts the region was found at all, because a regex that matches nothing is the
quietest way for a control to measure nothing.

## Deviations

**None.** The frozen brief's twelve cases became twelve `it` blocks (the first of which is an `it.each` over
fifteen rows, hence 25 reported cases). No file outside the one created was touched.

## Verification

| | |
|---|---|
| named suite | **25 / 25** |
| whole suite | **1989 / 1990** — the known environmental lint-script failure |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| case count | 1965 → **1990** (+25) |
