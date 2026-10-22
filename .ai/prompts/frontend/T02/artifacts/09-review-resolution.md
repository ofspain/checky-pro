# frontend · T02 · Phase 9 — Review Resolution

Date: 2026-10-22. Consumes `07-self-review.md` and `08-independent-review.md` (Kimi 2.7: 10 items, of which 3 are
confirmations and none is a defect). Each claim was checked against the code. The type claims were checked with `tsc` in
the `frontend` container, using a throwaway probe file that was deleted afterwards.

| Source | Comment | Decision | Change |
|---|---|---|---|
| Kimi 1 | Leaves lose their literal path types | **REJECTED.** The probe compiled `const children: RouteObject[] = [...map((path) => ({ path } satisfies RouteObject))]` and assigned `'anything-at-all'` to its `path` type without error. The suggested form still widens to `string`, because the `RouteObject[]` annotation wins. The literals that typed navigation needs are already exported as `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` (`as const`). | None |
| Kimi 2 | `appRoutes` is mutable | **REJECTED.** `createBrowserRouter` rejects `readonly RouteObject[]` (TS4104 in the probe), so a readonly type is impossible. At runtime React Router copies the input: `convertRoutesToDataRoutes` is called with `allowInPlaceMutations = false`. The router never mutates `appRoutes`, and Phase 10 case 6 deep-equals it with `router.routes`. A recursive freeze would guard against a hypothetical edit that code review and that case already catch. | None |
| Kimi 3 | The router is never disposed | **ACCEPTED (comment only)**, as Kimi recommended. | `mountShell.tsx` doc comment: called once per page load from `main.tsx`, so the router lives as long as the page and is never disposed |
| Kimi 4 | Nothing enforces a single wildcard | **ACCEPTED, already planned.** Phase 10 case 5 asserts that no declared path contains `*`. | Phase 10 |
| Kimi 5 | Inline `AppShell` prop type | **REJECTED.** The prop type is cosmetic, and nothing else uses it yet. The frozen brief fixed this exact signature (Phase 4, finding 9). | None |
| Kimi 6 | No comment explaining the empty elements | **REJECTED.** As Kimi notes, the `appRoutes` JSDoc already says "Screens are attached by their own tasks." | None |
| Kimi 7 | Named test not written | **ACCEPTED, scheduled.** It is written in Phase 10, as in T01. | Phase 10 |
| Kimi 8–10 | Confirmations (`react-router` import, deferred names absent, `admin` disjointness) | No action | None |
| Self 1 | Basename warning in `shellBuildsAndMounts` | Accepted as noise. That test is frozen. | None |
| Self 2 | Bundle size | Accepted. L13 mandates the router. | None |
| Self 3–6 | Info | No change. Self 3 is the subject of Kimi 3, handled above. | None |

After resolution:
- `typecheck`: clean.
- `shellBuildsAndMounts`: 7/7 passed.
- `build`: clean.

Production code changed only by the one comment.
