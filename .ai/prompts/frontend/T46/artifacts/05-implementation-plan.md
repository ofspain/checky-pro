<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). -->

# frontend · T46 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` — no code |

## Files to create

| | |
|---|---|
| `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts` | the named suite (freeze §4, F1) |
| `.ai/prompts/frontend/T46/artifacts/07-mutation-harness.py` | a copy of T45's 48 rows, re-aimed (phase 7) |

## Files to modify

**None.** Freeze §4 establishes this by enumeration rather than by intent: every `readdir` instrument
filters `!/\.test\.tsx?$/`, and no pinned list or count includes test files. If anything does have to
change, it is a finding to log, not a fix to make.

## Public surface

None. This task exports nothing; it imports `countCodePoints`, `ADVISORY_MINIMUM_CODE_POINTS` and
`ADVISORY_MAXIMUM_CODE_POINTS`, and the module namespace as a whole for the export-shape case.

## Private helpers inside the suite

| | |
|---|---|
| `CASES` | the table: `{ name, input, codePoints, utf16Units }`, every number a **literal** |
| `read(relativePath)` | `readFile` for the purity scan and the self-scan — the same two-line shape T45's suite uses |
| `withoutComments(code)` | strip `/* */` and `//` before scanning, as the package's other source scans do |

No render helpers, no transport, no `beforeEach`, no `afterEach`. There is nothing to arrange or tear down,
which is the point of F1 and the subject of freeze §2.

## Cases, in the order they will appear

| # | case | AC |
|---|---|---|
| 1 | `it.each(CASES)` — `counts $name as code points, not UTF-16 units` : asserts **both** `countCodePoints(input) === codePoints` and `input.length === utf16Units` | AC1, AC3 |
| 2 | `CONTROL: the table can tell the two units apart` — freeze §2's three clauses, as properties of the table | AC3 |
| 3 | `CONTROL: no expectation in this file is computed by the function under test` — source self-scan for `[...`, `Array.from(`, `countCodePoints(` inside the table literal region | AC3 |
| 4 | `the lower bound: eleven astral code points are twenty-two units, so .length would accept what R1 excludes` | AC2 |
| 5 | `the upper bound: 128 astral code points are 256 units, so .length would reject what R1 allows` | AC2 |
| 6 | `a grapheme cluster is not one code point` — flag 2, skin tone 2, ZWJ family 5 | AC5 |
| 7 | `the count is total over every string this screen can hand it` — `''`, lone high, lone low, surrogate in text, long value | AC4 |
| 8 | `the count is not additive across a concatenation` — 1 + 1 = **1** for a split pair | AC4 |
| 9 | `composed and decomposed spellings of the same letter count differently (OQ-T46-2)` | AC4 |
| 10 | `the module reaches for no environment` — source scan of `passwordLength.ts` | AC6 |
| 11 | `CONTROL: the environment scan fires on a module that does reach for one` | AC6 |
| 12 | `publishes exactly the three names its consumers use, and the bounds are 12 and 128` — **sorted** key set | AC7 |

Twelve cases, of which three are controls. The ratio is deliberate: freeze §2 says a suite with no
environment has to put its strength into the table and the controls.

## Execution order

The prompt's schema → dao → service → api order has no analogue here; the dependency order that does exist:

1. **Write `CASES` first**, with both number columns as literals, and run it. Any row whose two numbers are
   wrong fails immediately and loudly — which is the cheapest possible moment to find a bad number, and the
   lesson T44 paid seven phases for.
2. **Add the table control (case 2)** before anything else, because it is what stops the table drifting into
   ASCII-only rows later.
3. **Add the behavioural cases** 4–9 in table order.
4. **Add the source scans** 3, 10, 11 — these need `read`/`withoutComments` and are the only cases that touch
   the filesystem.
5. **Add the export-shape case** 12.
6. Run `vitest run` (whole suite), `tsc --noEmit`, `oxlint src`.
7. **Commit — before the harness**, because the harness reverts `frontend/src`.
8. Copy T45's harness, re-aim it to freeze §3's rows, run it with the Docker stack **stopped**.

## Risks this plan accepts

| | |
|---|---|
| **The suite restates the implementation** | The central risk of the task. Mitigated by literal expectations (F2), the self-scan (F9) and the `Array.from` row, which **must survive**. |
| **A row's two numbers are both wrong in the same direction** | Possible: `'🇬🇧'` is 2 code points and 4 units, and a careless row could say 1 and 2. Mitigated by step 1 — a wrong `codePoints` fails at once, and a wrong `utf16Units` fails at once, because both are asserted against the same string. |
| **The self-scan (case 3) is self-referential** | It scans the file it lives in. That is sound — it is how the package scans any source — but the scan must be aimed at the **table region**, not the whole file, or the source scans in cases 10–11 (which legitimately mention `Array.from`-shaped patterns) would trip it. The plan flags this as the one fiddly instrument. |

**Phase 5 complete.** No code written.
