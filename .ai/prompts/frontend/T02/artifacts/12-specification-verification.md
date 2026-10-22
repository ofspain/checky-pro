# frontend · T02 · Phase 12 — Specification Verification

Date: 2026-10-22. Consumes Phases 0–11. Verified against `tasks.md` task 2, `design.md` §4a (L14) and §4c (the route set
and the edge-prefix note of 2026-10-21 (2)), `package.md` §8, the FROZEN brief and `agents.md`.

## Phase 11 gaps (Kimi), resolved before verification

Kimi raised 7 gaps:
- 6 were accepted, one of them with a corrected expectation;
- 1 needed no test change, only a recorded mutation break.

Before anything was changed, the two behaviours the gaps relied on were checked with a throwaway Vitest probe (since
deleted): bare `/app/`, and a browser router versus a memory router. Only the test file changed; there was no production
change.

| Gap | Decision | Change | Break caught |
|---|---|---|---|
| 1 Query string on emailed links | ACCEPTED | New case: `/app/verify?token=abc` and `/app/reset?token=abc` resolve to their leaves. A memory router at `/app/verify?token=abc` keeps `pathname /app/verify` and `search ?token=abc`. | d (prefix widened) |
| 2 Bare `/app/` | ACCEPTED, **expectation corrected** | Kimi expected the matches to be `['/', '*']`. The probe showed bare `/app/` matches **only `['/']`**: the catch-all needs a non-empty segment. The new case asserts `['/']`, no errors, and no shell text. | j (an `index` route added) |
| 3 Leaves carry no screen | ACCEPTED | New case: every route in `appRoutes` has no `element`, `Component` or `errorElement`. | g (`Component` attached) |
| 4 `createAppRouter` must be a browser router | ACCEPTED, second option | Spying on an ESM import would need `vi.mock`. The probe confirmed that only a browser router moves `window.location`. Case 6 now calls `navigate('/home')` and asserts `window.location.pathname === '/app/home'` with an empty hash. `afterEach` resets the jsdom URL to `/`. | f (`createMemoryRouter` swapped in) |
| 5 Backend list tied to the edge prefixes | ACCEPTED, test-side | `BACKEND_PREFIXES` is the 12 prefixes from §4c, verbatim and `as const`. `BACKEND_PATHS` derives each one bare, with `/` and with `/x` (36 locations, length asserted). The list stays in the test, not in `routes.tsx`: it would be a production export with no production use, outside the frozen Files sections. | d |
| 6 Backend-named absolute route | NO TEST CHANGE; break recorded | With break i (`'admin'` → `'/admin'`), case 3 still passes, correctly: route paths sit under the basename, so `/admin` can't resolve outside `/app` whatever is declared. Cases 1 and 5 catch it. | i |
| 7 Public/authenticated split pinned | ACCEPTED | Case 5 now asserts both arrays equal the frozen lists, in order (this replaces the length checks). | h (`home` also listed as public) |

## Final suite
- `routesResolveUnderAppPrefixOnly`: 9 cases.
- `shellBuildsAndMounts`: 7 cases, unchanged.
- **16/16 passed**. `typecheck` clean, `build` clean, `pnpm install --frozen-lockfile` OK.

All 10 mutation breaks fail at least one case. Breaks a–e are from Phase 10 and were re-run on the final suite; f–j are new.

| Break | Failing cases |
|---|---|
| a) basename removed | 6 |
| b) `'/home'` declared | 1, 5 |
| c) catch-all removed | 4, 5 |
| d) `APP_BASENAME = '/'` | 1, query, 2, 3, 6 |
| e) `'disputes'` added | 5 |
| f) memory router | 6 |
| g) `Component` on leaves | no-screen |
| h) `home` also public | 5 |
| i) `'/admin'` declared | 1, 5 |
| j) `index` route under root | bare-`/app`, 5 |

## Traceability matrix
| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| Task 2 / L14: routes under `/app` | Yes | `routes.tsx:4` (`APP_BASENAME`), `:48` (`createBrowserRouter(appRoutes, { basename })`) | `routesResolveUnderAppPrefixOnly.test.tsx:83`, `:89`, `:154` | No | No |
| L14: §4c set verbatim (9 + 11) | Yes | `routes.tsx:7`, `:20` | `:127` (exact lists, tree shape) | No | No |
| Task 2: outside `/app` not served | Yes | basename at `routes.tsx:48`; the edge serves only `/app*` to the SPA | `:98`, `:104`; live `/home`, `/foo` → 404 | No | No |
| L9 / L14: disjoint from backend prefixes | Yes | relative paths under the basename | `:104` (36 locations from the 12 prefixes); live `/admin` → auth-service 401 | No | No |
| L11: no default 404 copy, no screens | Yes | `routes.tsx:42` (`*`), no elements (`:36`) | `:111`, `:119`, `:144` | No | No |
| L13: React Router | Yes | `frontend/package.json` `react-router` `8.4.0`; imports from `'react-router'` | every case | No | No |
| L12: extend T01 | Yes | `AppShell.tsx:5-6` (optional `children`), `mountShell.tsx:18` | AC6: `shellBuildsAndMounts` 7/7 unchanged | No | No |
| AC5 (live) | Yes | `infra/local` edge plus Vite SPA fallback | Recorded: `/app/home`, `/app/invoices/<uuid>`, `/app/verify?token=x` → 200; `/home`, `/foo` → 404 (re-run after Phase 11) | No | Live part recorded, not automated (frozen) |

Recorded deviations, all justified and none against a lock:
- Case 6 compares route trees by path, because a router's processed `.routes` can't deep-equal its input (Phase 10).
- The bare-`/app/` expectation is `['/']`, not Kimi's `['/', '*']` (probe, above).

## Principal-engineer answers
1. **Fully complete?** Yes.
   - The 20 §4c paths resolve only under `/app`.
   - Nothing outside `/app` resolves, the 12 backend prefixes included.
   - Unmatched `/app` paths render no text.
   - The live edge serves deep links under `/app` and returns 404 for paths outside it.
2. **Every acceptance criterion?** Yes.
   - AC1–AC4 and AC6 are automated.
   - AC5 was checked live twice: after Phase 6, and after the Phase 11 changes.
3. **Any lock violated?** No. L9, L11, L12, L13 and L14 are each upheld and tested. Only `react-router` was added.
4. **Remaining risks:**
   - AC5's live half isn't automated yet. A Playwright smoke through the edge comes with the first task that names an E2E
     test.
   - Expected router warnings appear on test stderr:
     - the basename warning from `shellBuildsAndMounts`;
     - the "leaf route has no element" warning, until screens attach.
   - `BACKEND_PREFIXES` in the test must be kept in step with the edge whenever a backend prefix is added. Its comment says
     so.
   - Bare `/app/` renders nothing until T41 adds its redirect. A user who types `/app/` today sees a blank page, which is
     consistent with the app having no screens yet.

**PASS:** every SPA route resolves under `/app` and nothing outside it is served by the SPA. The 9 cases of
`routesResolveUnderAppPrefixOnly` pass, all 10 mutation breaks are caught, and no lock is violated.
