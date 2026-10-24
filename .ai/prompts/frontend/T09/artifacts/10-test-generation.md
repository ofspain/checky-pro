# frontend · T09 · Phase 10 — Test Generation, Mutation and Coverage

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## The named test

`frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` — **55 cases**, one group per acceptance criterion:

| AC | Cases | What is pinned |
|---|---|---|
| AC1 | 23 | each of the eleven paths, and a sub-path of each, left to the network; plus a case asserting the sub-path table still covers every entry, in order |
| AC2 | 4 | `/app`, `/app/`, `/app/sign-in`, `/app/disputes/01HX/evidence` answered with the shell |
| AC3 | 1 | no cache read for any of the 22 denylisted navigations |
| AC4 | 16 | the segment-boundary table (14 rows), query/hash invariance, and mutual exclusion over all 47 paths used in the file |
| AC5 | 1 | nothing written to the cache for a denylisted navigation |
| AC6 | 5 | four non-navigation modes decline for every denylisted path and for the shell; a non-navigation is never answered from the cache |
| AC7 | 2 | the `agents.md` drift guard, order included; one derived pattern per path, asserted behaviourally |
| AC8 | 1 | R57, L17 and `agents.md` cited in the module source |
| AC9 | 2 | no banned global in module code; the shell prefix is the one the SPA owns |

Result: **55/55 pass.** Whole frontend suite: **203/204**, the one failure being the known environmental
`lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0` emits no summary line; baselined in T07 both
with changes stashed and against a real injected `fetch` violation). `tsc --noEmit` clean; `oxlint` reports no
violations.

## Mutation pass

Each mutant was applied to the real source file, the suite was run, and the file was reverted with
`git checkout --`. The working tree was verified clean after every mutant.

| # | Mutant | Target | Result |
|---|---|---|---|
| M1 | drop `/accounts` from the list | source | **CAUGHT** — 4 cases (AC1 sub-path, AC1 coverage, AC4, AC7) |
| M2 | reorder the list (swap `/login` and `/logout`) | source | **CAUGHT** — 2 cases (AC7 order, AC1 coverage) |
| M3 | match with `startsWith` instead of a segment boundary | source | **CAUGHT** — 3 cases (`/apikeys`, `/accountsxyz`, `/logouts`) |
| M4 | match with `includes` instead of an anchored prefix | source | **CAUGHT** — 5 cases |
| M5 | drop the `(?:/\|$)` boundary | source | **CAUGHT** — 5 cases |
| M6 | drop the regex escape | source | **CAUGHT** — 1 case (`/xwell-known`, AC4) |
| M7 | answer every navigation with the shell | source | **CAUGHT** — 24 cases |
| M8 | drop the navigate-mode check in `isDenylistedNavigation` | source | **CAUGHT** — 4 cases (AC6) |
| M9 | read the cache before the denylist check | test harness | **CAUGHT** — 2 cases (AC3, AC6) |
| M10 | order-blind drift parser **and** a reordered list | test + source | **CAUGHT** — 1 case |
| M11 | drop the navigate-mode check in `isShellNavigation` | source | **CAUGHT** — 5 cases (AC6) |

**11 mutants, 11 caught, 0 survived.**

M6 is the one the `/xwell-known` row exists for: without the escape, `^/.well-known` treats `.` as "any character",
so `/xwell-known` would be denylisted. No other case notices.

M9 is the mutant the Phase 4 gate created a target for: with a pure policy module there is no production cache read
to reorder, so the mutant is applied to the harness that stands in for Workbox's navigation fallback. It confirms
AC3 is a real assertion about ordering and not a tautology.

M10 is a **paired guard-strength check** rather than a defect probe: it weakens the drift guard to ignore order *and*
reorders the list, to see whether anything other than AC7's order comparison notices. One case does — the AC1 case
asserting the sub-path table's keys equal `DENYLISTED_NAVIGATION_PATHS` in order — so order sensitivity is covered
twice, in two files, for two different reasons.

## Coverage

`@vitest/coverage-v8` is not a dependency of this workspace and adding one is out of scope for this task (the
lockfile is under a supply-chain policy check), so coverage is accounted for by enumeration rather than by a
reporter — as in T07 and T08, where the mutation pass is the stronger evidence anyway.

`navigationDenylist.ts` has four branch points, every side of each exercised:
- `segmentPrefixPattern`'s trailing-slash test — true (`/oauth2/`, `/.well-known/`, `/api/`) and false (the other
  eight), both at module load;
- the escape — with a metacharacter (`/.well-known/`) and without (AC4's `/api-keys`);
- `isDenylistedNavigation` — non-navigate (AC6), match (AC1), no match (AC4 negatives);
- `isShellNavigation` — non-navigate (AC6), denylisted (AC1 asserts it false for all eleven), shell match (AC2),
  no match (`/application`, AC4).

Every exported symbol is referenced by at least one case: `DENYLISTED_NAVIGATION_PATHS` (AC1, AC7),
`NAVIGATION_DENYLIST` (AC7), `SHELL_NAVIGATION_PREFIX` (AC9), both predicates (throughout). No statement in the
module is unreached.
