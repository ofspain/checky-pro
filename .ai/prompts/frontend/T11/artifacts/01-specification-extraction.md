# frontend · T11 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim):**
> **API routes are NetworkOnly.** No API response is served from cache. Verifies
> `serviceWorkerNeverCachesApiResponses`. Cites R57, L17.

## Business Rules

- **R57 (`requirements.md:203-205`)** — cited by this task. Its WHEN is a *navigation*, which T09 and T10 pinned; the
  clause this task carries is the one `agents.md` states as the same rule's other half.
- **`agents.md:90`** — *"API routes use NetworkOnly."*
- **`agents.md:84-85`** — *"The service worker never answers an identity or API navigation from cache … Sensitive
  response bodies (account, token, receipt, evidence) are never cached."* This is the sharper requirement: not just
  "don't serve from cache", but **don't store** those bodies at all.
- **`design.md:194` (§5)** — the worker cache holds *"static shell and assets only. No API responses."*

## Locked Decisions

- **L17 (`design.md:61-62`)** — service-worker boundaries: the navigation paths bypass the fallback (T09/T10) and
  **API routes are NetworkOnly** (this task).
- **L9** — one origin: the SPA and every API are behind the same edge, so an API request is same-origin and can only
  be told apart from a shell request by its path (`design.md` §4c (2), the edge path map).
- **L14** — every SPA route is under `/app`; the twelve backend prefixes are disjoint from it.
- **L3 / L16 / `agents.md:78-83`** — tokens and PII are never persisted client-side; the Cache Storage API is
  client-side storage, so an API response in it is a storage violation, not only a staleness bug.
- **L12** — T01–T10 are consumed, not rewritten.
- **L6** — backend calls go through the generated client; the worker must not become a second path to the backend.

## Files involved

**To read:** `frontend/src/pwa/navigationDenylist.ts` (the pattern to follow, and the `mode !== 'navigate'` boundary
it leaves open); `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` (harness and conventions);
`frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:18-31` (`BACKEND_PREFIXES`, the existing copy);
`spec/frontend/design.md` §4c (2) and §5; `spec/frontend/agents.md`; `infra/local/edge/Caddyfile` (the live map).

**To create:** `frontend/src/pwa/apiRequestPolicy.ts` (name to be fixed in the brief) and
`frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts`.

**Must not modify:** `navigationDenylist.ts` and both T09/T10 tests, every T01–T08 file, `vite.config.ts`,
`.oxlintrc.json`, `spec/**`, `contracts/**`, `services/**`, `infra/**`.

## Dependencies

Vitest + jsdom; `spec/frontend/design.md` read from disk for the edge-map drift guard. No new package. No contract.
The policy must be a **pure** decision (no `fetch`, `caches`, `self`), so `.oxlintrc.json` stays untouched — the same
design that kept T09 clean.

## Acceptance Criteria

- **AC1 (`agents.md:90`, L17)** — a request to each of the twelve backend prefixes, and to a representative sub-path
  of each, is NetworkOnly: never answered from a cache, and its response never written to one.
- **AC2 (§5, `agents.md:84-85`)** — the SPA's own shell and static assets under `/app` **are** cacheable, so AC1 is
  not vacuously true: `/app/`, `/app/index.html`, `/app/assets/main-abc123.js`, `/app/assets/style-abc123.css`.
- **AC3 (`agents.md:84-85`)** — the sensitive bodies named in the rule are covered by name: an account response
  (`/accounts/me`), a token response (`/oauth2/token`), a receipt (`/api/v1/receipts/<uuid>`) and evidence
  (`/api/v1/...`), each NetworkOnly and never stored.
- **AC4 (method and mode independence)** — the decision does not depend on the HTTP method or on the request mode: a
  `GET`, a `POST` and a non-navigate mode to the same API path are all NetworkOnly. An API path is NetworkOnly even
  when the request *is* a navigation, which is the overlap with R57 and must agree with T09 rather than contradict
  it.
- **AC5 (the positive rule)** — anything that is neither an `/app` static asset nor a known backend prefix is
  NetworkOnly by default, so a thirteenth prefix added at the edge needs no change here to be safe.
- **AC6 (`/notifications`, SSE)** — the notification stream path is NetworkOnly, and the policy says so explicitly
  rather than by accident, because a buffered or replayed stream is broken, not merely stale.
- **AC7 (drift)** — the exported prefix list equals the edge path map in `design.md` §4c (2), parsed from the file,
  so a divergence between the spec's map and the worker's view fails here.
- **AC8 (purity, L3)** — the module references no `caches`, `fetch`, `self`, storage or cross-tab global, and holds
  no state.
- **AC9 (L12)** — the whole suite stays green, no T01–T10 file edited, and the navigation predicates still behave
  exactly as T09/T10 assert.

## Tests required

**`serviceWorkerNeverCachesApiResponses`** (new file in `frontend/src/pwa/`), groups AC1–AC8, using a
Workbox-semantics harness over a spied fake cache as T09 and T10 do — here simulating a *request* route rather than
a navigation fallback: a NetworkOnly decision must call neither `match` nor `put`, while a cacheable asset may.

**Mutation breaks:** drop a prefix from the list; invert the default (unknown paths become cacheable); make the
decision depend on the method; make `/app/assets` matching loose enough to swallow `/app/accounts`-style paths; let
`/notifications` be cacheable; cache an API response after a successful fetch (the harness's `put`).

## Open Questions

1. **State the rule positively or as a prefix list?** The task statement says "API routes use NetworkOnly", which is
   a list. `design.md` §5 says the cache holds "static shell and assets only", which is a default-deny. Recommending
   **default-deny plus the list as an explicit matcher set**: strictly stronger, fails safe when the edge map grows,
   and still gives task 12 concrete matchers. The gate should confirm going beyond the statement's literal words.
2. **`/notifications` is absent from R57 but present in the edge map.** T09 deferred this to T11 by rule. It is an
   API route, so it is NetworkOnly here. Recorded as the resolution of that open observation.
3. **Dev-only paths** `/app/@vite/client` and `/app/@react-refresh` are under `/app` and are not build assets. They
   never exist in a production build. Default: treat only `/app/assets/*` plus the shell as cacheable, which
   excludes them without naming them.
4. **Duplicating `BACKEND_PREFIXES`.** T02's copy is in a frozen test file. The L12-compatible option is a new
   exported constant in `src/pwa/` with the AC7 drift guard against `design.md`'s map; a later task that legitimately
   opens T02's file could then collapse the two.
5. **Does "never cached" need to constrain `put` as well as `match`?** `agents.md:84-85` says sensitive bodies are
   never cached, so yes — the test must assert nothing is *written*, not only that nothing is *read*. The pure policy
   cannot enforce that at runtime; task 12's wiring must, and that is a carry-forward.
