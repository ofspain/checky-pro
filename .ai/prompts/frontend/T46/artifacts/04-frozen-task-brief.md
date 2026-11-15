<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). Downstream phases may not renegotiate this. -->

# frontend · T46 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Task** | T46 — Code points, not UTF-16 |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Approval** | Owner's standing instruction to run to the next Kimi gate. Twelve findings dispositioned; three corrections made by measurement before freezing. |

## 0 · Three corrections this gate makes before freezing

**F8's export assertion would pin a bundler artifact, not a fact about the module.** Measured: under
Vite/vitest, `Object.keys(namespace)` returns **declaration order** —
`['countCodePoints', 'ADVISORY_MINIMUM_CODE_POINTS', 'ADVISORY_MAXIMUM_CODE_POINTS']`, exactly as F8 writes
it. But a *real* ES module namespace exotic object has **sorted** own keys, so in any runtime that does not
go through Vite's transform the same assertion fails. An ordered `toEqual` here would pass today for the
wrong reason and break on a runner change that has nothing to do with R1. **Frozen: compare the key set
sorted**, with a comment saying why the order is not the claim.

**F9 offers three control shapes and only one of them measures anything.** "Assert the `codePoints` column
is not empty, contains a value other than 0, and the maximum is 128" is arbitrary — a table of ASCII rows
satisfies every clause of it and says nothing about code points. **Only the source-scan form is frozen**
(the suite must not compute an expectation with `[...s]`, `Array.from(...)`, or the function under test),
and it is joined by the table control in §2, which is the one this package keeps having to learn.

**F5 declines a case that measurement says is worth having.** It calls a split surrogate pair "not needed;
the function takes one string". Measured: `'\uD83D'` is 1 code point, `'\uDC4D'` is 1 code point, and
`'\uD83D' + '\uDC4D'` is **1** code point and 2 units. So `count(a) + count(b) ≠ count(a + b)` — the count
is **not additive across a concatenation**, which is a real hazard for any future implementation that
counts incrementally (a chunked paste, a streamed value) and is not implied by any other row. **Frozen as a
case**, with that arithmetic in the comment.

## 1 · Disposition

| F | severity | disposition |
|---|---|---|
| **F1** `.test.ts`, not `.tsx` | Minor/Major | **ACCEPTED.** `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts`. Verified: `.test.ts` files already run in this package (`api/`, `auth/`, `pwa/`). This is the package's **first suite with no environment to arrange at all** — the `pkce` assertions in `authorizeRequestsExactlyTheDeclaredScopes.test.ts` still stub `crypto` — and §2 records what that costs. |
| **F2** literal expectations | Blocker/Major | **ACCEPTED.** A `CASES` table with `name`, `input`, `codePoints` **and `utf16Units`** — both numbers as literals, because the disagreement is the claim and stating only one of them halves it. |
| **F3** the two bound-crossings | Blocker/Major | **ACCEPTED**, with the arithmetic frozen: 11 astral = 11 code points / **22** units (`.length` would accept what R1 excludes); 128 astral = 128 / **256** (`.length` would reject what R1 allows). |
| **F4** grapheme clusters | Major | **ACCEPTED.** `'👍🏽'` → 2, `'🇬🇧'` → 2, `'👩‍👩‍👧'` → 5. All three measured on this host. The comment must say this pins **the unit R1 chose, not the unit a visitor would guess** — and that T45's copy deliberately says "characters" to the visitor for that reason. |
| **F5** totality | Major | **ACCEPTED, and extended** — see §0's third correction. |
| **F6** purity at the source level | Major/Blocker | **ACCEPTED.** A scan of `passwordLength.ts` with comments stripped, reaching for no environment API, **with a control proving the scan can see one** — a negative scan with no control is the shape this package has been bitten by twice. |
| **F7** normalisation pinned, not decided | Major | **ACCEPTED.** Composed `'é'` → 1, decomposed `'é'` → 2, both as literals, with OQ-T46-2 named in the comment. Measured: `.normalize()` would make the second **1**, so this row is what a normalising mutant dies on. |
| **F8** export shape and constants | Minor/Major | **ACCEPTED with the §0 correction** (sorted set, not order). On **OQ-T46-1**: the constants are pinned **here**, in the module's own suite, and the three incidental pins in the screen suites stay where they are. They are not duplicates of this one — they assert the screen reads the right numbers; this asserts the module publishes them. |
| **F9** the exactness control | Major | **ACCEPTED in its source-scan form only** — see §0. |
| **F10** harness rows | Major | **ACCEPTED**, frozen in §3, with two of the proposed rows re-aimed: "change a constant value" is killed by cases in **three other suites**, so it is predicted `inherited`, and "export an extra name" must add a name the consumers do not use, or it is not the claim. |
| **F11** `Array.from` survives | Minor | **ACCEPTED, and it is the most interesting row in the harness.** It is not a defect that it survives: it is the proof that this suite pins the **unit**, not the **syntax**. Classified `unfalsifiable`, scored apart, and its comment must say that a suite which killed it would be over-fitted to the implementation it is meant to outlive. |
| **F12** no production change | Major | **ACCEPTED, and now a measured claim rather than an intention** — see §4. |

## 2 · What a suite with nothing to arrange cannot borrow, and what replaces it

Every instrument this package relies on assumes an environment: a query that might look in the wrong place
(so it carries a control), a render that might not happen, a global that might be stubbed. `countCodePoints`
has none. Two consequences, frozen:

1. **The controls must be about the TABLE and the SOURCE, not about a query.** There is no query to
   control.
2. **A table of rows that all agree in both units measures nothing about the unit.** This is T44's table
   control and T45's, learnt twice, and it is the single most likely way this task ships a suite that looks
   thorough and proves nothing. **Frozen control:** the suite asserts that its table contains at least one
   row where `codePoints !== utf16Units`, at least one row **below** `ADVISORY_MINIMUM_CODE_POINTS` whose
   unit count is **at or above** it, and at least one row **at or below** `ADVISORY_MAXIMUM_CODE_POINTS`
   whose unit count is **above** it. The second and third clauses are the two crossings, stated as
   properties of the table rather than as two cases that could be deleted.

## 3 · Frozen mutant list

| mutant | predicted |
|---|---|
| `[...value]` → `value` (the function becomes `value.length`) | **own** — every astral row, both crossings |
| `[...value].length` → `Array.from(value).length` | **unfalsifiable** — identical behaviour, and killing it would mean the suite pins syntax (F11) |
| `return 0` | own — every non-empty row |
| `return value.normalize().length` | own — **the decomposed-accent row only**, which is what makes OQ-T46-2's pin load-bearing |
| throw on `''` | own — the totality case |
| a fourth export added that no consumer uses | own — the export-shape case |
| `ADVISORY_MINIMUM_CODE_POINTS` changed | **inherited** — three screen-suite cases already pin it; this task's case dies too, so the row is `own` only if attribution names this file. Predicted `own` **and** inherited kills elsewhere. |
| *(canary)* a row claiming an astral string's count equals its unit count | the suite itself |

**The row this task is built around is the first one, and the second one is how the suite proves it is not
cheating.** `value.length` must die in many rows; `Array.from` must survive in all of them.

## 4 · Files

**Create:** the named suite, `frontend/src/features/account/passwordLengthCountsCodePoints.test.ts` · the
harness.

**Modify: none — and this is measured, not assumed.** Every file-enumerating instrument in the package
(`readdir` in 16 suites) filters with `!/\.test\.tsx?$/`, and no pinned count or list includes test files:
`RENDER_SITES` (8) and `PRIMITIVES` (13) are production files, `PERMITTED_CREDENTIAL_HITS` (3) scans
`productionSources()` only, and the catalogue key-set pin is about copy. A new test file is **invisible** to
all of them. Tasks 43, 44 and 45 each discovered an instrument that had to grow; this task should not, and
if one does, that is a finding worth the same attention.

**Do not modify:** `passwordLength.ts` · `RegisterScreen.tsx` · `catalogue.ts` · `PasswordField.tsx` · the
three consuming suites · `spec/**` · `contracts/**`.

## 5 · Acceptance Criteria (final)

AC1–AC7 stand as the phase 2 brief states them, with **AC3 extended by §2's table control** and **AC4
extended by §0's non-additivity case**.

## 6 · Open Questions

**No blockers.**

- **OQ-T46-1** — **answered** by F8's disposition: the constants are pinned here; the screen-side pins stay.
- **OQ-T46-2** — **recorded for the owner, not decided.** The behaviour (no normalisation) is pinned as it
  stands, and the mutant that normalises dies on one row. The question of whether R1 *means* to be
  normalisation-sensitive — a composed and a decomposed `é` are the same password to a visitor and count 1
  and 2 here — is the owner's.
- **OQ-T46-3** — **answered by §2 and §3.** A function with no branches offers one real regression and one
  equivalence; the suite's strength therefore lives in its **table** and its **controls**, not in the number
  of rows the harness can kill.

**FROZEN.** Phase 5 plans against this document and nothing else.
