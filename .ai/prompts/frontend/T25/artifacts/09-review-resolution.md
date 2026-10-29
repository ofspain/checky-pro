# frontend · T25 · Phase 9 — Review Resolution

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi 2.7), `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

**Every cited line in the Phase 8 artifact was checked against source, and the one finding whose premise could be
tested was tested.** All six citations are exact (`:1238`, `:1256`, `:1284`, `:1338`, `:1353`, `:1381`). Seven of
the ten findings converge with the self-review: I1=S1, I2=S2, I3=S3, I4=S4, I5=S5, I9=S6, I10=S7.

**Nine accepted, one rejected.** I8 is rejected on a measurement, not a preference — its premise is false in this
language. Two accepted findings were applied here; four are carried to Phase 10 as new coverage; three are
recorded.

**The important outcome: the escaped mutant is closed.** `sessionStorage.clear()` in place of
`removeItem(RETURN_TARGET_KEY)` previously failed **2** of 124 cases, both of them T24's. It now fails **20**.

**Measured after the changes:** `tsc --noEmit` exit 0; named test 124 passed; suite **1132 passed / 1 failed** by
exit code, the failure being the known environmental case.

---

## Resolution log

### I1 · Throwing store and throwing removal are not exercised on the refused branch — **ACCEPTED, carried to Phase 10** (Medium)

Verified: `denyReadOf` and `denyRemovalOf` appear only in T24's cases (`:563`, `:890`), both on the honoured path.
Kimi and the self-review found this independently, and it is the one finding that is a **gap against the frozen
brief** rather than an improvement on it: Phase 2's Required Tests asked for it, Phase 4 §7 kept Phase 2's list in
full, and Phase 5 then split the authoring between Phase 6 and Phase 10 and named these rows in **neither**. That
is a phasing error of mine, recorded as such.

Kimi's reasoning for why it matters is right and sharper than my own: the refused branch returns `homeTarget()`
rather than `stored`, so it is a **different branch**, and it is the branch most likely to meet a store that
refuses the removal.

**Change made:** none here — these are new cases, which is Phase 10's scope. **Phase 10 requirement R-I1**: a
refused target under `denyRemovalOf(RETURN_TARGET_KEY)` (home is still reached, and the refused value stays, per
T24-D9), and a refused target under `denyReadOf` and under an unreachable store (treated as no target).

### I2 · Every T25 row would pass if `takeReturnTarget` called `clear()` — **ACCEPTED, applied** (Medium)

Verified by measurement, twice. Before: **2 failed of 124**, both T24's — none of the 57 T25 cases noticed, because
they assert the store is *empty* after a refusal and an empty store is what `clear()` produces too.

**Change made** — `threeFlows` now installs a `watchStorage()` and reports `clearedDuringRefusal`, and every
rejection row asserts it is `0`:

```ts
expect(flows.clearedDuringRefusal, 'the whole store is never cleared').toBe(0);
```

Two details that are the difference between this working and looking like it works:

- **It is a delta, not a total.** `threeFlows` clears the store itself between its three runs, so a raw count would
  measure the harness's own footprint. The count is taken immediately before and after each `signInAndLand`.
- **The watch is installed once**, outside the three runs, rather than per run — `vi.spyOn` on an already-spied
  method stacks, and `vi.restoreAllMocks()` does not run between runs inside one case.

**After: 20 failed of 124.** And the reason this matters beyond the mutant is in the assertion's comment: a
`clear()` would take **R29's mid-task form input** with it — the other half of L16's carve-out, which this module
has no business touching.

### I3 · AC9 is proven by a conjunction — **ACCEPTED as a record** (Low)

Verified. The proof is: the counterfactual shows the shape **would throw**, and the flow row shows the visitor
**reached `/app/home`** — which is unreachable had the throw happened, because the screen would have been left on
`/app/callback`.

Recorded explicitly, because Kimi is right that it leans on something unstated: **an unhandled rejection from
inside the effect prints *below* vitest's all-passed line**, so this proof is only sound for a reader who takes the
**exit code** as the result. Exit code 0 is recorded for every full run in this task's artifacts. That house rule
is not new — it predates this task — but AC9 is the first criterion in the package that *depends* on it, so it is
written down here rather than assumed.

**Change made:** none in code. A direct spy on `navigate` was considered and declined: it would assert the call
rather than the outcome, and the outcome is the thing the visitor experiences.

### I4 · The counterfactual uses `router.navigate`, not the screen's `useNavigate` — **ACCEPTED, applied** (Low)

Verified: `routerOutcomeFor` calls `router.navigate`; `CallbackScreen` uses `useNavigate()`, which additionally
resolves a relative path against the current route — which is exactly why `home` and `./home` behave differently
there.

Every shape in the table carries a leading slash except the external ones, which throw either way, so the
distinction cannot change an answer. The claim is narrowed anyway.

**Change made:** the doc comment on `routerOutcomeFor` now says it measures the **bare router, not the screen**,
and why that cannot change any answer here — *"an instrument should be described as what it is."*

### I5 · `/account/sessions/` is missing as a trailing-slash witness — **ACCEPTED, carried to Phase 10** (Low)

Verified by measurement: `/account/sessions/` reaches route `account/sessions`, the same property `/home/` has.
Kimi's reason is the useful one — a single-segment witness would let a fix pass that only handles one segment.

**Phase 10 requirement R-I5**, beside the near-miss pairs.

### I6 · The AC20 discard case uses only one refused shape — **ACCEPTED, carried to Phase 10** (Low)

Verified: `:1338` seeds `https://evil.example/app/home`, a `refuse` shape. Kimi's point is better than it first
looks: the *weakest* witness was chosen. A `refuse` shape is one react-router would reject anyway, so if a future
change inspected or normalised the target before discarding, the case would not notice; a `reach` shape like
`/home/../admin` is precisely what would be accidentally honoured.

**Phase 10 requirement R-I6**: parameterise AC20 over one representative per `reach` family, or at minimum add
`/home/../admin`.

### I7 · No mixed shape in the `reach` category — **ACCEPTED, carried to Phase 10** (Low)

Verified: the only mixed row (`:1256`) is `refuse`, where the external URL dominates and the combination proves
little. A `reach` combination — `/home?x=1#fragment` — would prove that query, fragment and traversal handling do
not interact to produce a match.

**Phase 10 requirement R-I7.**

### I8 · `routerOutcomeFor` dynamically imports `../app/routes` by name — **REJECTED, on a measurement** (Low)

**The premise is false in TypeScript.** `await import('../app/routes')` returns a **typed** module namespace, so
`routes.appRoutes` is checked at compile time exactly as a static import would be. Kimi's concern — "fails at
runtime while the test file still compiles" — cannot happen.

Tested rather than argued. Renaming `appRoutes` to `appRoutesRenamed` in `routes.tsx` and running `tsc --noEmit`:

```
src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx(225,44): error TS2339:
  Property 'appRoutes' does not exist on type 'typeof import(".../src/app/routes")'
src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx(360,44): error TS2339: ...
```

Line 225 is `routerOutcomeFor`; line 360 is T24's `arriveAt`, which has used this pattern since T16. A rename
breaks compilation, and `tsc --noEmit` runs in **every phase of this pipeline** and is an acceptance criterion.
The recommended "compile-time check that `appRoutes` exists" would be a test asserting that TypeScript is
TypeScript.

**Change made:** none. Rejection recorded with the measurement so the finding is not re-raised.

### I9 · The completeness pin hard-codes 18 — **ACCEPTED as a record** (Low)

Verified at `:1381`. The hard-coded count **is** the pin: it exists so a family cannot be dropped, and a count that
adjusted itself would pin nothing. The friction is the feature.

**Recorded, with the instruction the failure message cannot carry:** if this assertion fails because a family was
*added*, update the number and the name list. If it fails because one was *removed*, put it back.

### I10 · The three-kinds split is asserted by count, not membership — **ACCEPTED as a record** (Low)

Verified at `:1284`. Both reviews reached the same conclusion and so does this gate: **leave it.** Swapping one
`reach` for one `miss` would keep the counts, but the per-row control would then fail, because `miss` asserts the
catch-all and `reach` asserts a named route. The count pin is a readability aid; **the per-row cases are the
guard**, and that is recorded here so the pin is not mistaken for the thing doing the work.

---

## What changed

| File | Change | Finding |
|---|---|---|
| `auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | `threeFlows` watches `clear` and reports a delta; every rejection row asserts it is `0` | **I2** |
| `auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | `routerOutcomeFor`'s doc comment narrows its claim to the bare router | **I4** |

**No production code changed** — T25-D5's condition still has not triggered. `spec/` untouched.

## Carried into Phase 10

| Requirement | From | What |
|---|---|---|
| **R-I1** | I1 / S1 | a refused target under a throwing removal, a throwing read, and an unreachable store |
| **R-I5** | I5 / S5 | `/account/sessions/` as a multi-segment trailing-slash witness |
| **R-I6** | I6 | AC20 over a `reach` shape, not only a `refuse` one |
| **R-I7** | I7 | a `reach`-category mixed shape (`/home?x=1#fragment`) |

Already on Phase 10's list from Phase 5: the near-miss pairs as their own cases, the leading-space row, and the
mutation harness — whose list Phase 4 §7 froze, with case-folding, trailing-slash strip and trimming as the three
that matter most.

**New acceptance criterion from this gate: AC21** — a refused target is consumed by a **removal**, never by wiping
the store.

---

## Next — Phase 10 — test generation.
