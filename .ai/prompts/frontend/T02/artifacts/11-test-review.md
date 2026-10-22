# frontend · T02 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T02 — /app prefix |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **/app prefix.** Every SPA route resolves under `/app`, and a path outside it is not served by the SPA. Verifies `routesResolveUnderAppPrefixOnly`. Cites L14.

---

## Method

Reviewed the Phase 10 test manifest for `routesResolveUnderAppPrefixOnly` (6 cases + mutation breaks) against the frozen T02 acceptance criteria (AC1–AC6) and the named test requirement in `package.md` §8. No code was rewritten.

---

## Gaps

### 1. No automated test for query-string preservation on `/app` routes
**Gap ·** AC5 explicitly lists `/app/verify?token=x` as a live check, but none of the six automated cases exercises a path with a query string. Case 1 matches `verify` at `/app/verify`, but the query string is the part that makes the live email-link scenario realistic.  
**Why it matters ·** A regression in how React Router strips or rejects query strings on basename-prefixed URLs would only be caught by the live AC5 check, not by the unit suite.  
**Suggested test ·** Add a case (or extend case 1) that asserts `matchRoutes(appRoutes, '/app/verify?token=abc', APP_BASENAME)` resolves to the `verify` leaf, and that the `token` query parameter is preserved in the location object.

### 2. No assertion that the bare basename root `/app/` renders nothing
**Gap ·** The frozen brief finding 7 (and AC4) states that `/app/` is not a route and renders nothing. None of the six cases targets the bare basename root. Case 4 uses `/app/no-such-route`, and case 2 uses paths without `/app`.  
**Why it matters ·** A future change to the root route shape could accidentally make `/app/` render something or match a leaf. T41 will add a redirect later, but until then the behavior should be pinned.  
**Suggested test ·** Add a case that renders a memory router at `/app/` inside `AppShell` and asserts `textContent === ''` and the matches are `['/', '*']` (i.e., the catch-all under the root).

### 3. No explicit assertion that every leaf route has no `element`
**Gap ·** The brief’s Constraints state “Route elements stay empty until their owning tasks.” Case 4 relies on the empty-element behavior to render no text, but it only exercises the catch-all. It does not verify that the 20 declared leaves also have no `element`.  
**Why it matters ·** A leaf with an accidental element would change the shell content when that route is visited, violating the T02 contract that routes are paths only.  
**Suggested test ·** Extend case 5 to walk every leaf in `appRoutes` and assert `leaf.element === undefined` and `leaf.Component === undefined`. (The `children` array is already being inspected, so this is a cheap addition.)

### 4. `createAppRouter` is not proven to use `createBrowserRouter`
**Gap ·** Case 6 asserts `.basename === '/app'` and compares the route tree by path. It does not assert that the returned object is a browser router created by `createBrowserRouter`. A future refactor could switch to `createMemoryRouter` or `createHashRouter` inside `createAppRouter` without failing case 6.  
**Why it matters ·** AC5 depends on the browser router interacting with `window.history` and the edge. A different router type could pass the unit tests but fail the live check.  
**Suggested test ·** Add one of the following assertions in case 6:
- spy on `createBrowserRouter` and assert it is called with `appRoutes` and `{ basename: APP_BASENAME }`; or
- assert that the returned router has a `navigate` method that mutates `window.location.pathname` when called with a relative path.

### 5. Backend-prefix coverage is not tied to the exact edge prefix list
**Gap ·** Case 3 tests 20 backend locations, which is thorough. However, the test manifest does not state that the locations are derived from the exact edge prefix list in `design.md §4c` / the frozen brief. A maintainer adding a new backend prefix might forget to add it to the test array.  
**Why it matters ·** L14 requires the SPA route set to be disjoint from backend prefixes. If the test list drifts from the real edge map, the guarantee weakens.  
**Suggested test ·** Define the backend prefixes as a single `readonly` array in `routes.tsx` (or import them from a shared test constant) and generate the 20 locations from that array. The array should match the edge prefixes verbatim: `/oauth2`, `/login`, `/logout`, `/connect`, `/.well-known`, `/userinfo`, `/error`, `/accounts`, `/admin`, `/api-keys`, `/api/v1`, `/notifications`.

### 6. No test that a path segment matching a backend name is not resolvable outside `/app`
**Gap ·** Mutation break d widens `APP_BASENAME` to `'/'` and catches the prefix issue at the basename level. It does not test a more subtle regression: adding a route leaf whose path is a backend prefix (e.g., `{ path: 'admin' }` under `/app` is fine, but `{ path: '/admin' }` at the root would be wrong). The absolute-path mutation break (b) covers `{ path: '/home' }`, but not a backend-named absolute path.  
**Why it matters ·** The absolute-path break proves the test rejects `/home`, but the security-relevant scenario is rejecting `/admin`, `/accounts`, etc., as SPA routes.  
**Suggested test ·** Add a focused mutation break (or a permanent negative case) that declares `{ path: '/admin' }` and asserts case 3 still rejects `/admin`.

### 7. Case 5 does not verify the public/authenticated split
**Gap ·** Case 5 checks that there are 20 unique paths, none starts with `/`, none contains `*`, and the tree order is correct. It does not verify that the 9 public paths and 11 authenticated paths are exactly the ones listed in the frozen brief.  
**Why it matters ·** A swap of a path between arrays (e.g., moving `home` to public) would not fail case 5, but would mislead later tasks (T40–T42) that rely on the split.  
**Suggested test ·** Extend case 5 to assert `PUBLIC_ROUTE_PATHS` deep-equals the frozen 9 paths and `AUTHENTICATED_ROUTE_PATHS` deep-equals the frozen 11 paths, in order.

---

## Non-gaps (acknowledged, no change needed)

- **`.routes` cannot deep-equal `appRoutes`.** Correctly handled by comparing paths; case 5 pins the exact paths.
- **Expected React Router warnings on stderr.** Accepted as harmless noise; the tests do not treat stderr warnings as failures.
- **`shellBuildsAndMounts` passes unchanged.** Verified by running the full suite; AC6 satisfied.
- **Case 4 uses `textContent === ''`.** Sufficient to detect the default 404 text if the catch-all is removed.

---

## Summary

The Phase 10 test manifest provides strong coverage of the `/app` prefix behavior, the route set shape, and the backend-prefix disjointness. The gaps above are mostly strengthenings rather than missing core behavior: query strings on email links, the bare `/app/` root, leaf `element` absence, proof of `createBrowserRouter`, exact prefix-list synchronization, and the public/authenticated split. Adding them would make the suite more robust against the mutation scenarios that matter for L14.
