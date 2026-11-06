<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T36 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

The frozen brief audited **clause by clause**, as T36's own phase 10 note requires — not re-read for a general
impression. Two gaps found, both closed. Neither was a hole in the behaviour; **both were holes in what a
failure would tell the person reading it**, which is a distinct kind of defect and one this phase has now
found twice.

## Score

```
regressions: 30/30 killed      (28 → 30)
survivors:   none
broken:      none
exit:        0
```

| | |
|---|---|
| own suite | **81 passed (81)** — was 79 |
| whole suite | **1659 passed / 1 failed of 1660** — the known `oxlint` host case only |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

---

## G1 — AC4 was passable by a URL built with string concatenation

**The clause:** *"`post_logout_redirect_uri` is `${origin}${de-slashed basename}/signed-out`, derived from the
route table."*

Four cases asserted that value, and **every one of them read it through `searchParams.get()`, which decodes.**
A module that built the query by concatenation — leaving the target's `://` and `/` raw — returns the correct
string from all four and passes. What SAS then receives depends on its query parser rather than on this
module, and a bare `:` and three `/` in a query value is not something to leave to a parser.

Closed by asserting the **raw** query, in both directions: it contains
`https%3A%2F%2Fchecky.example%2Fapp%2Fsigned-out`, it does **not** contain `=https://`, and the value still
round-trips through `searchParams.get()` so the encoding is correct rather than merely present.

**ES30** is the mutant. It concatenates the query by hand and dies on this case alone.

## G2 — AC1's ordering was pinned by behaviour but not by cause

**The clause:** *"`signOut()` completes before the hand-over."*

`endSession()` takes the ID token, then calls `signOut()`, then hands over. Reversing the first two lines makes
`hint` null, so the parameter is absent and **AC5 already fails** — the bug was caught.

What was not caught is *what a reader would conclude*. A failing AC5 reads "the hint is missing", which sends
the next person to `takeIdToken()` or to `idToken.ts`. The cause is two lines in the wrong order in
`endSession.ts`. A source-order case names it, so the failure report points at the right file.

This is the same category as G1 and as phase 7's S1: the suite was *correct* and the diagnosis it offered was
*wrong*. A test that fails for the right reason but describes the wrong cause costs a reader the time the
test was supposed to save.

**ES29** is the mutant — and it taught something about the harness, below.

---

## What the audit confirmed rather than changed

Named here because a clause-by-clause audit that only reports its finds is not a completeness claim:

| Clause | Already held by |
|---|---|
| AC6 "exactly two parameters" — including a **duplicate** of one | `[...keys()].sort()` compares against a two-element array, and `keys()` yields duplicates, so a doubled `id_token_hint` fails |
| AC11 "no storage key" | **task 22's** `sessionStorageHoldsOnlyTheL16Items` pins L16's carve-out at four and is green — the right suite owns it, and T36 does not restate it |
| AC11 "no route" | AC4's `PUBLIC_ROUTE_PATHS` check proves the target is a route that **already existed** |
| AC8 "exactly once" | `raw.split(identity)` length, which counts occurrences rather than asserting presence |
| AC12 "absent, not empty" | `has(...)` is `false` **and** `url.search` does not contain the parameter name |
| AC16's narrowness | A denylist broadened to `/connect/` would fail this case — but T10's own tests already pin that list verbatim against `agents.md`, so T36's case agrees with the owning suite rather than adding a second opinion |

## ES29 found a defect in the harness, twice over

**First: the mutant was wrong.** It came back `BROKEN  anchor missed`. As two edits it replaced
`const hint = takeIdToken();` with `  signOut();` *first*, which left the file holding **two** copies of that
line — so the second edit's anchor matched twice and the apply refused.

T33's NR03 established that *a reorder is not a deletion*. This is the other half: **a reorder of adjacent
lines is not two independent edits either.** It is one edit over the block. Re-anchored that way: `own`, 8
failures.

**Second, and worse: the tally counted that broken mutant as a kill.** The run printed

```
regressions: 30/30 killed
broken:      ['ES29 ...']
```

`len(scored) - survivors` never subtracted the broken rows, so a mutant that **never ran** inflated the one
number a reader quotes. Fixed in T36's copy: broken rows leave the numerator and the line says so
explicitly — `29/30 killed  (1 BROKEN, not counted as killed)`. **The four earlier copies have the same
arithmetic** (T32, T33, T35, and the one T31 derived from); not fixed there, because those tasks are closed.
Recorded as **FU-T36-3**.

A mutation score that silently counts a mutant that did not execute is the exact failure the harness's own
docstring claims it prevents. It was visible only because `broken:` prints separately — which is an argument
for printing both lines rather than a single score, and against trusting a score whose denominator is not
shown.

## A correction to phase 10's own first reading

I reported this run as *"exited with code 0 with a BROKEN mutant, contradicting its own docstring"* and
treated it as a third defect. **It was not one.** I had piped the harness through `tail`, so the status I read
was `tail`'s, not the harness's. `sys.exit(main())` does return 1 when anything is broken or survives — and
the re-run, with the exit code captured directly, reports `HARNESS_EXIT=0` on a clean 30/30 as it should.

The lesson is about measurement, not about the harness: **a pipeline's exit status is the last command's**,
so a harness whose verdict is its exit code must not be read through a pipe.

## New mutants

| | |
|---|---|
| **ES29** | sign-out runs before the ID token is taken, so the hint is always null — `own`, 8 failures |
| **ES30** | the query is concatenated by hand, leaving the post-logout target unencoded — `own`, 2 failures |

## Follow-ups

- **FU-T36-3** — the broken-counts-as-killed arithmetic in the four earlier harness copies.
- **FU-T36-1**, **FU-T36-2** — carried from phase 9, unchanged.
- **OQ-T36-5** — still nothing calls `endSession()`. The import scan added in phase 9 is now also the handoff.
- **OQ-T35-3** — still task 39's, and still made *producible* by T36.

**Phase 10 complete.** Next is **phase 11 — Kimi 2.7's adversarial test review.**
