# frontend · T12 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **frozen; phases 5–13 may use nothing else** |

The phase 2 brief stands as written, amended by the decisions below. Where a decision contradicts the brief, the
decision wins.

## Disposition of Kimi's phase 3 findings

| # | Finding | Disposition |
|---|---|---|
| 1 | `pnpm` missing blocks phase 6 | **Closed, not escalated** — see D-0 |
| 2 | `generateSW` cannot import project modules; need `injectManifest` | **Accepted** — D-1. The strongest finding in the review; it is the difference between AC6 being met and the T09/T11 modules being dead code |
| 3 | Worker scope not explicitly configured | **Accepted** — D-2 |
| 4 | Registration API shape undefined | **Accepted in part; one function rejected** — D-3 |
| 5 | Navigation-fallback wiring unspecified | **Accepted, with a correction** — D-4 |
| 6 | Shell precache key may not be `/app/index.html` | **Accepted** — D-5 |
| 7 | T01 edits need their new expected values | **Accepted** — D-6 |
| 8 | Plugin may emit a `manifest.webmanifest` T01 forbids | **Accepted** — D-7 |
| 9 | Forced-update/rollback cited but out of scope | **Accepted** — D-8 |
| 10 | No design for keeping the worker lint-clean | **Accepted** — D-9 |

Nothing in the review was rejected on its premise. One recommendation inside finding 4 is rejected because it
violates the rule the task exists to enforce, and finding 5's snippet is incomplete against AC6. Both are set out
below. The review also did not reach the task's central technical problem, which is **OQ-1**.

## Decisions

### D-0 — OQ-C is closed. The install path is `npx -y pnpm@12.14.0`.

`pnpm` is not installed on this host and nothing global will be installed to fix that. `npx -y pnpm@12.14.0`
resolves and runs **the exact version `package.json`'s `packageManager` field pins** (verified: prints `12.14.0`),
so the workspace's own package manager writes the lockfile and no `npm`-authored lockfile is introduced. Every
install step in phases 6+ uses that invocation. No owner action is required.

This does **not** fix T07's AC2 end-to-end case, which shells out to bare `pnpm` from inside the test. That remains
an environmental failure of a frozen T07 test and is out of scope here; the baseline stays **314/316**.

### D-1 — `injectManifest`, pinned versions.

`vite-plugin-pwa` is configured with `strategies: 'injectManifest'`, `srcDir: 'src/pwa'`,
`filename: 'serviceWorker.ts'`, and `injectRegister: null` (registration is written by hand, per D-3). `generateSW`
is **not** used: it builds the worker from a config object and cannot import `navigationDenylist.ts` or
`apiRequestPolicy.ts`, which would make AC6 unsatisfiable.

Pinned, exact, no ranges — matching this repo's convention:

| Package | Version | Why |
|---|---|---|
| `vite-plugin-pwa` | `2.0.0` | latest; peer `vite: ^8.0.0` covers this repo's `vite@8.3.5`; depends on `workbox-build@^7.4.1` |
| `workbox-routing` | `7.4.1` | the worker source's own imports; matches the plugin's workbox line |
| `workbox-strategies` | `7.4.1` | ″ |
| `workbox-precaching` | `7.4.1` | ″ |

All four go in `devDependencies`: the worker is a build artifact, and nothing in the app's runtime graph imports
them. Phase 6 must confirm `2.0.0` still exposes `strategies`/`srcDir`/`filename`/`injectManifest`/`manifest` under
those names before writing config against them — registry metadata proves the version and peer range, not the
option names.

### D-2 — Scope is `/app/`, stated explicitly and asserted.

Registration passes `{ scope: '/app/' }` rather than relying on the emitted script's directory. The test asserts
the registration's scope is exactly `/app/`. Rationale, carried from T09: the edge routes only `/app*` to the SPA
(`Caddyfile:28`), so an `/app/`-scoped worker can never see `/login` or `/oauth2/...` at all. Scope is the first
line of defence behind the denylist, and making it implicit invites a silent widening later.

### D-3 — The registration API, with `skipWaitingAndReload` **rejected**.

Frozen surface of `src/pwa/registerServiceWorker.ts`:

```ts
export function registerServiceWorker(): Promise<ServiceWorkerRegistration | undefined>;
export function onUpdateWaiting(listener: () => void): () => void;  // returns an unsubscribe
```

`registerServiceWorker` is idempotent, resolves `undefined` when `navigator.serviceWorker` is absent (jsdom,
insecure origins), and never throws into a caller.

**Kimi's third function, `skipWaitingAndReload()`, is rejected.** The only way a page can promote a waiting worker
is to message it and have the worker call `self.skipWaiting()`. `agents.md:91` says `skipWaiting` **is not used**,
and AC1 asserts the string appears nowhere in the source or the emitted bundle. Shipping that function would mean
implementing the exact call this task exists to forbid. It is not a detail to settle in phase 5 — it is excluded.

A consequence follows for whoever builds the prompt UI, recorded in OQ-1.

### D-4 — Navigation fallback and request policy, both derived from the existing modules.

The worker source is, in shape:

```ts
import { precacheAndRoute, createHandlerBoundToURL } from 'workbox-precaching';
import { NavigationRoute, registerRoute, setDefaultHandler } from 'workbox-routing';
import { NetworkOnly } from 'workbox-strategies';
import { NAVIGATION_DENYLIST } from './navigationDenylist';
import { SHELL_DOCUMENT_URL /* or equivalent single constant, see D-5 */ } from './apiRequestPolicy';

precacheAndRoute(self.__WB_MANIFEST);
registerRoute(new NavigationRoute(createHandlerBoundToURL(SHELL_DOCUMENT_URL), { denylist: NAVIGATION_DENYLIST }));
setDefaultHandler(new NetworkOnly());
```

**Correction to Kimi's snippet.** It imports only `NAVIGATION_DENYLIST`, which leaves AC6's second half unmet —
nothing would tie the worker to T11. `setDefaultHandler(new NetworkOnly())` *is* T11's default-deny rule, but as a
coincidence rather than a derivation. The worker therefore also imports from `apiRequestPolicy.ts`, and the test
asserts the tie both ways:

- the `NavigationRoute`'s denylist **is** `NAVIGATION_DENYLIST` (identity, `toBe`, not a deep copy);
- the shell URL the fallback is bound to is derived from `apiRequestPolicy`'s shell constants, not a literal;
- every non-cacheable request reaches the `NetworkOnly` default handler, driven by `isNetworkOnlyRequest` over the
  twelve prefixes.

`precacheAndRoute` is registered **before** the navigation route, which is the ordering that makes AC7's first half
load-bearing: a precache route matches by URL ahead of the navigation fallback, so a denylisted URL inside the
manifest would be served from cache no matter what the denylist says. This is the hole T09 and T10 named and could
not close; T12 closes it with an assertion over the emitted manifest.

### D-5 — One shell-URL constant, asserted present in the manifest.

The shell URL used by `createHandlerBoundToURL` is a single exported constant, and the test asserts the emitted
precache manifest contains exactly that URL. Phase 6 determines its real value from the actual build output rather
than assuming `/app/index.html`; whatever it is, the constant and the assertion read the same source of truth. A
mismatch here fails closed and loudly, which is the point.

### D-6 — The exact T01 edits. Three, not four.

In `frontend/src/app/shellBuildsAndMounts.test.tsx`, nothing outside these three sites changes:

1. **`:75-76`** — `expect(existsSync(join(outDir, 'sw.js'))).toBe(false)` becomes an assertion that the worker
   **is** emitted, at the name the plugin produces (`serviceWorker.js`, per D-1's `filename`). The companion line
   `expect(existsSync(join(outDir, 'manifest.webmanifest'))).toBe(false)` is **kept as it stands**, because D-7
   disables manifest emission.
2. **`:77-80`** — the loop asserting no built asset contains `serviceWorker` is narrowed, not deleted: the
   registration module legitimately names `navigator.serviceWorker`, so the assertion becomes that no asset
   contains `skipWaiting` or `clients.claim`. T01 keeps a real guarantee instead of losing one.
3. **`:103-116`** — the `register` spy assertion. Whether this changes **depends on where registration is called**,
   which phase 5 settles: if registration lives in `src/main.tsx`, outside `mountShell`, T01's test never triggers
   it and **the assertion stays untouched** — the preferred outcome. Only if registration is moved inside the
   mounted tree does it invert. Phase 6 must state which happened.

The comment at `:74` (*"No worker yet (T09-T12 own it)"*) is updated to say what is now true.

T01's other assertions — the `/app/assets/...` script sources, the dev-server config, the empty shell, no `fetch`,
no `setItem`, the `import.meta.env`/absolute-URL scan over all of `src/**`, the single-`mountShell` entry — are
untouched and must stay green. The `src/**` scan now covers the new worker source automatically, which is the
intended enforcement of R58 and costs nothing as long as the worker names no URL and reads no env key.

### D-7 — `manifest: false`.

The plugin emits no web app manifest. Manifest contents, icons and the install experience are not this task, and
disabling emission keeps T01's `manifest.webmanifest` assertion intact instead of re-opening it. An installable PWA,
if wanted, is a later task with its own acceptance criteria.

### D-8 — Locked scope statement, to be carried in the source header.

> T12 implements only the prompt-to-reload update policy. The forced-update-for-rollback mechanism of
> `package.md:321-323` — the remote configuration document, the build-time flag, the kill switch, and the
> build-identifier denylist — is **unowned by any task in `tasks.md`** and is out of scope here. The visible reload
> prompt is also out of scope; this task ships the policy and a programmatic hook.

### D-9 — The worker source's lint contract.

The worker source uses only Workbox's typed APIs and the two policy imports. It names no `fetch`, no
`XMLHttpRequest`, no `self.fetch`. Workbox's own network call lives in `node_modules`, which `pnpm run lint` does
not scan (`oxlint --no-ignore -c .oxlintrc.json src`). `.oxlintrc.json` is **not** modified: T07 asserts it has no
`overrides` key and that the run uses exactly 2 rules. The worker will need a `/// <reference lib="webworker" />`
and a `self` typed as `ServiceWorkerGlobalScope` for `tsc`; neither introduces a restricted form.

## Amended acceptance criterion

**AC3 is reworded.** It previously read *"only a reload promotes it"*. That is not how the lifecycle works and would
have frozen a false statement into the test:

> **AC3.** While a new worker is `waiting`, the controlling worker is still the previous one, and nothing in the
> app promotes it: no `skipWaiting`, no `clients.claim`, no automatic reload, no message that would cause either.
> The update applies on the next load at which no client controlled by the old worker survives.

A plain refresh is *not* sufficient to activate a waiting worker, because the replacement document begins loading
while the old one is still controlled — the overlap keeps the old worker active. Activation requires every
controlled client to be gone, or `skipWaiting()`, which is forbidden. The test asserts what is assertable in jsdom:
the forbidden calls are absent, and while a worker waits the controller does not change.

All other acceptance criteria (AC1, AC2, AC4–AC11) stand as written in phase 2, with AC6 and AC7 read through D-4
and D-5, and AC11's baseline confirmed at **314/316**.

## Open Questions

- **OQ-1 — "a new build waits for a user reload" is not achievable by a reload, and the only mechanism that would
  make it so is forbidden. Logged, not silently resolved; does not block phases 5–6.**
  `tasks.md:21` says a new build "waits for a user reload"; `agents.md:91` forbids `skipWaiting` and
  `clients.claim`. Those two cannot both be satisfied in the usual sense, because a refresh does not promote a
  waiting worker — the standard fix is precisely the forbidden `postMessage` → `self.skipWaiting()` pattern (the
  reason Kimi's `skipWaitingAndReload` is rejected in D-3).

  **This task's code is identical under every reading**, so implementation proceeds: no `skipWaiting`, no
  `clients.claim`, wait in `waiting`. What differs is only what a future prompt may promise the user:
  1. *(recommended, and what this task assumes)* the update applies on the next cold load — all tabs closed, or the
     last controlled client gone. A future prompt says so honestly ("the update applies next time you open the
     app") rather than offering a button that cannot work.
  2. a prompt whose button reloads, accepting that the update may not apply until the tab is closed. Misleading.
  3. an ADR amending `agents.md:91` to permit a **user-initiated** `skipWaiting` — the only reading under which
     "waits for a user reload" is literally true, trading the LOCKED rule's absolute guarantee for one scoped to
     explicit user consent.

  Owner decision, needed before the prompt UI is built, **not** before this task ships. Flagged here because the
  guardrails require logging a LOCKED decision that looks wrong rather than deviating from it, and because
  `agents.md` is authoritative: this task obeys it.

- **No blockers.** OQ-A (prompt UI out of scope) and OQ-B (forced rollback out of scope) are locked by D-8. OQ-C is
  closed by D-0. No `package.md` §11 question (Q1–Q15) touches this task.
