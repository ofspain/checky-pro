<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T40 · Phase 6 — Implementation

| | |
|---|---|
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

## Gates

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1724 / 1725** — the single failure is the known host-environmental
`src/api/lintRejectsHandWrittenBackendFetch.test.ts` case (this host's `oxlint@1.87.0` prints no summary line) |

## What was written

### `src/auth/RequireSession.tsx` — new, 38 lines

```tsx
export function RequireSession() {
  const hasSession = Boolean(accessToken.current());
  return hasSession ? <Outlet /> : null;
}
```

Two imports only: `Outlet` from `react-router`, `accessToken` from `./accessToken`. The condition is named
rather than inlined into the ternary, because `Boolean(...)` in a condition position is a cast oxlint strips
as redundant; named, it is a conversion, and the empty-string case stays enforced where the brief put it.
The header carries the four things the source cannot say: why it renders nothing instead of redirecting
(task 41 owns R28's second clause, and the catch-all has rendered nothing silently since task 1), why a
held token claims less than it looks like (a SUSPENDED account keeps a working token — task 31), why it is
one layout route rather than eleven elements, and **OQ-T40-1** — the read is not reactive, nothing today can
expose that, and a subscription would be a new surface on the session owner this task may not touch.

### `src/app/routes.tsx` — the split

`AUTHENTICATED_ROUTE_PATHS.map(...)` now produces the **children of one pathless guard route** rather than
direct children of the root. Pathless, not `path: ''`. The catch-all stays a **sibling**, with a comment
saying why that is deliberate: task 41 replaces it with the redirect, and a catch-all behind the gate could
not redirect the only visitor it exists for.

### `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — one assertion grown, one comment added

`pathTree`'s expectation at `:154` names the new level explicitly —
`[undefined, AUTHENTICATED_ROUTE_PATHS.map(...)]` between the nine public leaves and the catch-all — rather
than being loosened. `routesWithElements`'s pin at `:161` is unchanged at three; the comment above it now
says that the `route.path !== undefined` filter is why, that the filter is load-bearing from now on, and
where what it cannot see is pinned (**AC13**, in the named suite, phase 10).

## Two instruments fired that phase 5 predicted would not

Phase 5's risk table said *"none of them mounts an authenticated path — so the nesting should be invisible
to them."* **That was wrong, and running step 4 is what caught it.** Neither failure is about mounting: both
read the route **table** and one reads a **match chain**. Both were grown under the closed-task rule — an
instrument may be edited when it has become false or must grow to admit new state, never when it is merely
incomplete for something the new task owns.

### `src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx:518` (task 33's AC11)

It filtered `appRoutes[0].children` for an authenticated path with an element. After the nesting, that
search found **none of the eleven** — and the case's own **lower bound** (*"the paths really are in the
table, so the check is not passing over an empty list"*) is what turned a vacuous pass into a failure. That
lower bound did its job exactly as written, three tasks after it was written. The walker now descends the
tree; the claim is unchanged and still reads zero, because the guard carries no `path`.

Its title says *"a T41 obligation when it changes"*, which is now a mis-attribution — the **guard** is task
40's and only the **redirect** is task 41's. The title is left as written and corrected in a comment rather
than edited, because the title records what task 33 claimed; the assertion message, which speaks in the
present tense about what to do next, is the part that was corrected.

### `src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx:1368` (task 25's AC16 control)

The counterfactual instrument reports a match chain, spelling a pathless match `'(index)'` (`:375`). The
chain for `/home` is now `['/', '(index)', 'home']`. Grown by naming the level, not by dropping the chain
assertion — the chain is what this control measures. Every other case in that file reads `.at(-1)`, and the
**leaf is unchanged**, which is why 24 counterfactuals and 188 cases were untouched.

## What phase 7 must mutate

The condition (`Boolean` → `!== null`, and inverted), the `null` arm, the nesting flattened back, the guard
route gaining a `path`, the catch-all moved inside the gate, a public path moved inside it, the two
count-pin filters, and the `pathTree` level. Each must kill with a **named** case — and until phase 10
writes the named suite, several of these have nothing to kill them, which is the measurement phase 7 exists
to produce rather than assume.

**Phase 6 complete.** One file created, three modified (one more than planned, both additions forced by a
measurement and both grown under the closed-task rule).
