# frontend · T09 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
frontend T09: denylisted navigations bypass the worker
```

## Commit message

```
The SPA is about to acquire a service worker with an offline shell. A
fallback that answered every navigation would hand index.html to a
browser on its way to /oauth2/authorize or /connect/logout, and no one
could sign in or out. R57 and L17 name the eleven paths that must always
reach the network; this task writes that boundary as the worker's
navigation policy, before any task turns the worker on.

src/pwa/navigationDenylist.ts exports the eleven agents.md paths
verbatim and in order, a derived RegExp[] in the shape Workbox's
navigateFallbackDenylist and NavigationRoute both accept, and two pure
predicates. Matching is path-segment prefix, so /accounts covers
/accounts/me but not /accountsxyz, and /app covers /app and /app/sign-in
but not /application. Non-navigations decline, so the module cannot be
repurposed as the API policy task 11 owns.

src/pwa/sasNavigationBypassesServiceWorker.test.ts has 61 cases. A
test-local harness mirrors Workbox's navigation-fallback semantics over
a spied fake cache, so "never answered from cache or with the shell" is
asserted observably while the policy module stays pure and holds no
cache reference. A drift guard parses the list out of
spec/frontend/agents.md, order included.

By gate decision D1 this task registers nothing: tasks 9-11 are all pure
policy, and task 12 ("a new build waits for a user reload") is the only
one of the four that needs a live registration, so it is the task that
wires vite-plugin-pwa and updates T01's three absence assertions. No
T01-T08 file is touched (L12).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/pwa/navigationDenylist.ts` | **new**, 69 lines — the policy |
| `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` | **new**, 61 cases — the named test |
| `.ai/prompts/frontend/T09/artifacts/*` | the fourteen phase artifacts |

No existing file modified. No dependency added. `vite.config.ts`, `.oxlintrc.json`, `spec/**`, `infra/**`,
`contracts/**` and every T01–T08 file are untouched.

## Testing performed

| Check | Result |
|---|---|
| `vitest run src/pwa` | **61/61 pass** |
| `vitest run` (whole frontend suite) | **209/210** — the one failure is environmental, below |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |
| Mutation pass | **11 mutants applied to real source, 11 caught, 0 survived** |

The failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts` AC4, which expects `oxlint` to print
`Found 0 warnings and 0 errors`. This host's `oxlint@1.87.0` prints no summary line at all — verified in T07 both
with all changes stashed and against a deliberately introduced real `fetch` violation. It predates this task and is
unaffected by it.

## Carried forward

1. **Task 12 must build the worker's navigation route from these exports.** Importing `NAVIGATION_DENYLIST` rather
   than restating the list is what makes T09's guarantee real; nothing in T09 can enforce it, and a worker wired
   with its own list would leave all 61 cases green.
2. **Task 12 must assert its generated precache manifest contains no URL matching `NAVIGATION_DENYLIST`.** A Workbox
   precache route matches by URL and registers ahead of the navigation fallback, so a denylisted URL in the manifest
   would be answered before this policy is consulted. This is the task's hardest limit and both reviews confirmed
   it.
3. **Task 12 owns worker scope and serving.** A worker served from `/app/sw.js` has scope `/app/` and never sees a
   `/login` navigation; root scope needs the script served from `/` with `Service-Worker-Allowed: /`, which is an
   edge change (`infra/local/edge/Caddyfile`). The denylist is deliberately scope-independent.
4. **Task 12 needs an `.oxlintrc.json` override** for a real worker's `self` and `fetch`, anticipated by T04 and
   sidestepped here by the pure design.
5. **Task 10** owns `/connect/logout`'s dedicated test (`endSessionNavigationBypassesServiceWorker`); it is covered
   here only as one of the eleven, by the general rule.
6. **`/notifications` stays out of the navigation denylist** (D10): it is an API route, handled by task 11's
   NetworkOnly policy. R57 is copied verbatim and `spec/` is read-only here.

## Specification references

R57 (`requirements.md:203-205`); `agents.md:88-89` (the eleven paths), `:90` (NetworkOnly, task 11), `:91-92`
(update policy, task 12); L17 (`design.md:61-62`), L13, L14, L12, L6, L10, L3, L16; `design.md:194` (§5 cache
contents), `design.md:220` (§6 `pwa/`); `tasks.md:18-21`.
