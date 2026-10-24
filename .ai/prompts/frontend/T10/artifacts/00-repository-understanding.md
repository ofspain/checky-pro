# frontend · T10 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim):**
> **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies
> `endSessionNavigationBypassesServiceWorker`. Cites R57.

## 1. What already exists, and the overlap question

T09 shipped `frontend/src/pwa/navigationDenylist.ts` — the eleven `agents.md:88-89` paths as a verbatim list, a
derived `RegExp[]`, and `isDenylistedNavigation` / `isShellNavigation` — plus 61 cases in
`sasNavigationBypassesServiceWorker.test.ts`. **`/connect/logout` is one of those eleven**, and T09 already asserts
that a navigation to it is declined and not answered from cache (`:80`, `:89`, `:112`, `:145`).

So the first thing to establish is what T10 adds beyond a duplicate row. Three things, and they are not small:

1. **The real end-session URL carries an ID token in its query string.** The backend's own passing integration test
   builds it as `/connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=…&state=…`
   (`services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java:519-527`). A worker that
   cached that navigation would write **an ID token into Cache Storage**, which outlives the tab and is readable by
   any later script in scope — a direct breach of L3 (tokens in memory only) and `design.md` §5. T09 pinned the path;
   it never pinned a URL with a token in it, and its cache assertions key on the path alone.
2. **The flow is two navigations, and the second one *is* the shell's.** SAS answers end-session with `302` to
   `post_logout_redirect_uri + '?state=…'`, i.e. `/app/signed-out?state=s1`, and `/app/signed-out` is a **public SPA
   route** (`frontend/src/app/routes.tsx:16`). So the correct behaviour is: navigation 1 bypasses the worker
   entirely, navigation 2 is answered by the shell. Nothing in T09 exercises that crossing, and getting it wrong in
   either direction breaks sign-out — serve navigation 1 from cache and the SAS session survives while the user is
   shown the signed-out page; decline navigation 2 and offline sign-out stops rendering.
3. **A cached end-session response is the one failure that silently lies to the user.** Every other denylisted path
   fails loudly (sign-in breaks, an API call 404s). Here the user sees "you are signed out" while their SAS session
   is alive, which is exactly what R19 and `agents.md:35-36` exist to prevent.

## 2. The end-session flow, as proven on the backend

`SasLoginIntegrationTest.endSessionEndsTheSasSessionAndReturnsToTheSignedOutRoute` (`:474-497`) is green and gives
the shape T10 should use verbatim rather than invent:

- Request: `GET /connect/logout?id_token_hint=<id_token>&post_logout_redirect_uri=<allowlisted>&state=s1`
- Response: `302` with `Location: <post_logout_redirect_uri>?state=s1`
- The allowlisted default is `http://localhost:5173/app/signed-out`
  (`services/auth/src/main/resources/application.properties:53`).
- A target outside the allowlist is a `400` and the session survives (`:501-517`) — a failure the SPA can be shown,
  and one more reason the response must never be cached.

## 3. Constraints carried in from T09's gate

- **D1 — no worker is registered.** Tasks 9–11 are policy; task 12 wires `vite-plugin-pwa`, emits `sw.js` and edits
  T01's three absence assertions (`shellBuildsAndMounts.test.tsx:74-80, 103-116`). T10 keeps that.
- **D5 — the Workbox-semantics harness** lives in T09's test file. T10 needs the same semantics; whether it imports
  one or writes its own is a phase 2 question.
- **The precache carry-forward.** T09's phase 13 records that a Workbox precache route matches by URL *ahead* of the
  navigation fallback. `/app/signed-out` will be a precached shell route, so this is where the hole is closest to the
  surface — but the guard still belongs to task 12, which owns the manifest.

## 4. Precedent for a test-only task

T08 was cases-only over T07's module and that was the right call; T10 looks the same over T09's module, since
`isDenylistedNavigation` already returns `true` for every end-session URL shape (the predicate reads `pathname`, so
the query string cannot change it). If that holds under phase 1's scrutiny, T10's deliverable is
`endSessionNavigationBypassesServiceWorker` and **no production change** — which is a result, not a shortfall: it
demonstrates T09's policy covers the flow R19 depends on.

## 5. What I could not settle here

1. **Does T10 add production code at all?** Default: no. The one thing that might justify some is an explicit
   "never cache a URL carrying a token" assertion that is stronger than "this path is denylisted" — but any such
   check would belong to the task that owns cache writes (task 11/12), not here.
2. **Where the test file lives and whether the harness is shared.** Duplicating `simulateNavigationFallback` in a
   second file is the honest small cost of keeping T09's file frozen; exporting it from a test helper would touch
   T09's file, which L12 discourages. Phase 2 question.
3. **Whether to assert the `400` (non-allowlisted target) case.** It is a real backend behaviour and an argument
   against caching, but the SPA never constructs that URL. Leaning to one case for the reason, not the flow.
