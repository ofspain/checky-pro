# frontend · T25 · Phase 5 — Implementation Plan

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**This task is test-only, which the Phase 6 prompt explicitly provides for** ("Do NOT write tests here … unless the
task itself is test-only"). So the plan splits the work across the two authoring phases deliberately rather than
leaving Phase 10 with nothing: **Phase 6 proves the claim, Phase 10 proves the proof.**

| Phase | Delivers |
|---|---|
| **6** | the eleven family rows, F4's and T25-D4's three additional rows, F7's mixed row, F3's discard row, and T25-D1's counterfactual controls — the task's substance, and the first run that could find a shape unrefused (T25-D5) |
| **10** | the mutation harness; the near-miss pairs as their own cases; the leading-space row; and whatever the Phase 6 run shows to be weak |

---

## Files to create

**None.** The frozen brief authorizes no new file.

## Files to modify

| File | Scope of the edit |
|---|---|
| `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | the `describe('a target outside the allowlist falls back to home (T25)')` block at `:1055-1070` — fifteen `it.todo` rows become cases — plus new harness helpers at the file's helper section, and **nothing else**. The T24 cases above it are not touched (if one must change, that is a finding) |

**Conditional, and only on a Phase 6 measurement (T25-D5):** `frontend/src/auth/returnTarget.ts`, the narrowest
change that refuses an unrefused shape, flagged as a deviation and carried to the Phase 9 gate.

## New harness helpers (signatures)

All in the existing helper section of the named test, beside `arriveAt`/`seedRaw`/`signInAndLand`.

```ts
/** What a bare router does with a shape, so harm is measured before defence is claimed (T25-D1). */
async function routerOutcomeFor(target: string): Promise<
  { threw: string } | { pathname: string; search: string; hash: string; matched: string[] }
>;

/** Everything a visitor could tell one outcome from another by, for AC4's four-field comparison (T25-D2). */
function visibleOutcome(arrival: Arrival): {
  pathname: string;
  search: string;
  text: string;
  historyAction: string;
};

/** One rejection row's three flows, from one harness: refused, no target, honoured (T25-D2). */
async function threeFlows(shape: string): Promise<{
  refused: ReturnType<typeof visibleOutcome>;
  absent: ReturnType<typeof visibleOutcome>;
  honoured: ReturnType<typeof visibleOutcome>;
  refusedHeldToken: string | null;
  storeAfterRefusal: string[];
}>;
```

`visibleOutcome` is the file's existing comparison object from the T24 AC9 case, lifted into a helper so the T25
rows and that case cannot drift apart. Lifting it **changes one T24 case's body without changing its assertions** —
that is the single authorized touch of T24 code, and Phase 6 must say so in its notes.

Reused unchanged: `arriveAt`, `startSignIn`, `remember`, `seedRaw`, `signInAndLand`, `watchStorage`,
`removedKeys`/`writtenKeys`/`storedKeys`, `denyReadOf`, `denyRemovalOf`, `deferredAnswer`, `refusal`,
`jsonResponse`, `tokensFor`, `EXPECTED_ALLOWLIST`.

## Entities / repositories / services used

None — this service has no persistence layer. The equivalents are: the module under test
(`auth/returnTarget.ts`, read only), the one call site (`auth/CallbackScreen.tsx:194-195`, read only), the route
data (`app/routes.tsx`, read only), and `sessionStorage` under L16's carve-out.

## Tests required

### Phase 6 — the rejection table

One `it.each` over a `FAMILIES` table, one row per family, each row asserting the five things T25-D2/D3 fix:

| # | Row | Shape |
|---|---|---|
| 1 | absolute URL, another origin | `https://evil.example/app/home` |
| 2 | absolute URL, this origin | `http://localhost:5173/app/home` |
| 3 | protocol-relative host | `//evil.example/app/home` |
| 4 | `javascript:` | `javascript:alert(1)` |
| 5 | `data:` | `data:text/html,x` |
| 6 | backslash path | `/\evil.example` |
| 7 | traversal | `/home/../admin` |
| 8 | encoded traversal | `/home/%2e%2e/admin` |
| 9 | prefix match | `/homeward` |
| 10 | public route | `/sign-in` |
| 11 | a route that does not exist | `/not-a-route` |
| 12 | parameterised route, real id | `/invoices/123` |
| 13 | query string | `/home?x=1` |
| 14 | fragment | `/home#top` |
| 15 | **trailing slash** (F4) | `/home/` |
| 16 | **case** (T25-D4) | `/HOME` |
| 17 | **encoded separator** (F4) | `/account%2Fsessions` |
| 18 | **mixed** (F7) | `https://evil.example/../admin?x=1#fragment` |

Each row asserts: the refused flow lands on `/app/home`; **the sign-in completed** (the token is held, so the row
is not passing against a discard); the refused value is **gone** from the store; `isAllowlistedTarget(shape)` is
`false`; `rememberReturnTarget(shape)` stores nothing, with an allowlisted control (T25-D3); and AC4's four fields
equal the no-target baseline while the honoured flow differs in pathname (T25-D2).

### Phase 6 — the counterfactual controls (T25-D1), their own cases

- the four external shapes **throw** `External navigation is not allowed` from a bare router;
- `/home/../admin` reaches **`/app/admin`**, a real authenticated route;
- `/home/` and `/HOME` reach the **`home` route** — the two families the router would honour, where the predicate
  is the sole defence;
- `/account%2Fsessions` reaches the **catch-all**, and `/app/home` would reach `/app/app/home`.

### Phase 6 — F3's discard row

A refused target seeded, any discard shape, and the visitor on `/app/sign-in` with the refused value still in the
store — the R8/T24-D14 counterpart for a rejection.

### Phase 10 — the strengtheners

The near-miss pairs as their own cases (`/homeward` vs `/home`, `/accou` vs `/account`, `/home/` vs `/home`,
`/HOME` vs `/home`); the leading-space row (`'  /account/sessions'`), which is the shape that gives the trimming
mutant its harm; and the mutation harness, whose list is already frozen in §7 of the brief, with **case-folding,
trailing-slash strip and trimming** as the three that matter most because the router would honour their results.

## Execution order

The backend ordering in the prompt (schema → dao → service → api → tests) has no counterpart here. The order that
matters is **measure, then assert**, and it is the order T25-D1 fixes:

1. **Baseline.** `tsc --noEmit` and the full suite by exit code, so the 1075/15/1 starting point is on the record
   before anything is edited.
2. **The counterfactual controls first.** They are the cases that can *fail for a real reason* — if react-router
   8.4.0 no longer throws, or no longer matches `/HOME` to `home`, the brief's §2 is stale and the rows built on
   it would be asserting the wrong thing.
3. **`visibleOutcome` lifted** out of the T24 AC9 case, and that case re-run on its own to prove the lift changed
   nothing.
4. **`routerOutcomeFor` and `threeFlows`**, then one family row end to end — row 7, the traversal, because it is
   the one with a demonstrable harm — and that single row run alone before the table is filled out.
5. **The remaining seventeen rows**, then the table run alone, with the **runtime recorded**: three flows × 18 rows
   is 54 mounts, and T25-D2 requires the measurement to come back here if it is slow rather than a flow to be
   dropped.
6. **F3's discard row.**
7. **The full suite by exit code**, and the T24 cases confirmed still passing **unchanged**.
8. **Commit and push**, before Phase 10's harness ever runs — it reverts with `git checkout`.

## What this plan does not do

No production edit unless step 2 or 4 finds a shape unrefused (T25-D5), and then only the narrowest one, declared.
No normalisation, decoding or pattern matching of any kind. No new file, no new dependency, no catalogue string.
No change to `CallbackScreen.tsx`, `routes.tsx`, the two pure readers, the holder, `authorizeRequest.ts` or
`.oxlintrc.json`. No assertion that an unchanged `window.location` proves anything (T25-D6). No T24 case's
assertions altered.

---

## Next — Phase 6 — implementation.
