<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T45 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Consumes** | every prior artifact; `spec/frontend/requirements.md`, `design.md`, `tasks.md` |
| **Produces** | `artifacts/12-specification-verification.md`, `artifacts/12-mutation-run.txt` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run …registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | **19 / 19 passed** |
| `vitest run` | **1964 / 1965** — the one known environmental failure |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| `python3 -I …/07-mutation-harness.py` | **44/44 regressions killed**, 3 canaries killed, **1 expected survivor** (TR24), **0 broken**, **0 disagreements** |

**Four harness runs across phases 9, 10 and 12, and every row's verdict is identical in all four.** This
run's verdicts and attributions match phase 10's row for row (`diff` over verdict, class and row id: empty).
Phase 9's second run differed only in TR27's failure *count*, never its verdict.

## Traceability matrix

Line numbers are against commit `ceb53df4`, the tree this phase verified.

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R1** the check is **advisory guidance**, and the guidance is now rendered | **yes — this task is that clause** | `RegisterScreen.tsx:103` renders the sentence on `advisory === 'out-of-range'`; `:70` is the unchanged check | `…RejectsPasswords…test.tsx:211` (5 rows) | — | — |
| **R1** 12 **code points**, not UTF-16 units, at the lower bound | yes (T43's, unchanged) | `RegisterScreen.tsx:70` via `countCodePoints` | `:211` row *11 astral code points, which are 22 UTF-16 units* — **the row that discriminates**, added in phase 10 | — | — |
| **R1** 128 code points at the upper bound | yes (T44's, unchanged) | `RegisterScreen.tsx:70` | `:261` rows *the upper bound itself*, *128 astral…* | — | — |
| **R1** the request goes **either way** | yes, untouched | `RegisterScreen.tsx:73-74` — the advisory result is not consulted | `:211` asserts one request, body unaltered, nothing disabled, on every out-of-range row | — | — |
| **R1** the server stays the authority | yes | no branch on the response; the copy delivers no verdict | `:490` + its control at `:506` | — | — |
| **R2** one acknowledgement, no second path | yes — untouched | the guidance lives on the form, not on the result | T43's AC11 rows, still green | — | — |
| **L11** every word from the catalogue | yes | `catalogue.ts:109`; `RegisterScreen.tsx:81` | `:460` scans the screen's source for the key **and** for the two constants | — | — |
| **L11** programmatic association | yes | `RegisterScreen.tsx:96` + `PasswordField.tsx:55,57,72` | `:300` walks `aria-describedby` → element; `:341` the way back | — | — |
| **L11** reading order matches the visual one | yes | `RegisterScreen.tsx:103`, between field and control | `:300`, `compareDocumentPosition` (added phase 9) | — | — |
| **L12** additive | yes | the `advisory` state machine is unchanged; one state gained a sentence | the whole inherited suite, green | — | — |
| **L4** no branch on the response, nothing about the address | yes | the guidance depends on `advisory` only | `:300` asserts the email field is neither described nor marked | — | — |
| **L6** generated client only | yes, untouched | `RegisterScreen.tsx:73` | T43's `:459` *builds no URL and calls no transport of its own* | — | — |
| **L2 / L3 / L14** no credential surface, no storage, no route | yes | nothing added | T43's AC8/AC10 cases, green | — | — |
| **T173 rule 1** no `className`/`style` on a primitive | yes | `PasswordField` gained only `'aria-describedby'?: string` | the design-system prop-type pins, green | — | — |
| **T173 rule 2** a primitive may take a state the consumer computes | yes | `PasswordField.tsx:55` — a relationship the screen owns; `ActionButton.tsx:22` is the precedent | AC4 end to end | — | — |
| **T173 rule 3** no primitive carries copy | yes | the sentence is passed in by the screen | `:460` | — | — |
| **T44 AC4** no primitive carries `maxLength`/`minLength` | yes, untouched | — | the design-system scan over all primitives, + TR32/TR35 | — | — |
| **package.md §8** the named test exists | yes | `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | 19 cases | — | — |
| **tasks.md 45** "11 or 129 code points shows the advisory guidance" | yes | — | `:211`, both ends plus astral and empty | — | — |

### Acceptance criteria

| AC | held by | verdict |
|---|---|---|
| **AC1** 11 → guidance | `:211` (3 of 5 rows below the minimum, one of them astral) | ✔ |
| **AC2** 129 → **the same** guidance | `:211` (2 rows above) + the table control at `:240` | ✔ |
| **AC3** in range → nothing, with a control | `:261` (3 rows) + `:279` | ✔ |
| **AC4** programmatic association | `:300`, `:341` | ✔ |
| **AC5** nothing before the first submit | `:370` (3 and 200 characters) **and** `:390`, the other edge | ✔ |
| **AC6** still sent, nothing disabled | asserted in every row of `:211` | ✔ |
| **AC7** catalogue, no spelling, reachable, names the bounds, no verdict | `:432`, `:460`, `:490`, `:506`, + the reachable-keys line in T43's suite | ✔ |

## The four questions

**1 · Is the task fully complete?** Yes. The task statement's two passwords (11 and 129 code points) each
show the guidance, in a named suite that did not exist before, and the clause R1 has carried since the
package was written — *"advisory guidance only"* — is on screen for the first time. Before this task a
visitor who typed eleven characters saw an outlined box and a status line saying their account was being
created.

**2 · Does it satisfy every acceptance criterion?** Yes, and two of the seven are stronger than they were
when the brief froze. AC1 gained the row where counting the wrong unit **withholds** the sentence (11 astral
code points are 22 UTF-16 units), which is the only direction of the counting bug that fails silently. AC5
gained its other edge: the brief asked only that nothing appear before the first submit, and the state
machine also has a way **back** and a way to **stay**, neither of which anything measured.

**3 · Does it violate any LOCKED decision?** No. The one decision in genuine doubt at the freeze was T173's
rule 2 — whether a field primitive may accept `aria-describedby`. It may: the rule forbids a primitive
taking the attribute that *is* its contract, and permits a state or relationship the consumer computes.
`ActionButton` has accepted this exact prop since task 16 and `Text` carries `id` "for one reason:
`aria-describedby`". What rule 2 keeps inside `PasswordField` — `type`, and the `htmlFor`/`id` pair — is
still inside it.

**4 · Remaining risks.**

| risk | where it stands |
|---|---|
| **The sentence is described, not announced** | D-T45-1's accepted residual risk. A screen-reader user who submits and does not return to the field may not hear the guidance, because the live region is immediately filled with "Creating your account…". The association is correct and measured; the announcement is a product decision this task was told not to make, and OQ-T43-10 (no focus move on submit) is where it would be revisited. |
| **"characters", not "code points"** | Deliberate (phase 8's I8). The sentence understates the allowance for a grapheme cluster built from several code points. The server resolves the difference. |
| **OQ-T45-5** | Should the guidance clear when the visitor edits the password? Today it stays until the next submit, matching what T43 chose for the mark. Pinned by `:390`, with TR48 as the mutant that becomes the fix if the answer is no. |
| **OQ-T45-4** | AC4's email-field exemption is enforced by the type system rather than a mutant: `EmailField` accepts neither `aria-describedby` nor `aria-invalid`, so the mutant would not compile. The first task that gives the address a message turns that assertion into a real claim needing a real mutant. |
| **FU-T42-1** | Five sightings. Phase 9 measured the mechanism: the flake can only invert a row with **no genuine kill of its own**, which is why TR24 is the row that flips and TR27 merely gained a failure. Not this task's to fix; the mitigation is two runs and reading failing case *names*. |
| **FU-T43-2** | Three sightings. ~120 lines of helpers copied again, and the copies have begun to diverge. Owner's call. |
| **OQ-T44-1** | Unchanged: the length-cap scan forbids on `EmailField` an attribute the contract caps at 254. |

## What this task changed in production

| file | change |
|---|---|
| `i18n/catalogue.ts:109` | one key — **the first interpolated string in the application** |
| `app/ui/PasswordField.tsx:55,57,72` | one prop, forwarded to the `<input>` unchanged |
| `features/account/RegisterScreen.tsx:59,81-84,96,103` | an id constant, the interpolated lookup, and one condition driving both the mark and the sentence |

Three files, and no behaviour removed from any of them. `passwordLength.ts`, `routes.tsx`, `api/**`,
`contracts/**` and `spec/**` are untouched, as the freeze required.

## What the four reviews cost, and what they bought

Thirty-eight findings across phases 7, 8 and 11. Three of them changed what this task ships:

1. **The screen end of the provenance wire** (phase 7 F1 / phase 8 I1). The freeze named provenance as the
   thing only one assertion could protect, and located it at the catalogue. It has two ends. A screen
   interpolating the literals `12` and `128` renders a byte-identical sentence from the real template and
   satisfies the digit identity by arithmetic — it passed **126 of 126** cases. Found by applying the
   mutant rather than reasoning about it.
2. **The end where the counting bug is silent** (phase 10). Every out-of-range row was out of range on both
   counts, so none of them could tell which unit the screen compared. At the lower end it inverts, and the
   failure is **silence** rather than a wrong word.
3. **A criterion that was prose** (phase 7 F7 / phase 8 I4). The screen's own comment claimed a reading
   order; the suite was green with the sentence after the submit button.

And three comments that said something false were corrected rather than left: the bounds case crediting the
wrong half of itself, the 129-astral row claiming to discriminate what it cannot, and — in the other
direction — a phase 11 finding whose first citation had been fixed two phases before it was written.

## Verdict

**PASS** — R1's advisory clause is rendered, associated and measured at both bounds in both units, with
every acceptance criterion held by a case and every case held by a mutant.
