<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T41 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
feat(frontend): redirect every unauthenticated arrival to the sign-in start (T41, R28)
```

## Commit message

```
feat(frontend): redirect every unauthenticated arrival to the sign-in start

R28's second clause. Task 40 built the first and left every blocked route
rendering blank - not a refusal so much as a dead end, with the deep link
discarded. One component answers all three arrivals R28's "any other
route" denominates: a blocked authenticated path, a path that matches
nothing, and the bare /app/. They are identical by construction rather
than by three separate pieces of care, which is what L4 asks for, since a
redirect is observable to an unauthenticated prober.

The mechanism was decided by measurement, not preference. A loader is
technically cleaner - it redirects before a render - but a loader carries
no element, so a loader-based redirect would be invisible to
routesWithElements, to task 37's AC9 and to task 40's AC13, the pin
written one task earlier for exactly that hazard. Three loader traps are
recorded in the component header: redirect() pushes, redirect() with the
basename on it loops until V8 runs out of heap, and only replace() both
replaces and runs once under StrictMode.

The return target is remembered with the allowlist as the only filter, so
there is no branch for the arrival that matched nothing, for the bare
/app/, or for a parameterised path - returnTarget.ts refuses what it
cannot honour and says nothing. That rule is about where a rule lives
rather than about behaviour, so a source scan enforces it; a behavioural
assertion cannot.

Eleven instruments of tasks 16, 33, 37 and 40 were grown rather than
bypassed, including task 40's AC13, which fires on the first task after
the one that wrote it - by design.

One line of production code was deleted rather than added as a result of
review. The module briefly stripped the basename from the arrival, on the
premise that useLocation().pathname carries it. It does not:
router.state.location.pathname does. Measured over six arrivals through
both router constructors, the strip could only fire on /app/app/account -
the one arrival where it was wrong, turning a route nobody asked for into
a remembered /account.

Closes: frontend task 41
Requirements: R28
LOCKED: L1, L4, L11, L12, L14, L16

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| file | | |
|---|---|---|
| `src/auth/RedirectToSignIn.tsx` | **new** | +93 · the only new export, `RedirectToSignIn(): ReactElement \| null` |
| `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **new** | +589 · the named test, 35 cases, 8 describes, all prefixed `T41` |
| `src/app/routes.tsx` | modified | +27/−… · the index route, the catch-all's element, the import-cycle comment |
| `src/auth/RequireSession.tsx` | modified | one line, plus the header paragraph that became false |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | grown | `withProbes`' gate identity, T37's AC9, T40's AC2/AC10/AC12/AC13 |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | grown | four pins, one new case |
| `src/auth/callbackValidatesNonce.test.tsx` | seam only | the exchange now resolves an `accessToken`, as production does |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | seam only | the same |

**8 files, +891 / −53**, across **10 commits** (`593aa3d6`… is T40; T41 runs from the phase‑2 brief to this
artifact). No file under `spec/` was modified. `auth/returnTarget.ts`, `auth/accessToken.ts`,
`auth/cleanReauth.ts`, the three screens, `app/mountShell.tsx`, `app/AppShell.tsx`, `i18n/catalogue.ts` and the
two route arrays are untouched, as the plan required.

## Summary

Three arrivals, one component, and a return target the allowlist filters. The visitor who typed
`/app/account` with no session now lands on the sign-in start with `/account` waiting in their own
`sessionStorage`, instead of on a blank page with the link thrown away.

What this task did **not** do is as deliberate: an authenticated visitor at an address that matches nothing
still gets task 40's blank page, because R28 is conditioned *WHILE a visitor is unauthenticated* and
redirecting them would log them out of their own address bar.

## Testing performed

| | |
|---|---|
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **35 / 35** |
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **49 / 49** |
| `vitest run src/app/routesResolveUnderAppPrefixOnly.test.tsx` | **10 / 10** |
| `vitest run` | **1780 / 1781** — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case |
| `tsc --noEmit` · `oxlint src` | **exit 0** · **exit 0** |
| `python3 -I .ai/prompts/frontend/T41/artifacts/07-mutation-harness.py` | **22 / 22 regressions killed, every kill `KILLED/own`, 0 survivors, 0 unfalsifiable, 0 broken, 0 disagreements** |

The harness is the ninth copy of T37's machinery. Four runs are kept: `07-`, `09-`, `10-` and
`11-mutation-run.txt`, which together record the task's whole measurement history — six rows alive at phase 7,
two of them rewritten at phase 9 because they were mutating dead code, all twenty killed at phase 10, and two
new rows added at phase 12 to settle a reviewer's disagreement by measurement instead of by argument.

Three reviews ran at the Kimi gates: 18 findings at phase 3, 14 at phase 8, 14 at phase 11. **One of them
changed production code** — phase 8's I6, which doubted a measurement and was right.

## Specification references

- **Task:** `spec/frontend/tasks.md` task 41 — *Unauthenticated redirect*
- **Requirement:** **R28** (`requirements.md:99`)
- **LOCKED decisions:** **L1** (the visitor's intent survives sign-in) · **L4** (nothing discloses a cause) ·
  **L11** (no router-supplied English) · **L12**, **L14** (the route set is exactly §4c) · **L16** (storage
  inventory)
- **Named tests** (`package.md` §8): `unauthenticatedRoutesRedirectToSignIn` (**created**) ·
  `publicRouteSetIsExactlyTheDeclaredSet` (**grown**) · `homeRouteRendersLandingScreenAndManagesFocus`
  (task 42's)

## Branch

`spec/service-specs-and-ai-framework`, pushed to origin after every phase commit. `main` stays deployable: the
SPA's behaviour change is additive at every public route, and the eleven authenticated routes were already
unreachable without a session.

## Open, carried forward

- **OQ-T41-1** — the basename-spelled destination cannot be a harness row; it kills the process rather than
  failing. Narrowed, since `APP_BASENAME` is no longer imported by the module.
- **OQ-T41-2** — the end-to-end deep link needs a screen behind the gate: task 42's, blocked on 173/174.
- **OQ-T41-3** — `useLocation()` being basename-relative is now load-bearing in the opposite direction.
- Carried from earlier tasks: **OQ-T40-1** (the gate's read is not reactive) · **FU-T37-2** (`routes.tsx:5`'s TDZ
  claim is false as written) · **OQ-T36-5** (`endSession()` has no caller) · **OQ-T33-2** (the authenticated data
  path is dormant) · **FU-T39-1** (the shared module-scope scan's holes) · **FU-T07-1** · spec drift at
  `design.md:137` vs `package.md:368`.
- **This template's trailer is still stale** — `13-pr-preparation.md` asks for a `Claude Opus 4.8 (1M context)`
  co-author line. The message above uses the correct one. Regenerating `.ai/` would fix it for every service.

**Phase 13 complete. T41 CLOSED — PASS.**
