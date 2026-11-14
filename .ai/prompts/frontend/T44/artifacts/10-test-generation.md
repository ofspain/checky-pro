<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). Tests only; no production code. -->

# frontend · T44 · Phase 10 — Test Manifest

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Named test** | `registrationAcceptsTwelveAndOneTwentyEightCodePoints` — **complete** as of this task |
| **Suite** | **1945 / 1946** (the known environmental lint-script failure) · `tsc` 0 · `oxlint` 0 |
| **Harness** | `artifacts/10-mutation-run.txt` — **32/32 killed · 0 broken · 2 canaries killed · 1 expected survivor · 0 disagreements** |

## What this phase added

Phases 6 and 9 wrote the cases; this phase added the two things that were missing from them — **a control
for each negative assertion.** Both AC4 pins claim that something is *absent*, and nothing inside either
file could tell a live instrument from a dead one; only a mutation run could. That is precisely the shape
T42's AC11 had, where an instance spy on jsdom storage never intercepted and the negative passed for seven
tasks. A control that ships with the file is what makes the claim survive a refactor nobody runs the
harness against.

| test | file | what it holds |
|---|---|---|
| `CONTROL: the length-cap scan can SEE a cap — the negative above is not self-proving (T44)` | `screensRender…` | the regexes match a capped sample; they also match a **spread** and a **forwarded prop**, the two shapes a narrower `\bmaxLength\s*=` would miss; and they do **not** match an uncapped file |
| the probe inside `T44 AC4 — neither field caps its own length…` | named suite | `hasAttribute` answers **true** for an input that carries the attributes, and **false** for a name nobody set |

The second control corrected a claim written minutes earlier in its own comment: the spelling risk is **not**
the casing, because `hasAttribute` is ASCII-case-insensitive on an HTML element, so the camelCase React prop
name answers for the lowercase DOM attribute and cannot make the pin vacuous. The real failure mode is a
name that is simply wrong, and that is what the control now pins.

## Manifest — every acceptance criterion to the test that holds it

| AC | R | test(s) | mutant that proves it is live |
|---|---|---|---|
| **AC1** 128 ASCII is accepted, sent unaltered, not marked | R1 | `sends 128 ASCII characters, the upper bound itself to the server either way` (table row through the existing `it.each`) | **TR31** (`<=` → `<`) |
| **AC2** 128 astral = 256 UTF-16 units is accepted; the count says 128 where `String.length` says 256 | R1 | `sends 128 astral code points, which are 256 UTF-16 units…` (row) · `T44 AC2 — 128 emoji are 256 UTF-16 units in the DOM and 128 code points to the check` (DOM case) | **TR33** (the upper comparison counts units) — killed by exactly those two |
| **AC3** 129 is out of range, marked, **and still sent** | R1 | `sends 129 ASCII characters, one past the upper bound to the server either way` (row) | **TR30** (the `<=` half deleted) — killed by that row alone · **TR29** (maximum → 999) |
| **AC4** no `maxLength`/`minLength` on any primitive, pinned structurally and on the rendered element | R1 | `gives NO primitive a length cap of its own…` (scan over `PRIMITIVE_FILES`) · `T44 AC4 — neither field caps its own length…` (rendered) · one control each | **TR32** (`maxLength` on `PasswordField`) · **TR35** (`minLength` on `EmailField`) — each killed by **exactly** those two pins and nothing else |
| **AC5** T43's claims hold by extension, not rewrite | L12 | `CONTROL: the table measures something — the two ends disagree about the mark`, now pinning a row **on** the maximum and a row **above** it | **TR29**, which fails the control as well as the 129 row |

## Boundary and state coverage

| | |
|---|---|
| **Lower bound** | 11 (out) · 12 (on, in) · 13 (in) · 12 astral (in) · 7 emoji (out) · empty (out) — all task 43's |
| **Upper bound** | 128 ASCII (on, in) · 128 astral (on, in) · 129 (out) — this task's |
| **Counting unit** | proven at **both** ends and in **both** directions: at 12 a `.length` check over-counts *into* range (false accept, T43's 7-emoji row); at 128 it over-counts *out of* range (false reject, the astral row) |
| **State transitions** | `unchecked → in-range` and `unchecked → out-of-range` at both ends; `idle → submitting → sent` and `→ idle` on rejection, all task 43's and all still green |
| **Not covered, deliberately** | Enter-to-submit (jsdom has no implicit submission — OQ-T43-8) · `noValidate`'s effect (no interactive constraint validation — TR24, the expected survivor) · double submit (OQ-T43-11, out of scope) |

## Named tests from `package.md` §8

| | |
|---|---|
| `registrationAcceptsTwelveAndOneTwentyEightCodePoints` | **complete.** Task 43 proved the 12, task 44 proves the 128. The file header no longer calls its own name half a promise. |
| `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` | **task 45.** The rows it needs now exist (11 and 129); it adds the *words*, which L11 keeps in the catalogue. |
| `passwordLengthCountsCodePoints` | **task 46.** The counter is unchanged and untouched by this task. |

## Conventions

`agents.md`'s JUnit/Testcontainers clauses do not apply to this package — the frontend's equivalents are
vitest + jsdom, no clock is involved (nothing here is time-dependent), and the contract is checked through
the generated client rather than by a separate contract test. No production code was changed in this phase
or in this task.
