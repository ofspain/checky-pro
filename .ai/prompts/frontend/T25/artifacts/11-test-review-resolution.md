# frontend · T25 · Phase 11 — Test Review Resolution

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/11-test-review.md` (Kimi 2.7) |
| **Produces** | `artifacts/11-test-review-resolution.md` |

Every claim checked against source. **All seven gaps accepted**, one of them (**Gap 2**) accepted *only* in its
alternative form, because its premise was already tested and rejected at the Phase 9 gate and is still false.

**Gap 1 is the one that matters beyond this task**, and it has been true since T14.

**Final state.** Named test **184 cases** (109 → 117 for T25). Suite **1192 passed / 1 failed** by exit code, the
failure being the known environmental case. `tsc --noEmit` exit 0. **Mutation: 25 of 25 killed, no survivors, now
run from the committed script.**

---

## Resolution log

### Gap 1 · The mutation harness was not in the repository — **ACCEPTED, and it was true of every task since T14**

Verified: the script lived only in the session scratchpad. Kimi's point is exactly right and larger than T25 — the
mutation results have been *reportable* but not *reproducible* for twelve tasks. A manifest that no one else can
re-run is a claim, not evidence.

**Change made:** committed as `.ai/prompts/frontend/T25/artifacts/10-mutation-harness.py`, and made genuinely
runnable rather than merely archived:

- `ROOT` is derived from the script's own location (`parents[5] / "frontend"`), so it works from a fresh clone
  instead of hard-coding this machine's path;
- the header carries **the danger in capitals**: it edits production source and reverts with
  `git checkout -- src/auth/`, so uncommitted work in that directory is destroyed silently — which has cost 19
  test cases in T14 and 5 in T15;
- it documents *why the mutant list looks like it does* (M1–M3 are the three react-router would honour), how to
  read the two-column output, and that **BROKEN is not KILLED**;
- and it records that **M8 survived the first run and is the reason the no-leading-slash families exist** — so if
  M8 ever survives again, a family has been deleted.

**Verified safe from regeneration:** `.ai/generate.py` only ever writes (`mkdir(exist_ok=True)` plus
`write_text`), never deletes, so a regeneration will not clobber it. **Confirmed by running it from the repository
root:** `python3 -I .ai/prompts/frontend/T25/artifacts/10-mutation-harness.py` → 25 killed, **no survivors**.

### Gap 2 · `routerOutcomeFor` imports `../app/routes` by name — **premise REJECTED again; alternative ACCEPTED**

This is Kimi I8 re-raised in identical terms. It was rejected at the Phase 9 gate **with a measurement**, and the
measurement still holds: `await import('../app/routes')` returns a **typed** module namespace, so renaming
`appRoutes` produces

```
TS2339: Property 'appRoutes' does not exist on type 'typeof import(".../src/app/routes")'
```

at `routerOutcomeFor` **and** at T24's `arriveAt`, which has used this pattern since T16. It cannot "fail at
runtime while the test file still compiles", and `tsc --noEmit` runs in every phase and is an acceptance criterion.
The suggested compile-time guard would be a test asserting that TypeScript is TypeScript.

**But the third of Kimi's three alternatives is the good one, and it is accepted:** a case asserting
`routerOutcomeFor('/home')` reaches `HOME_ROUTE`. Not for the reason offered — a broken dynamic import would
reject and fail loudly anyway — but for a better one that neither review named:

> Every other use of the instrument expects a **throw**, a **catch-all**, or a real route the allowlist then
> refuses. **Nothing proved it could report a successful honoured navigation at all.** An instrument that silently
> reported nothing would make all 24 counterfactuals read as refusals — and every one of them would still pass.

**Change made:** `it('reports a real navigation too, so a broken instrument cannot read as a refusal')`, over
`/home` and `/account/sessions`, asserting pathname *and* matched route. The rejected half of the finding is
recorded in a comment beside it so it is not raised a third time.

### Gap 3 · No single-segment leading-whitespace case end to end — **ACCEPTED**

Verified: the end-to-end whitespace case used `  /account/sessions` only.

**Change made:** the case is now an `it.each` over three shapes — `  /account/sessions`, `  /home`, and
`/home  ` (trailing, which Kimi did not ask for and which is the other half of the same property).

### Gap 4 · No case for `./home` — **ACCEPTED**

Verified: the table had `home` and `account/sessions` but not the explicitly relative form.

**Change made:** a 24th family row, `./home`, classified `miss` — the router resolves it against the current route
exactly as it does a bare `home`, so it lands on the catch-all. The full battery applies: router control, flow
row, producer row, and the unreachable-store row.

### Gap 5 · The counterfactual's claim could be read as the screen's — **ACCEPTED**

Verified: Phase 9 narrowed `routerOutcomeFor`'s own doc comment (I4), but the `describe` title and the
`routerWould` field name still read loosely. Kimi's cited line 1349 is now a different line after Phase 10's
additions; the substance of the observation is correct.

**Change made:** the block is now `what the BARE ROUTER would have done…`, and `RouterWould` carries a doc comment
saying the two instruments are guaranteed to agree **only for absolute router paths** — and pointing out that this
is *why* the `./home` and no-leading-slash rows are `miss`.

### Gap 6 · No standalone producer case for T24-D8's contract — **ACCEPTED**

Verified: the `is not even storable` rows do cover both no-leading-slash shapes, as Kimi acknowledges — but they
cover them as two refusals among twenty-four.

**Change made:** `it('stores a path with a leading slash and refuses the same path without one (T24-D8)')` — the
contract as a round trip, in the direction a caller reads it: the bare path stores nothing, the slashed path
stores, and `takeReturnTarget()` returns it.

### Gap 7 · The completeness pin's comment says how but not why 24 — **ACCEPTED**

Verified at the pin. **Change made:** the comment now lists **all ten additions** beyond the fourteen the `it.todo`
list named, each with its provenance — F4's two trailing-slash rows, T25-D4's case row, F4's encoded separator,
the leading-whitespace row, M8's two no-leading-slash rows, Gap 4's relative row, I7's query-and-fragment
combination, and F7's mixed row. A maintainer no longer has to reverse-engineer the number from the table.

---

## What changed

| File | Change | Gap |
|---|---|---|
| `.ai/prompts/frontend/T25/artifacts/10-mutation-harness.py` | **new** — the harness, path-independent, with the warning and the reasoning in its header | **1** |
| `auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | the instrument's positive control | **2** |
| | whitespace case widened to three shapes | **3** |
| | `./home` as a 24th family | **4** |
| | `BARE ROUTER` in the block name; `RouterWould` documented | **5** |
| | T24-D8's contract as a round trip | **6** |
| | the pin's comment lists all ten additions with provenance | **7** |

**No production code changed.** T25-D5's condition never triggered across the whole task. `spec/` untouched.

## The table, final

**24 families: 7 `refuse`, 9 `reach`, 8 `miss`.** The `reach` nine are where the allowlist is the **sole**
defence — the router would land on a real route — and they are the reason this task is not paperwork.

---

## Next — Phase 12 — specification verification.
