# frontend · T10 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim):**
> **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies
> `endSessionNavigationBypassesServiceWorker`. Cites R57.

## Business Rules

- **R57 (`requirements.md:203-205`)** — a navigation to an identity or backend path, `/connect/logout` among the
  eleven, is never answered from cache and never receives the application shell.
- **R19 (`requirements.md:62-65`)** — on sign-out the SPA clears in-memory token state and ends the SAS session
  through the OIDC end-session endpoint with an allowlisted `post_logout_redirect_uri`. Its own words: *"The
  end-session navigation is served by SAS, not the SPA shell (R57)."* That sentence is this task's subject.
- **R22 (`requirements.md:72-75`)** — sign-out propagates to every open tab, and the SPA never shows a
  half-authenticated screen. A cached end-session response produces exactly that half state.
- **`agents.md:35-36`** — sign-out ends the SAS session through the end-session endpoint with an allowlisted
  redirect, clears in-memory token state, and propagates to every tab.
- **`agents.md:88-89`** — the eleven denylisted navigation paths.

## Locked Decisions

- **L17 (`design.md:61-62`)** — service-worker boundaries.
- **L3 / `design.md:191`** — tokens live in memory only, cleared on sign-out and tab close. The end-session URL
  carries an ID token in `id_token_hint`, so caching that navigation would persist a token outside memory.
- **`design.md` §5 (`:194`)** — the worker cache holds static shell and assets only; no API or identity responses.
- **L14** — every SPA route is under `/app`; `/app/signed-out` is a public route
  (`design.md:139`, `frontend/src/app/routes.tsx:16`).
- **L18 (`design.md:63`)** — tabs share renewal and sign-out.
- **L12** — phase additivity; T01–T09 are consumed, not rewritten.

## Facts established from source

- The end-session URL shape, from the backend's own green test
  (`services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java:519-527`):
  `GET /connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=<allowlisted>&state=<s>`.
- Its response: `302`, `Location: <post_logout_redirect_uri>?state=<s>` (`:488-490`), with the allowlisted default
  `http://localhost:5173/app/signed-out` (`services/auth/src/main/resources/application.properties:53`).
- A non-allowlisted target is `400` and the session survives (`:501-517`).
- T09's `isDenylistedNavigation` reads `url.pathname` only (`navigationDenylist.ts:55-58`), so **no query string can
  change the decision** — which is why T10 is expected to need no production change.

## Files involved

**To read:** `frontend/src/pwa/navigationDenylist.ts`; `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts`
(for the harness and the conventions); `frontend/src/app/routes.tsx` (the `signed-out` public route);
`spec/frontend/agents.md`.

**To create:** `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`.

**Expected to modify:** nothing. **Must not modify:** `navigationDenylist.ts` and T09's test (L12), every T01–T08
file, `vite.config.ts`, `.oxlintrc.json`, `spec/**`, `contracts/**`, `services/**`, `infra/**`.

## Dependencies

Vitest + jsdom; `spec/frontend/agents.md` read from disk if a drift guard is wanted. No new package, no contract, no
config key. No `ServiceWorkerGlobalScope`/`caches` stub — the same pure-decision plus harness approach as T09.

## Acceptance Criteria

- **AC1 (R57, R19)** — a navigation to the full end-session URL, with `id_token_hint`, `post_logout_redirect_uri`
  and `state`, is declined: the network sees it, the cache is never read, and the shell is never returned.
- **AC2 (L3, §5)** — the ID token never reaches a cache: no cache read or write occurs with any key containing the
  token, `id_token_hint`, or the end-session path, for any of the URL shapes in AC4.
- **AC3 (R19, L14)** — the redirect target **is** the shell's: `/app/signed-out` and `/app/signed-out?state=s1` are
  answered with the shell, so sign-out still renders.
- **AC4 (R57, R19)** — shape invariance: with all three parameters, with none, with only `id_token_hint`, with a
  trailing slash, with a percent-encoded `post_logout_redirect_uri`, and with a `post_logout_redirect_uri` pointing
  at a foreign origin (the `400` case) — every one is declined, and none is cached.
- **AC5 (R19)** — the two-step flow in order: navigation 1 (`/connect/logout?…`) declined with no cache access,
  navigation 2 (`/app/signed-out?state=s1`) answered from the shell, and the only cache key ever used is the shell's.
- **AC6 (R22, L18)** — the same end-session navigation is declined identically when it is repeated, so a second tab
  signing out is not served a cached response from the first tab's attempt.
- **AC7 (drift)** — `/connect/logout` is still the end-session path in `agents.md`'s denylist and still exported by
  `DENYLISTED_NAVIGATION_PATHS`, and `signed-out` is still a public route in `routes.tsx`.
- **AC8 (L12)** — the whole frontend suite stays green with no T01–T09 file edited.

## Tests required

**`endSessionNavigationBypassesServiceWorker`** (new file, `frontend/src/pwa/`), groups AC1–AC7, using a
Workbox-semantics harness over a spied fake cache as T09 does, with a realistic JWT-shaped `id_token_hint` value.

**Mutation breaks:** remove `/connect/logout` from the list; make the decision read `url.href` or `url.search`
instead of `url.pathname`; make the shell fallback answer `/connect/logout`; make the harness read the cache before
the denylist check; make the shell matcher reject `/app/signed-out`.

## Open Questions

1. **Is T10 test-only?** Everything above is already satisfied by T09's module, because the predicate reads
   `pathname` and `/connect/logout` is in the list. Default: **yes, cases only, no production change** — the T08
   precedent over T07's module. The task then *demonstrates* R19's dependency rather than implementing new policy,
   and that should be stated plainly rather than dressed up.
2. **Duplicate the harness or share it?** Sharing means exporting it from T09's frozen test file (L12 friction);
   duplicating means ~12 lines repeated. Leaning to a local copy with a comment pointing at T09's, and a note that
   task 12 replaces both with the real route.
3. **The precache hole is closest to the surface here** — `/app/signed-out` is a shell route that will be precached,
   and a precache route matches by URL ahead of the navigation fallback. The guard still belongs to task 12 (it owns
   the manifest); T10 should restate the carry-forward rather than let it drift.
4. **Does the SPA's sign-out use a full-page navigation?** R19 implies it (the browser must reach SAS and follow a
   302), and only a navigation is a navigation request the worker sees. The sign-out control itself is a later task,
   so T10 asserts the policy for a navigation and records the assumption.
