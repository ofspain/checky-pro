# frontend · T12 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
frontend T12: prompt-to-reload update policy
```

## Commit message

```
T09, T10 and T11 wrote the worker's policy as pure predicates, and
nothing registered a worker, so none of it ran. This registers one and
fixes how a new build arrives: it waits.

The reason is correctness, not freshness. This worker decides whether an
identity navigation is answered from cache and whether an API response is
cached at all, so a worker that activated mid-session -- what skipWaiting
plus clients.claim cause -- would swap those rules underneath a page
already holding tokens in memory, with a half-applied session. Neither
call appears in either source, in vite.config.ts, or in the emitted
worker and app chunks.

serviceWorker.ts is three statements and decides nothing: precache, a
NavigationRoute carrying T09's denylist by identity, and NetworkOnly as
the default handler. The denylist is passed rather than copied, so the
route cannot hold a filtered or stale version of the eleven paths. The
fallback binds to apiRequestPolicy's SHELL_DOCUMENT_URL, so it and the
cacheable set cannot drift apart. Workbox had to run in injectManifest
mode for any of that to be possible -- generateSW cannot import project
modules, which would have left both policy modules dead code.

There is deliberately no function that promotes a waiting worker. The
only way a page can is to message the worker so it calls
self.skipWaiting(), which is the call agents.md:91 forbids.

20 mutants, 20 caught. Two of them corrected claims of mine. Changing the
SPA base was caught by other guards, not by the AC7 precache case it was
aimed at: that case resolved manifest entries against a hardcoded /app/
and so could not fail at all, which is worse than the "guards a future
change" limit already recorded. It is now a named matcher exercised
against entries no build emits, and the test states that matching
unresolved entries matches nothing. And AC9 asserted the absence of a
translation call rather than the absence of copy -- a bare sentence would
have passed -- so it now also rejects any string literal containing a
space.

Suite 375/377 from a 314/316 baseline: 61 new cases, the same two
environmental T07 failures, nothing else moved.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/pwa/serviceWorker.ts` | **new**, the worker entry |
| `frontend/src/pwa/registerServiceWorker.ts` | **new**, the single registration site |
| `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts` | **new**, 61 cases |
| `frontend/src/pwa/apiRequestPolicy.ts` | one addition: `SHELL_DOCUMENT_URL`; no export removed or changed |
| `frontend/vite.config.ts` | `VitePWA({ strategies: 'injectManifest', srcDir, filename, injectRegister: null, manifest: false })` |
| `frontend/src/main.tsx` | one import, one call |
| `frontend/src/app/shellBuildsAndMounts.test.tsx` | **three edits** (T01), each justified below |
| `frontend/package.json`, `pnpm-lock.yaml` | four pinned packages |
| `.ai/prompts/frontend/T12/artifacts/*` | the fourteen phase artifacts |

**The T01 edits, since this is the first frozen test re-opened in this package.** Each keeps a guarantee rather
than dropping one:

1. `sw.js`/`manifest.webmanifest` absence **kept**, `serviceWorker.js` presence **added** — proof the plugin's
   `filename` took effect and that no web app manifest ships.
2. "no asset names `serviceWorker`" → "no asset names `skipWaiting` or `clients.claim`". The bundle may now name
   `navigator.serviceWorker`; what it may never do is promote a waiting worker.
3. The entry-shape assertion now expects two statements instead of one. **This was not predicted** — the plan
   argued registering from `main.tsx` would need only two edits because it leaves the register-spy assertion
   (`:103-116`) alone. That held; this assertion broke instead.

`navigationDenylist.ts` and the T09/T10/T11 test files are untouched. `.oxlintrc.json` is untouched — T07 asserts
it has no `overrides` and runs exactly two rules, so the worker avoids the restricted forms rather than being
exempted.

## Testing performed

| Check | Result |
|---|---|
| `vitest run src/pwa` | **228/228** (T09 61, T10 23, T11 83, T12 61) |
| `vitest run` (whole suite) | **375/377** |
| `tsc --noEmit` | clean |
| `oxlint` | exit 0, no violations |
| Mutation pass | **20 mutants, 20 caught** |
| `spec/frontend/agents.md`, `package.md` | **byte-identical** after the mutation pass |
| `vite build` | clean; worker emitted under `/app/`, no manifest |

The two failures are T07's, environmental, and unchanged: AC4's missing `oxlint` summary line and AC2's
`spawn pnpm ENOENT`. This task's own install used `npx -y pnpm@12.14.0`, which runs the version `packageManager`
pins without putting a binary on PATH, so AC2 is unaffected either way.

## Carried forward

1. **`segmentPrefixPattern` is still duplicated** between `navigationDenylist.ts` and `apiRequestPolicy.ts`. The
   frozen brief made collapsing it permitted but not required, and this task had no other reason to open T09's
   file. It is now the only T09–T11 carry-forward still open.
2. **A real-browser lifecycle test** belongs with the prompt UI: no jsdom test can show a browser refusing to
   promote a waiting worker. Playwright (L13) is the instrument.
3. **Gap 6's self-precache assertion is a guard, not a tested rule** — the plugin cannot put the worker in its own
   manifest, and unlike AC7's half I found no way to exercise it directly.
4. **`apiRequestPolicy.ts:46` lists `/app` in `SHELL_DOCUMENT_PATHS`, and `/app` returns 404** on the running
   stack. Harmless, and it is T11's constant and `design.md`'s route list — passed on, not changed.

## Two things for the owner

- **OQ-1 (needs a decision before the prompt UI, not before merge).** `tasks.md:21` says a new build "waits for a
  user reload". A refresh does **not** promote a waiting worker — the replacement document loads while the old one
  is still controlled — and the standard remedy is the `postMessage` → `self.skipWaiting()` pattern that
  `agents.md:91` forbids. This task's code is identical under every reading and obeys `agents.md`. The choice is
  what a prompt may honestly promise: *(recommended)* "the update applies next time you open the app"; or a reload
  button that may not work; or an ADR permitting a **user-initiated** `skipWaiting`, the only reading under which
  the task statement is literally true.
- **Spec traceability (no blocker).** `package.md:172` maps `serviceWorkerUpdateWaitsForReload` → **R57**, which is
  only about navigation and caching. No requirement in `requirements.md` states the update policy; the binding rule
  is `agents.md:91` plus `package.md:320`, and the suite guards both. An `R` for the update policy would make §8's
  mapping honest. Also unowned: `package.md:321-323`'s forced-update-for-rollback mechanism is cited by no task.

## Specification references

`tasks.md:21`; `agents.md:11`, `:87-92`; `design.md:44` (L9), `:46` (L10), `:47` (L11), `:49` (L12), `:51` (L13),
`:53` (L14), `:61-62` (L17), `:194` (§5), `:220` (§6); `requirements.md:203-205` (R57), `:206-207` (R58);
`package.md:99` (§8), `:172`, `:311-323` (§10); `infra/local/edge/Caddyfile:28-34`.
