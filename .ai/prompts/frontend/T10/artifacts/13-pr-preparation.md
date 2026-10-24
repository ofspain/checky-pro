# frontend · T10 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
frontend T10: end-session navigation bypasses the worker
```

## Commit message

```
R19 ends the SAS session by navigating the browser to /connect/logout
with an ID token in the query string, and says in its own words that
this navigation "is served by SAS, not the SPA shell (R57)". Two things
follow that T09's general denylist did not pin: a cached end-session
response would show the signed-out page while the SAS session is still
alive -- the one worker failure that lies to the user rather than
breaking visibly -- and it would write an ID token into Cache Storage,
outliving the tab that owns it (L3, design.md §5).

No production change. isDenylistedNavigation reads url.pathname and
/connect/logout is one of the eleven, so every end-session URL shape was
already declined; the task proves the flow R19 depends on and fails
loudly if a later change narrows the policy to bare paths.

src/pwa/endSessionNavigationBypassesServiceWorker.test.ts, 23 cases over
seven URL shapes taken from the backend's own green test
(SasLoginIntegrationTest:519-527, :488-490), not invented. The cache spy
records every key it is asked about, so the token assertions are over
recorded keys rather than call counts alone. The other half of the flow
is pinned too: /app/signed-out is the shell's to answer, so sign-out
still renders.

10 mutants applied to real files, 10 caught, 0 survived.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts` | **new**, 23 cases |
| `.ai/prompts/frontend/T10/artifacts/*` | the fourteen phase artifacts |

**No production file modified.** `navigationDenylist.ts`, `sasNavigationBypassesServiceWorker.test.ts`, every
T01–T08 file, `vite.config.ts`, `.oxlintrc.json`, `spec/**`, `contracts/**`, `services/**` and `infra/**` are
untouched.

## Testing performed

| Check | Result |
|---|---|
| `vitest run src/pwa` | **84/84** (T09's 61 + T10's 23) |
| `vitest run` (whole suite) | **232/233** — the one failure is environmental |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |
| Mutation pass | **10 applied, 10 caught, 0 survived** |
| Live platform | `bin/smoke` **PASSED**, including sign-out through end-session |

The failure is `lintRejectsHandWrittenBackendFetch` AC4: this host's `oxlint@1.87.0` prints no summary line.
Baselined in T07 both ways; predates and is unaffected by this task.

## Carried forward

1. **Task 12 must import `NAVIGATION_DENYLIST`** rather than restate the list, and **assert its generated precache
   manifest contains no URL matching it** — a Workbox precache route matches by URL *ahead* of the navigation
   fallback, so a denylisted URL in the manifest would answer `/connect/logout` with every case here still green.
   The second half, named in T10's gate decision D6: task 12 must also assert its navigation route **still answers
   `/app/signed-out` from the shell**, because that route *will* be precached and the two requirements pull in
   opposite directions.
2. **The real 302 redirect chain is untested** (Kimi phase 11, gap 8). jsdom has no `FetchEvent`, so the two-step
   case is two harness calls. Task 12's integration tests, or Playwright against the local stack, own the real
   chain — and `bin/smoke` already exercises it end to end outside the SPA.
3. **R22's cross-tab guarantee is not proven here.** AC6 proves the decision is stateless; cache isolation across
   tabs belongs to tasks 11 and 12 and to T06's machinery. Phase 12's matrix credits it accordingly.
4. **Two harness copies, one warning.** `simulateNavigationFallback` exists in both T09's and T10's test files; only
   T10's carries the keep-in-step comment, because adding the reciprocal one would edit a frozen file (L12, gate
   decision D4). Task 12 replaces both with the real `NavigationRoute`.
5. **The `agents.md` block parse is now in four test files** (T05, T08, T09, T10). Worth extracting into a shared
   spec reader when a task legitimately opens one of them.
6. **Coverage is enumerated, not measured**, across T07–T10: `@vitest/coverage-v8` is not a workspace dependency and
   adding one was out of scope. If real coverage numbers are wanted, that is a small lockfile change.

## Specification references

R57 (`requirements.md:203-205`); R19 (`:62-65`); R22 (`:72-75`); `agents.md:35-36`, `:88-89`; L17
(`design.md:61-62`), L3 (`:191`), §5 (`:194`), L14 (`:139`), L18 (`:63`), L12; `routes.tsx:4`, `:16`;
`SasLoginIntegrationTest.java:474-497`, `:501-517`, `:519-527`;
`services/auth/src/main/resources/application.properties:51-53`.
