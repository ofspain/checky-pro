# frontend · T25 · Phase 10 — Test Generation

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

`returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — **176 cases**, of which **109 are T25's**. No production
code changed.

**Suite: 1184 passed / 1 failed** by exit code over two consecutive runs, the failure being the one known
environmental case (`lintRejectsHandWrittenBackendFetch.test.ts:118`). `tsc --noEmit` exit 0. 5.84s.

**Mutation: 25 mutants, 25 killed, no survivors — and every one of them is killed by T25's own file**, not by an
inherited T24 or T23 case. The harness reports both, because "the suite caught it" and "the section that owns this
behaviour caught it" are different claims and only the second one is this task's.

**One mutant survived the first run, and it found a family the table was missing.** That is recorded in §3 rather
than quietly fixed.

---

## 1 · What this phase added

| Requirement | From | Delivered |
|---|---|---|
| **R-I1** | Kimi I1 / self-review S1 | `describe('a refused target when the store will not cooperate')` — a throwing removal, a throwing read, and an unreachable store over **every** family |
| **R-I5** | Kimi I5 / S5 | `/account/sessions/` as a FAMILIES row and a near-miss pair |
| **R-I6** | Kimi I6 | AC20 parameterised over three `reach` shapes beside the original `refuse` one |
| **R-I7** | Kimi I7 | `/home?x=1#fragment` — a combination inside the `reach` category |
| Phase 5's deferrals | plan §"Phase 10" | the near-miss pairs as their own cases; the leading-whitespace row; the mutation harness |
| **New, from the harness** | M8 survived | the **no-leading-slash** family, one segment and two |

**FAMILIES is now 23 rows: 7 `refuse`, 9 `reach`, 7 `miss`.**

## 2 · Test manifest — T25's section

### `what the router would have done, measured first (AC16, T25-D1)`

| Case | Verifies |
|---|---|
| one case per family (23) | **AC16** — the harm, before the defence. 7 assert the throw; 9 assert a **named real route**; 7 assert the catch-all |
| `is three kinds of risk…` | the split by count — a readability aid, **not** the guard (Kimi I10) |

### `and every one of them lands on home instead (AC1-AC12, AC17-AC19, AC21)`

| Case | Verifies |
|---|---|
| one case per family (23) | **AC1** home · **AC3** the honoured destination reachable from the same harness · **AC17/AC4** indistinguishable from no target over four fields, **per row** · the sign-in **completed** (token held, so no row passes against a discard) · **AC5/AC7** the store is empty · **AC21** and emptied by a *removal*, never a `clear()` · **AC2** refused by the real exported predicate |
| `is not even storable: $label` (23) | **AC12/AC18** — the producer refuses it, with an allowlisted control |

### `the near misses, where membership is the only thing between them (AC19)`

| Case | Verifies |
|---|---|
| 8 pairs | each differs by the smallest edit the rule must notice: four characters appended, two removed, one trailing slash (one segment and two), case, two leading spaces, one leading slash (one segment and two) |
| `sends a whitespace-padded target home, where a trim would have honoured it` | the end-to-end witness a trim would break |

### `a refused target when the store will not cooperate (R-I1, AC15)`

| Case | Verifies |
|---|---|
| `still reaches home when the removal throws, and the refused value stays` | **T24-D9** on the **refused** branch |
| `treats a refused target it cannot read as no target at all` | **AC10** on the refused branch |
| `never throws for $label, even with the store unreachable` (23) | **AC15/T24-D10** as a property of the function, not of one flow |

### The rest

`leaves %j where it is when the callback is discarded` (4 shapes) — **AC20/AC6**, now including three `reach`
shapes · `covers every family the todo list named, and four more` — the completeness pin, with the instruction its
failure message cannot carry (Kimi I9).

## 3 · The mutation harness, and the survivor that mattered

25 mutants over `returnTarget.ts` and `CallbackScreen.tsx`; baseline **618 passed, exit 0** over the six auth test
files, **166 → 176** in the named file alone. Script:
`/private/tmp/claude-501/-Users-macbookpro-personal-crypto-dispute/d6c7daa6-f34e-40ce-afe5-23ce45bf781e/scratchpad/mutate-t25.py`.
Tests were committed and pushed before each run — it reverts with `git checkout`.

**The list was written from the Phase 4 measurements rather than generically.** M1–M3 are the three whose results
**react-router would honour**, so a table that does not kill them proves nothing about the shapes that matter most.

| Mutant | Killed by T25's own file | Failures |
|---|---|---|
| M1 membership folds case | yes | 4 |
| M2 membership strips a trailing slash | yes | 6 |
| M3 membership trims the target | yes | 9 |
| M4 prefix test (target starts with a member) | yes | 29 |
| M5 reverse prefix test | yes | 2 |
| M6 substring match | yes | 48 |
| M7 decodes the target first | yes | 2 |
| **M8 strips a leading slash from both sides** | **see below** | **0 → 10** |
| M9 strips the basename | yes | 4 |
| M10 ignores a query string | yes | 4 |
| M11 ignores a fragment | yes | 2 |
| M12 matches on the first segment only | yes | 23 |
| M13 the parameterised filter dropped, so `:id` paths join the allowlist | yes | 3 |
| M14 public routes join the allowlist | yes | 123 |
| M15 the stored value returned unvalidated | yes | 38 |
| M16 the fallback returns `null` | yes | 39 |
| M17 the fallback returns the sign-in route | yes | 39 |
| M18 a refused target is **not** consumed | yes | 37 |
| M19 the whole store is cleared instead of one removal | yes | 25 |
| M20 the removal is unguarded (T24-D9 gone) | yes | 1 |
| M21 the whole body is unguarded (T24-D10 gone) | yes | 26 |
| M22 the write-side filter dropped | yes | 24 |
| M23 the write-side filter inverted | yes | 78 |
| M24 the screen ignores the fallback and stays on the callback | yes | 58 |
| M25 the navigation pushes instead of replacing when falling back | yes | 58 |

### M8 · the survivor, and the family it found

**M8 survived the entire table on its first run — 0 failures of 608.** Stripping a leading slash from both sides
makes `home` a member, and **nothing in 21 families had a target without a leading slash.** Twenty-three families
of hostile and near-miss shapes, and the one missing was the most ordinary: a path spelled the way a caller would
spell it if they used `useLocation().pathname.slice(1)` or hand-edited the store.

What makes it more than a coverage gap is what the router does next. **T18 measured it**: a relative target
resolves against the **current** route, so `home` from the callback becomes `/app/callback/home` and lands on the
catch-all **silently**. That silence is precisely why T24-D8 fixed the stored form as a path *with* a leading
slash — and until now, nothing held the two halves of that decision together. The decision was recorded in a
comment; it is now a test.

**Two rows added** — one segment (`home`) and two (`account/sessions`), because a one-segment witness would be
satisfied by a rule that only mishandled single-segment paths — plus two near-miss pairs. **M8 now fails 10
cases**, and the harness reports **no survivors**.

## 4 · What this phase did not do

No production change — T25-D5's condition never triggered across the whole task. No normalisation, decoding,
pattern matching or prefix handling. No new file, dependency or catalogue string. No T24 assertion altered. No
claim that an unchanged `window.location` proves anything (T25-D6). `oidcRoundTripEndToEnd` remains unowned, so no
family is proven against a real browser.

---

## Next — Phase 11 — test review (Kimi 2.7).
