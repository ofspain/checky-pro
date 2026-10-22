# frontend · T02 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T02 — /app prefix |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/app/routes.tsx`, `AppShell.tsx`, `mountShell.tsx`, `frontend/package.json`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **/app prefix.** Every SPA route resolves under `/app`, and a path outside it is not served by the SPA. Verifies `routesResolveUnderAppPrefixOnly`. Cites L14.

---

## Review scope

Reviewed the Phase 6 T02 implementation against the frozen brief and `spec/frontend/agents.md`. No code was rewritten.

---

## Findings

### 1. `appRoutes` children are typed as `{ path: string }[]`, losing literal path types
**Issue ·** `routes.tsx` builds `appRoutes` by mapping the `as const` path arrays, but the mapped objects are inferred as `{ path: string }[]`. The `appRoutes` constant is typed `RouteObject[]`, so downstream consumers cannot derive the exact route paths from it.  
**Evidence ·** `frontend/src/app/routes.tsx:40-42` (`...PUBLIC_ROUTE_PATHS.map((path) => ({ path }))`).  
**Recommendation ·** Add an explicit type annotation or use `satisfies` so the children retain the literal path strings, e.g.:
```ts
const children: RouteObject[] = [
  ...PUBLIC_ROUTE_PATHS.map((path) => ({ path } satisfies RouteObject)),
  ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path } satisfies RouteObject)),
  { path: '*' },
];
```
This preserves the `as const` literals for typed navigation helpers in later tasks without changing runtime behavior.  
**Confidence ·** Low — runtime behavior is correct; this is a type-quality observation.

### 2. `appRoutes` is exported as a mutable array
**Issue ·** The route module is intended to be the single source of truth for the route set. While the path arrays are `as const`, `appRoutes` itself is `export const appRoutes: RouteObject[]`, which does not prevent mutation at runtime.  
**Evidence ·** `frontend/src/app/routes.tsx:36` (`export const appRoutes: RouteObject[] = [...]`).  
**Recommendation ·** Either freeze it (`Object.freeze(appRoutes)` and recursively freeze children) or export a getter/`createAppRoutes()` function. Given the brief’s emphasis on single-source-of-truth, shallow-freezing the top-level array and its children is a cheap defense.  
**Confidence ·** Low — no current mutation risk, but a future task could accidentally push into it.

### 3. `createAppRouter()` has no disposal hook in production
**Issue ·** Every call to `createAppRouter()` creates a browser router with internal subscriptions. It is called once in `mountShell` during page load and the router is never disposed. For a full-page SPA reload this is fine, but if the shell is ever unmounted and remounted (tests, future embed scenarios), routers leak.  
**Evidence ·** `frontend/src/app/mountShell.tsx:17` (`<RouterProvider router={createAppRouter()} />`).  
**Recommendation ·** No change required for T02 — the self-review correctly notes this is acceptable because `main.tsx` mounts once. Document the assumption in a code comment so a later refactor does not introduce a disposal bug.  
**Confidence ·** Low — accepted design choice, but worth making explicit.

### 4. The catch-all renders nothing, but there is no assertion that it is the *only* wildcard
**Issue ·** Frozen AC4 and mutation break #3 rely on the catch-all suppressing React Router’s default 404. The implementation has exactly one `*`, but nothing in the production code enforces that future edits cannot add another wildcard or a parameterised catch-all.  
**Evidence ·** `frontend/src/app/routes.tsx:42` (`{ path: '*' }`).  
**Recommendation ·** This is covered by the planned test case 5 (“none contains `*`). No production-code change needed.  
**Confidence ·** Low — test will catch regressions.

### 5. `AppShell` accepts `children?: ReactNode` but the type is inline
**Issue ·** The prop type is declared inline: `({ children }: { children?: ReactNode })`. This is correct but repeats the type at every call site if `AppShell` is later wrapped. A named `AppShellProps` type would be cleaner.  
**Evidence ·** `frontend/src/app/AppShell.tsx:5`.  
**Recommendation ·** Optional: extract `export interface AppShellProps { children?: ReactNode }`. Not required for T02.  
**Confidence ·** Low — cosmetic.

### 6. No inline comment explaining why route elements are empty
**Issue ·** The route leaves have no `element`, which is intentional per the brief (screens are added later). A reader might mistake this for unfinished code.  
**Evidence ·** `frontend/src/app/routes.tsx:40-42`.  
**Recommendation ·** Add a brief comment on the mapped leaf objects, e.g. `// no element yet; screens attach in their owning tasks`. The existing JSDoc on `appRoutes` already explains this, so this is optional.  
**Confidence ·** Low — documentation.

### 7. The T02 named test file does not exist yet
**Issue ·** `routesResolveUnderAppPrefixOnly.test.tsx` is part of Phase 10 and has not been written. The production code is complete, but the named test that verifies `routesResolveUnderAppPrefixOnly` is missing.  
**Evidence ·** `glob frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` returns nothing.  
**Recommendation ·** Complete Phase 10 and write the six test cases listed in the frozen brief, including the mutation breaks. This is expected workflow, not a production-code defect.  
**Confidence ·** High — it is a missing deliverable, but scheduled for a later phase.

### 8. `mountShell.tsx` imports `RouterProvider` from `'react-router'`, matching the frozen disposition
**Issue ·** None. This is correct per the probe that showed `react-router-dom@8.4.0` is not published.  
**Evidence ·** `frontend/src/app/mountShell.tsx:3`.  
**Recommendation ·** No change.  
**Confidence ·** N/A — confirmation.

### 9. The route set correctly excludes deferred Phase 2–5 names
**Issue ·** None. `evidence`, `disputes`, `reputation`, and `fraud` are not present.  
**Evidence ·** `frontend/src/app/routes.tsx:7-32`.  
**Recommendation ·** No change.  
**Confidence ·** N/A — confirmation.

### 10. Backend-prefix disjointness is correct in the route declaration
**Issue ·** None. The same name (`admin`) appears as an authenticated SPA route under `/app` while `/admin` is a backend prefix handled by the edge.  
**Evidence ·** `frontend/src/app/routes.tsx:26`; frozen brief §AC3 backend prefixes include `/admin`.  
**Recommendation ·** No change. The planned test case 3 will verify `matchRoutes(...) === null` for `/admin`.  
**Confidence ·** N/A — confirmation.

---

## Open Questions

- None blocking. The Phase 10 test file is the remaining T02 deliverable.

---

## Summary

The T02 production implementation closely matches the frozen brief. The only material gap is the missing Phase 10 test file (`routesResolveUnderAppPrefixOnly.test.tsx`). The remaining findings are low-severity type-quality, documentation, and defensive-programming observations that do not affect T02 acceptance criteria.
