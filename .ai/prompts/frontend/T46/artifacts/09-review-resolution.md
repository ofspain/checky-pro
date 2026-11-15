<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T46 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T46 — Code points, not UTF-16 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md`, `artifacts/09-mutation-run.txt` |

## Resolution log

Ten findings from phase 7, eleven from phase 8, overlapping almost exactly: I1/F4, I2/F5, I4/F1+F2, I5/F3,
I6/F7, I7/F8, I9, I10/F10 are the same findings read twice. Phase 8 added one of its own (**I3**) and one
observation (**I11**).

| # | finding | decision | change |
|---|---|---|---|
| 1 | **F4 / I1** — the AC6 control uses a count, not a category | **ACCEPTED** | `ENVIRONMENT` is now labelled by category; the control asserts the **set** |
| 2 | **F5 / I2** — a comment claims a performance property nothing measures | **ACCEPTED** | reworded to what the line measures |
| 3 | **I3** — the table-region regex is fragile | **ACCEPTED in part, and the other part DECLINED with evidence** | the guard counts rows; the file-wide scan is refused — see §3 |
| 4 | **F3 / I5** — the revert is not crash-safe | **ACCEPTED** | a startup guard that refuses a dirty tree, and a `finally` |
| 5 | **F1 / F2 / I4** — stale predictions, and a check that shared the fix's bug | **ALREADY DONE in phase 7; verified here and independently** | none |
| 6 | **F10 / I10** — `typeof` is held by the type system | **ACCEPTED (comment)** | one sentence saying which it is |
| 7 | **I9** — the table control is coupled to the constants | **ACCEPTED (comment)** | one sentence saying the coupling is deliberate |
| 8 | **F6 / F7 / F8 / I6 / I7 / I8** | positive | none |
| 9 | **I11** — the harness is heavyweight for a pure function | **DEFERRED** | none — see §5 |

---

### 1 · F4 / I1 — the control asserted a number where the claim was a set · **ACCEPTED**

It asserted `fired.length >= 5` against a synthetic impure module, and exactly five of the twelve patterns
*can* match that sample. So the threshold was satisfied with no margin — sensitive today, and silently
blunted by the next line anyone adds to the sample. `ENVIRONMENT` now carries a `category` per pattern and
the control asserts the sorted set:

```ts
expect(fired, 'the scan is blind to a whole kind of reach')
  .toEqual(['clock', 'console', 'dom', 'import', 'storage']);
```

A broken pattern now fails here regardless of what else the sample contains.

### 2 · F5 / I2 — a comment stronger than its case · **ACCEPTED**

*"A LONG VALUE COSTS ONE PASS"* sat over an assertion that counts no passes and measures no time. The
comment now says what the line does establish — a 100,000-character value **answers**, with the right
number, which is what matters for an advisory check that must not become a gate — and attributes the
complexity claim to the implementation rather than to the test.

### 3 · I3 — the fragile region regex · **ACCEPTED in part; the proposed alternative DECLINED**

**Accepted:** the guard no longer asks whether the region exists, it asks whether the region holds **every
row**. And it earned its keep on its first run by reporting **12 of 15** — which was not a short extraction
but my own counter: it matched `{ name:` and four rows wrap onto several lines, so their `name:` does not
follow a brace. Counting `name:` alone gives **16**, the extra one being the **type annotation**, which also
says `name:`. The guard now counts `/utf16Units: \d/`, a field followed by a digit, which can only be a row.

A guard that could only say "found" or "not found" would have reported neither of these things.

**Declined, with evidence:** I3 also proposed scanning the whole file for `[...` and whitelisting. That
cannot work here — `passwordLengthCountsCodePoints.test.ts`'s export-shape case legitimately writes
`[...Object.keys(passwordLength)].sort()`, so a file-wide ban fails on the suite's own honest code. The
region is the right scope; it just has to be provably the whole region, which is what the row count does.

### 4 · F3 / I5 — the revert is not crash-safe · **ACCEPTED**

Two changes to this task's own harness:

**A startup guard.** `main()` now refuses when `git status --porcelain` over `REVERT_PATHS` is non-empty, and
prints the offending paths. It caught my own uncommitted edits the first time I invoked it, which is exactly
the behaviour wanted. This closes **both** halves of the hazard:

- A **leftover mutant** from a killed run. The next run's baseline guard would catch the one this task
  actually suffered (`ADVISORY_MINIMUM_CODE_POINTS = 11`) only *because* it breaks tests; a leftover from
  TR24 or TR50 changes nothing observable, would pass every guard, and would be committed by the next
  `git add` of that path.
- **Uncommitted test edits.** `main()`'s first act was `revert()` — `git checkout --` plus `git clean -fq` —
  which destroys uncommitted work under those paths without asking. That cost 19 cases in T14 and 5 in T15,
  and has been a standing rule ever since. A rule a tool can enforce should not stay a rule people remember.

**A `finally`.** The per-row revert now runs in `finally`, covering an exception and a Ctrl-C. It does **not**
cover a SIGKILL, because nothing can, and the comment says so instead of implying more — the startup guard
is what covers that, on the next run.

### 5 · I11 — the harness is heavyweight for a pure function · **DEFERRED**

True, and the measured shape of it: 55 rows, of which **8 plus a canary** are this task's and 46 are
inherited regression coverage for screens. The inherited rows are not waste — TR05's attribution is this
task's central evidence and TR47's is task 45's — but a harness copied sixteen times is tracked as
**OQ-T32-4** and trimming it is not a measurement task's business.

### 6 · One correction to phase 8's summary

It credits *"Phase 10"* with adding the table control, the circularity guard and the non-additivity case.
T46 has no phase 10 yet; all three were written in **phase 6**, and the non-additivity case exists because
phase 3's F5 called it unnecessary and the freeze measured otherwise (`1 + 1 = 1` for a split surrogate
pair). Recorded because a review's provenance claims get read later as fact.

---

## Verification after the accepted changes

| | |
|---|---|
| named suite | **25 / 25** |
| whole suite | **1989 / 1990** — the known environmental lint-script failure |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| harness | **49/49 regressions killed**, 4 canaries killed, 2 expected survivors (TR24, TR50), **0 broken**, **0 disagreements** |

`artifacts/09-mutation-run.txt`. Verdicts are **identical row for row** to phase 7's run 2 (`diff` over
verdict, class and row id: empty), so the suite changes above moved no mutant's fate — which is what a
control-hardening change should do.

**Three runs of mine and one of Kimi's**, all with 49/49 and both expected survivors; the only thing that
ever changed between them was the prediction metadata, now settled. The guard means a fourth run cannot
start on a tree somebody's crash has edited.

## Open items carried forward

| | |
|---|---|
| **OQ-T46-2** | Is R1 meant to be normalisation-sensitive? A composed `é` counts 1 and a decomposed one 2, so the same password typed on two keyboards is two lengths. Held by a test and by TR52; the answer is the owner's. |
| **OQ-T46-1** | Answered at the freeze: the constants are pinned in the module's own suite, and the three screen-side pins stay. |
| **OQ-T32-4** | Sixteen copies of the harness machinery. I11 is the newest sighting. |
| **FU-T42-1** | Quiet through all three of this task's runs, with the stack stopped. |
| **FU-T43-2** | Not engaged by this task: a pure-function suite needs no shared render helpers. |

**Phase 9 complete.** Seven findings accepted (five as code, two as comments), one declined with evidence,
one deferred, one review claim corrected.
