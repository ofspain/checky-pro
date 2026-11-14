<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T43 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` · `artifacts/10-mutation-run.txt` |

Phase 9 fixed what was **wrong**. Phase 10 adds what was **missing**: five cases, one table row, and three
harness rows. The named suite is **34 → 39 cases**; the whole suite **1931 → 1938**.

## Cases added

| | case | what it closes |
|---|---|---|
| 1 | `the empty string, which must not crash the count` (a **table row**, not a new case) | **D16**'s frozen clause, unmeasured since the freeze (F5 / I10). Both fields empty is the most ordinary accidental submit there is, and the row asserts what every row asserts: the request still goes |
| 2 | `counts a total function: empty, astral, and a lone surrogate all return a number (D16)` | `passwordLength.ts`'s header claimed totality and nothing asserted it. A count that could **throw** would be a client-side gate, which is the one thing R1 forbids the client to be |
| 3 | `reaches the catalogue for every word it renders, which the DOM cannot tell from a literal` | **TR09**, the harness survivor. Both directions: every key reached, no sentence spelled |
| 4 | `arrives at /app/register through the REAL route table, with no session` | **I2**. `routesResolveUnderAppPrefixOnly` *resolves* the path with `matchRoutes` and `publicRouteSet…` *walks* the table for an element — neither **renders** it. T41 and T42 both proved their composition this way |
| 5 | `returns the form to its idle state when the send rejects, and claims nothing` | **F2 / I7**, and it holds phase 9's fix: the region empties, no transport word leaks into the copy, both values survive, and a **second submit reaches the transport** — the retry is what makes `idle` the right state rather than a quiet dead end |

Plus two cases from phase 9 that are instruments rather than fixes: the **pair pin** on the two buttons, and
`RegisterScreen` in T174's four-screen focus guard.

## The substring trap bit a second time, in the same session

Case 3 failed on its first run with *"the screen spells "Password" instead of looking it up"* — because
`'Password'` is the catalogue's one single-word string and a substring of **`PasswordField`** in the
screen's own import line. This is **exactly** the defect phase 6 found and corrected in T173's copy scan,
reproduced in a different file hours later.

The cause is structural, not carelessness: the word-boundary matcher now exists **twice**, once in
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` as `spelledOut()` and once inline here, because
this package has no shared test-util module and every suite is self-contained. **FU-T43-2**: a third copy
should become one module. Recorded rather than fixed, because creating a shared test module is a change to
how every suite in the package is written and belongs to a task that owns it.

## What the suite still does not measure

| | |
|---|---|
| **Enter-to-submit** | jsdom implements no implicit submission. AC9 asserts the structural cause and measures the click path that shares the handler — **OQ-T43-8** |
| **`noValidate`'s effect** | jsdom performs no interactive constraint validation, so the attribute's whole effect is invisible here. It is the harness's one `unfalsifiable` row, **TR24**, which is the honest way to carry it |
| **A failure sentence** | A rejected send says nothing. D7 froze six keys and the words are an owner's decision — **OQ-T43-9** |
| **Double submit** | Two clicks still send two requests. Owner's call — **OQ-T43-11** |
| **Focus on arrival** | D5 froze *no focus move*, and the guard added in phase 9 now **enforces** it. Whether a public screen should focus its heading is **OQ-T43-10** |

## The harness, re-aimed and re-run

`python3 -I .ai/prompts/frontend/T43/artifacts/07-mutation-harness.py` → **28 rows, 26/26 regressions
killed, 0 BROKEN, 1 canary killed, 1 survivor (TR24, `unfalsifiable`), 0 disagreements.** Baseline 1 failed
of **1938**, the documented host `oxlint` case, subtracted by case name. Full output, including the appended
TR28 re-run, in `artifacts/10-mutation-run.txt`.

All three phase-7 survivors are now killed, **and each died to exactly the instrument written for it** —
which is the part worth checking, because a kill count cannot tell a precise instrument from a lucky one:

| row | killed by | cases failed |
|---|---|---|
| **TR09** copy hardcoded in English | `reaches the catalogue for every word it renders…` | **1** |
| **TR21** focus moved on mount | `RegisterScreen leaves focus on <body> when it mounts` (T174's grown guard) | **1** |
| **TR26** the rejection handler deleted | `returns the form to its idle state when the send rejects…` | **1** |

TR26 is the contrast that makes the point: **its predecessor killed thirteen cases and measured nothing**,
because it removed the whole send. The replacement kills **one**, and that one is the claim.

## TR27 was killed and still measured nothing — so TR28 was written

TR27 changes `ActionButton`'s accent alone and was reported `KILLED/own`. Reading **which** cases died
rather than how many shows two: the new pair pin **and** T173's own `gives ActionButton the colour and type
utilities it owns`. The second would have failed with no pair pin in the repository, so the row never
isolated the claim it was written for — the same defect the old TR26 had, two rows apart.

**TR28** is the row that isolates it: `SubmitButton`'s class string **and** its `REQUIRED_UTILITIES` row
change together, which is what a well-meant restyle of one button looks like. Every per-file row still
passes — each file satisfies its own, now different, row — and exactly **one** case fails: the pin on the
pair. That is the hole `SubmitButton`'s header claimed was closed, finally measured as its own claim.

## The inert-spy sweep, and a claim of mine it corrected

Phase 9's fix carried a comment saying the inert instance spy was *"carried as FU-T43-1 for the other
suites that do this"*. Every `vi.spyOn` target in `frontend/src` was then inventoried:
**80 uses of `Storage.prototype` across 18 files**, 8 on `globalThis` (`fetch`, `setTimeout`,
`setInterval` — all plain writable properties), 27 on `console`, module objects, `Request.prototype` and
`crypto`, and **exactly one on a storage instance**: T42's AC11, already fixed.

So the correct pattern was the package's norm from the start, and this was **one case that deviated from an
eighty-use convention**. It survived two phases of review because its assertion was **negative** — the one
direction in which a dead instrument and a clean result look identical. **FU-T43-1 is closed**, and the two
source comments that call it open are corrected here.
