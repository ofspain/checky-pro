# frontend · T12 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

No design, no code in this phase.

## Business Rules

The header offered no requirement IDs and said to derive them. The honest result of deriving them is that
**`requirements.md` contains no requirement about the service-worker update policy.** Grepping it for *service
worker*, *offline*, *cache*, *reload*, *version*, *build identifier* and *install* returns exactly two lines: R54
(capability links) and R57 (navigation denylist). Neither is about updates.

| ID | One sentence | Applies how |
|---|---|---|
| **R57** | A navigation to an identity or backend path is never answered from cache and never gets the shell. | **Not the rule under test.** `package.md:172` maps `serviceWorkerUpdateWaitsForReload` → R57, but R57 says nothing about updates. R57 is nonetheless a *constraint*: registering a worker for the first time is what makes T09's and T11's policies real, so T12 must not register one that violates them. |
| **R58** | The production bundle contains no secret and no environment key outside the §4c public list. | Binds any build-identifier or version value the worker might carry, and is enforced mechanically by T01 `:121-124` over all of `src/**`. |

**The normative rule for this task is a LOCKED standing rule, not a requirement.** `agents.md:91-92`:

> The worker uses one worker-wide update policy: prompt-to-reload. `skipWaiting` and `clients.claim` are not used.
> Rollback is handled as stated in `package.md` §10.

restated at `package.md:320`. By `agents.md`'s own authority ("never restate or violate it") this is binding, so
the absence of a requirement ID does not weaken the task — it only means traceability runs to `agents.md:91` and
`package.md:320` rather than to an `R`. Logged as OQ-1.

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L17** (`design.md:61-62`) | The scoped decision. Its text covers navigation bypass + NetworkOnly; the update half of the worker's behaviour is in `agents.md`. T12 must not weaken either half while adding registration. |
| **L13** (`design.md:51`) | `vite-plugin-pwa` (Workbox) is the confirmed stack, "confirmed by the author". A hand-rolled worker is **not** an available option. |
| **L10 / R58** (`design.md:46`) | No environment key or secret in the bundle. |
| **L11** (`design.md:47`) | Anything user-facing — and "prompt to reload" is user-facing — must be keyboard-operable, announce itself through a live region, meet WCAG AA, and take every string from the catalogue. |
| **L12** (`design.md:49-50`) | Phase additivity: spec phases 2–5 never replace the Phase 1 shell. T12 is itself Phase 1 Foundation, so editing T01's shell test is not an L12 violation — but it is the first frozen-test edit in the package and must be surgical. |
| **L14** (`design.md:53`) | Every SPA route lives under `/app`; worker **scope** must not exceed what the edge routes to the SPA. |
| **L9** (`design.md:44`) | Single origin through the edge; the worker sees only what Caddy routes to `/app*`. |
| **L6** | Backend calls go through `@themistra/api-client`; enforced by two oxlint rules that ban `fetch`/`XMLHttpRequest`. |
| **L3 / L16** (`design.md:13`, `:58-60`) | Tokens in memory only; sessionStorage limited to the stated carve-out. A worker must add no new client-side storage beyond the shell/asset cache (`design.md:194`). |

## Files involved

**To read, then extend:**

| File | Why |
|---|---|
| `frontend/src/pwa/navigationDenylist.ts` | `NAVIGATION_DENYLIST` must be **imported** by the registration/worker wiring, never restated (carried forward from T09). |
| `frontend/src/pwa/apiRequestPolicy.ts` | `NETWORK_ONLY_PATH_PREFIXES` likewise (carried forward from T11), plus `isCacheableRequest`. |
| `frontend/vite.config.ts` | where the plugin is wired; `base: '/app/'` already set. |
| `frontend/package.json` | `vite-plugin-pwa` must be added (see OQ-2). |
| `frontend/index.html` | the single HTML entry. |
| `frontend/src/main.tsx` | today a single `mountShell` call; a registration call site is the natural place. |

**To edit (frozen, T01) — `frontend/src/app/shellBuildsAndMounts.test.tsx`:**

| Line | Assertion | Why it must change |
|---|---|---|
| `:75-76` | `sw.js`, `manifest.webmanifest` absent from `dist/` | the build now emits a worker |
| `:77-80` | no built asset contains `serviceWorker` | registration names `navigator.serviceWorker` |
| `:103-116` | mounting registers no worker | only if registration happens inside the mounted tree |

**New:** a worker source under `src/pwa/` (`design.md:220`), and
`frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts`.

## Dependencies

- **Packages:** `vite-plugin-pwa` and its `workbox-*` transitives (absent today — OQ-2). No runtime dependency added
  to the app itself.
- **Config keys:** none. Nothing may be read from `import.meta.env` (R58, T01 `:121-124`).
- **Contracts:** none. This task touches no API surface, so `contracts/api/*.yaml` are not involved.
- **Browser APIs:** `navigator.serviceWorker.register`, the registration lifecycle
  (`installing`/`waiting`/`active`, `updatefound`, `statechange`), and — only if a prompt is in scope —
  `postMessage`/`controllerchange`. Explicitly **not** `skipWaiting` and **not** `clients.claim`.
- **Existing modules:** the two `src/pwa/` policy modules; `src/i18n/catalogue.ts` if any copy is introduced.

## Acceptance Criteria

Traced to `agents.md:91-92` and `package.md:320` (the normative source), with L/R IDs where they bind.

| # | Criterion | Traces to |
|---|---|---|
| **AC1** | The worker source calls neither `skipWaiting()` nor `clients.claim()`, in any spelling, and the built worker bundle contains neither. Asserted against the real emitted artifact, not only the source. | `agents.md:91`, `package.md:320` |
| **AC2** | The plugin is configured so the generated worker does not self-activate: no `skipWaiting`-equivalent option is enabled, and the registration does not auto-reload. A new build therefore reaches `waiting` and stays there. | `agents.md:91` |
| **AC3** | A new build is **not** applied to an open client until the user reloads: while a worker is `waiting`, the controlling worker is still the old one. | `agents.md:91` (*"a new build waits for a user reload"*, `tasks.md:21`) |
| **AC4** | One worker-wide policy: there is exactly one worker, one registration site, and no per-route or per-asset update behaviour. | `agents.md:91` (*"one worker-wide update policy"*) |
| **AC5** | Worker **scope** does not exceed `/app/`, so no denylisted navigation can reach it even in principle. | L14, L9, R57 |
| **AC6** | The registered worker honours T09 and T11: the navigation fallback carries `NAVIGATION_DENYLIST` as its denylist, and the request side is `NetworkOnly` for everything not cacheable. Both lists are **imported**, not restated. | R57, L17 |
| **AC7** | **The precache manifest contains no URL that matches `NAVIGATION_DENYLIST`**, *and* `/app/signed-out` is still answered from the shell. (Both halves, because a precache route matches by URL ahead of the navigation fallback — the hole T09/T10 identified and could not close.) | R57, L17, T09/T10 carry-forward |
| **AC8** | No environment key and no absolute URL in the new source; nothing new is persisted client-side; the cache holds static shell and assets only. | R58, L10, L3, L16, `design.md:194` |
| **AC9** | If a reload prompt is implemented (pending OQ-3), every string comes from the i18n catalogue and the prompt is keyboard-operable with a live-region announcement. If it is not implemented, the task states plainly that the policy is realised without UI and names the task that owes the UI. | L11 |
| **AC10** | A drift guard fails if `agents.md`'s update sentence (`:91`) or `package.md` §10's update bullet (`:320`) is softened, deleted, or starts permitting `skipWaiting`. | pattern from T09–T11 |
| **AC11** | The suite is green against the known baseline, and the only pre-existing files edited are T01's three absence assertions — each edit justified in the artifact. | L12, T01 |

## Tests required

**Named test (`package.md:172`):** `serviceWorkerUpdateWaitsForReload` → file
`frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts`.

Boundary tests implied by the acceptance criteria:

1. **Source and bundle scan** for `skipWaiting` / `clients.claim`, including the `self.skipWaiting()`,
   `self.addEventListener('install', () => self.skipWaiting())` and `clients.claim()` spellings, and the plugin
   options that would inject them (AC1, AC2).
2. **Lifecycle simulation** with a fake registration: install a second worker, assert it sits in `waiting`, assert
   the controller is unchanged, then assert that a reload — and only a reload — promotes it (AC3).
3. **Exactly one registration site**, found by scanning `src/**` rather than asserted about one known file (AC4).
4. **Scope assertion** on the registration call and on the emitted worker's URL (AC5).
5. **Composition with T09/T11**: import both predicates and assert the generated worker's config is derived from
   them, with an identity check that the generated denylist equals `NAVIGATION_DENYLIST` (AC6).
6. **Precache manifest, both halves**: no entry matches `NAVIGATION_DENYLIST`; `/app/signed-out` still resolves to
   the shell (AC7).
7. **Real-build assertions** over `dist/`, in T01's style (temp dir, real `vite build`) — the only way AC1, AC5 and
   AC7 can be checked against what ships (AC1, AC5, AC7).
8. **Drift guards** over `agents.md:91` and `package.md:320` (AC10).
9. **T01's edited file still passes** in full, with its remaining absence assertions intact (AC11).

## Open Questions

- **OQ-1 — the named test is mapped to a requirement it does not test (traceability, not a blocker).**
  `package.md:172` maps `serviceWorkerUpdateWaitsForReload` → **R57**, but R57 is only about navigation and caching;
  no requirement states the update policy. The rule is real and LOCKED in `agents.md:91`, so T12 proceeds and traces
  to `agents.md:91` + `package.md:320`, citing L17 as `tasks.md:21` instructs. **Owner may wish to add an `R` for
  the update policy** so §8's mapping is honest. Not a blocker; no spec file is modified by this task.

- **OQ-2 — `vite-plugin-pwa` cannot be installed on this host. Blocks phase 6, not phases 1–5.**
  L13 names it as confirmed stack. It is absent from `frontend/package.json`, from `pnpm-lock.yaml` (no
  `vite-plugin-pwa`, no `workbox-*`) and from the local store. The registry is reachable (HTTP 200), but **`pnpm`
  is not installed**: `packageManager` declares `pnpm@12.14.0`, `~/Library/pnpm/store` exists, and no `pnpm` or
  `corepack` binary is on PATH in any shell — only `npm`, whose lockfile would be wrong for this workspace. Owner
  input: install `pnpm`, or run the install inside the `frontend` container, or accept an `npm`-written lockfile.
  This is also why T07's AC2 end-to-end case fails today (`spawn pnpm ENOENT`).

- **OQ-3 — does T12 own the reload *prompt*, or only the *policy*?** The task statement is "the worker never calls
  `skipWaiting` or `clients.claim`, and a new build waits for a user reload" — satisfiable with no UI at all, since
  the default lifecycle already waits. "Prompt-to-reload" names a UI, no requirement describes one, no later task
  in `tasks.md` claims one, and L11 would impose catalogue strings, focus management and a live region on it. A
  defensible reading is that T12 owns the policy and the registration plumbing plus a *programmatic* prompt hook,
  and the visible prompt lands with the first real screens. **Decision needed at the phase 4 gate** — it is the
  difference between a pure-plumbing task and a UI task.

- **OQ-4 — does T12 own "Forced update for rollback" (`package.md:321-323`)?** §10 has five bullets; the task cites
  §10 as a whole but states only the update-policy bullet. The remote configuration document, the build-time flag,
  the kill switch and the build-identifier denylist that **forces** a reload are cited by **no task in
  `tasks.md`** (grep for *remote config*, *kill switch*, *build identifier*, *flag* returns only task 12 and three
  unrelated Phase-2 payment tasks). `agents.md:92` says only "Rollback is handled as stated in `package.md` §10",
  which reads as a pointer. Note the tension: a forced reload is the **opposite** of waiting for a user reload, and
  the forced half additionally needs a build identifier in the bundle, which R58 and T01 `:121-124` constrain.
  **Recommended scope: the update policy only**, with the forced-rollback half recorded as unowned spec surface for
  the owner to assign. Phase 3 should challenge this.

- **Not blockers:** no `package.md` §11 open question (Q1–Q15) touches this task — they concern MFA, payments,
  notifications, Phase 2–5 APIs, refresh tokens, design system, i18n, analytics, capability links, auth contract,
  bootstrap, admin views and revocation. None gates T12.

## References

`tasks.md:21`; `agents.md:11`, `:87-92`; `design.md:44` (L9), `:46` (L10), `:47` (L11), `:49` (L12), `:51` (L13),
`:53` (L14), `:13` (L3), `:58-60` (L16), `:61-62` (L17), `:194` (§5), `:220` (§6); `requirements.md:203-205` (R57), `:206-207`
(R58); `package.md:99` (§8), `:172`, `:311-323` (§10), `:325+` (§11);
`frontend/src/app/shellBuildsAndMounts.test.tsx:75-80`, `:103-127`;
`frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts:194-195`.
