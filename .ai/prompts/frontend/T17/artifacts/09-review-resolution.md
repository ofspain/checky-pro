# frontend · T17 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Human Approval gate — assembled and applied by Claude Opus 5 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` (Kimi 2.7) |
| **Produces** | `artifacts/09-review-resolution.md` |
| **Commit** | `b8d9aa59` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Disposition

**All six accepted.** Kimi's phase 8 corroborated every one of phase 7's findings and added the test angle for
three of them; it raised no finding phase 7 had missed, and rejected none. Its three "new" findings are F1, F3
and F2 restated with a test to go with each, so they are resolved together below rather than counted twice.

Each fix is confined to files T17 already owns. Nothing was refactored, no public API changed, nothing renamed
beyond adding one exported constant.

| # | Finding | Disposition | Mutant |
|---|---|---|---|
| F1 / Kimi 1 | `void` discards the seam's rejection | **ACCEPTED** — catch routes to the discard path | M1 **killed** |
| F2 / Kimi 3 | deps array contradicts the latch | **ACCEPTED** — deps reduced to `[]` | M4 **killed** |
| F3 / Kimi 2 | whitespace `state`/`code` passes the guard | **ACCEPTED** — trimmed before the test | M2, M3 **killed** |
| F4 | no StrictMode seam-zero on discard | **ACCEPTED** — one case added | M5 **killed** |
| F5 | `'callback'` literal, no constant | **ACCEPTED** — `CALLBACK_PATH` added | M6 **killed** |
| F6 | AC15 control pairs by index | **ACCEPTED** — keyed to one record | M7 **killed** |

---

## The changes, exactly

### F1 / Kimi 1 — the rejection is handled by the task that created the seam

`CallbackScreen.tsx`. The discard branch became a named `discard()` closure, so one definition serves both
callers, and the seam call became:

```ts
exchangeAuthorizationCode({ code, state }).catch(discard);
```

Both reviews converged on the same remedy, and it is the cheap one: the discard path already exists, the copy is
already neutral, so L4 makes "could not complete" the honest thing to say about a failed exchange too. A comment
records that task 20 may route it somewhere more specific.

**Why this is not scope creep into task 20.** The seam is T17's; a task that introduces a collaborator owes a
statement of what happens when it fails, even while it cannot fail yet. Leaving it would have put the defect in
task 20's diff, where it would read as task 20's mistake.

A case drives it with a rejecting spy and asserts the visitor lands on `/app/sign-in` with the notice, and that
storage is untouched — a failed exchange is not task 23's deletion either.

### F2 / Kimi 3 — the dependency list now says what the code does

`CallbackScreen.tsx`. `[params, navigate]` → `[]`, with the reason in a comment: the browser arrives by a fresh
navigation every time, so there is no second set of parameters to react to. Verified that the lint config makes
this safe — `.oxlintrc.json` sets every category `off` and enables only the two L6 `no-restricted-*` rules, so
no `exhaustive-deps` rule is in play and no suppression comment was needed.

A case pins the behaviour that makes it correct: navigate the mounted screen to a second `?code=…&state=…` and
the seam is still called once, with the first grant.

### F3 / Kimi 2 — trimmed before the emptiness test

`CallbackScreen.tsx`:

```ts
const state = params.get('state')?.trim() ?? null;
const code = params.get('code')?.trim() ?? null;
```

The trimmed values are what travel onward, so the exchange never receives padding the authorization server did
not send. Two rows added to `DISCARD_SHAPES` (`state=%20%20`, `code=%09`), which flow into all three `it.each`
blocks and the L4 identical-copy loop.

### F4 — the missing StrictMode case

One case: spy the seam, arrive at a discard shape under `{ strict: true }`, assert `calls` is empty.

### F5 — `CALLBACK_PATH`, and the tautology it would have created

`routes.tsx` gained `export const CALLBACK_PATH = 'callback'`, used at the route-attachment site, and
`PUBLIC_ROUTE_PATHS` is now built from both constants rather than repeating their literals.

**This fix broke AC17 in a way worth recording.** With the list built from `SIGN_IN_PATH`, AC17's
`expect([...PUBLIC_ROUTE_PATHS]).toContain(SIGN_IN_PATH)` compares the constant with itself — the
assertion-against-its-own-constant defect this package has caught seven times (T12 AC7, T13 AC8, T14 AC1, T15
S1/S2 and M10/M12, T16 M18). My own remediation would have been its eighth appearance.

AC17 now asserts the thing that can actually break, which is external: auth registers the SPA's redirect URI as
`/app/callback`, **measured against the live stack in phase 6**, so both spellings are a contract with another
service rather than an internal preference.

```ts
expect(SIGN_IN_PATH).toBe('sign-in');
expect(CALLBACK_PATH).toBe('callback');
```

Deliberately **not** changed: `routesResolveUnderAppPrefixOnly.test.tsx` keeps its literal
`SCREENS_ATTACHED_SO_FAR = ['sign-in', 'callback']`. That list is T02's independent statement about which
screens exist; importing the constants into it would make it agree with the route table by construction and stop
it detecting drift — the same trap, one file over.

### F6 — pattern and control declared together

`forbidden` became a record array of `{ label, pattern, violation }`, the three scans use `$label`
interpolation, and the negative control iterates the same record. Inserting a row can no longer misalign
anything.

---

## Verification

**Every fix is load-bearing.** Each was undone and the T17 suite re-run; all seven mutants were killed. The
harness compares **collected totals**, not failure counts, because a mutant that stops a file loading shows
fewer tests rather than more failures.

| Mutant | Undoes | Result |
|---|---|---|
| M1 | the `.catch` | 1 failed, 55 of 56 |
| M2 | the trim on `state` | 4 failed, 52 of 56 |
| M3 | the trim on `code` | 4 failed, 52 of 56 |
| M4 | the latch, with `params` back in the deps | 1 failed, 55 of 56 |
| M5 | the `return` after `discard()` | 7 failed, 49 of 56 |
| M6 | the registered callback spelling | 24 failed, 32 of 56 |
| M7 | one forbidden pattern, control left in place | 1 failed, 55 of 56 |

**Suite: 617 passed · 1 failed (618)** — +9 against the 609 at phase 7. `tsc --noEmit` clean, `oxlint` clean.
T17's own file is 56/56.

The single failure is the known environmental one, and **Kimi's phase 8 misattributes it.** Its non-gaps list
says "one known environmental T07 failure (`pnpm` not on PATH)". The failing case is
`lintRejectsHandWrittenBackendFetch` **AC4** — `expected '' to contain 'Found 0 warnings and 0 errors'`, because
this host's `oxlint@1.87.0` prints no summary line. The `pnpm`-on-PATH fault is **AC2** of the same file, and it
**passes** under the `npx -y pnpm@12.14.0` invocation used here. Two different faults; only one is
invocation-dependent, and neither is T17's. Recorded because the misattribution would otherwise be inherited by
phase 12.

### Process note

The mutation run crashed mid-way on a missing import and left M1's one-line mutation in the working tree.
Because the fixes had been committed first (`b8d9aa59`), nothing was lost and `git checkout --` restored it.
This is the failure that cost 19 cases in T14 and 5 in T15 when the order was the other way round.

---

## Open questions

**OQ-3 — the callback copy is unreviewed.** Unchanged by this phase. Neutral, names no cause, identical across
all six discard shapes, properties enforced by test. The owner's to reword in one file. **Gates nothing.**

**Carried, gating nothing ·** T16's OQ-1 (the five sign-in strings); T12's OQ-1 (a refresh cannot promote a
waiting worker); `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading and its claim that auth T19
is "not built" while L5 records it as built; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to
R57, which does not describe it.

---

## Next

Phase 10 — test generation.
