# frontend · T02 · Phase 1 — Specification Extraction

## Business Rules
- T02 cites only L14. **R28**, which serves only public routes when unauthenticated and redirects the rest to sign-in,
  depends on the same route set but is proved by T40/T41, not here.
- Since the 2026-10-19 note in design.md §4c, `/app/verify` and `/app/reset` take `?token=`. The query string doesn't change
  the path, so it doesn't affect route matching.

## Locked Decisions
- **L14** `[ALL]`:
  - every SPA route lives under `/app`;
  - the paths are verbatim in §4c (Q6 resolved 2026-10-10: final);
  - `/app` is disjoint from every edge-mapped backend prefix;
  - a new public route needs an ADR.
- **L13:** React Router is the router.
- **L12:** don't rewrite the T01 shell; extend it.
- **L9:** single origin. The SPA never serves a backend path; the edge routes those to the services.
- **L11:** no hard-coded copy, including any default error or 404 UI that a library would render.

## Files involved
- Extend: `src/app/mountShell.tsx` (render the router), `src/app/AppShell.tsx` (host the router output), and
  `frontend/package.json` and `pnpm-lock.yaml` (`react-router`).
- New: a route module in `src/app/` (basename plus the §4c path set) and the `routesResolveUnderAppPrefixOnly` test.
- Unchanged: `vite.config.ts` (`base` already `/app/`; SPA fallback is the Vite default), `infra/local/**` (the edge
  already restricts the SPA to `/app*`), and every T01 test.

## Dependencies
- `react-router` (8.x; L13).
- The route set from design.md §4c:
  - **public:** `sign-in`, `callback`, `register`, `register/sent`, `verify`, `resend-verification`, `reset/request`,
    `reset`, `signed-out`;
  - **authenticated:** `home`, `account`, `account/sessions`, `account/mfa`, `api-keys`, `admin`, `invoices`,
    `invoices/:id`, `payments`, `notifications`, `receipts/:id` (the last two `:id` routes come from the note of
    2026-10-19).
- Deferred names (`evidence`, `disputes`, `reputation`, `fraud`) are **not** part of the route set.
- Edge prefixes that must never resolve in the SPA (§4c note 2026-10-21 (2)): `/oauth2`, `/login`, `/logout`, `/connect`,
  `/.well-known`, `/userinfo`, `/error`, `/accounts`, `/admin`, `/api-keys`, `/api/v1`, `/notifications`.
  `admin`, `api-keys` and `notifications` exist as SPA routes **only** under `/app`. That's exactly why the prefix matters:
  `/admin` is auth-service and `/app/admin` is the SPA.

## Acceptance Criteria
| # | Criterion | Source |
|---|---|---|
| AC1 | Every §4c route path resolves to its route when visited under `/app` (`/app/<path>`). | task, L14 |
| AC2 | The same path without `/app` (e.g. `/home`, `/admin`) resolves to nothing in the SPA. | task ("not served"), L14 |
| AC3 | No backend edge prefix resolves in the SPA. | L9, L14 |
| AC4 | An unmatched path never shows library-default copy. | L11 |
| AC5 | Live: through the edge, `/app/<route>` deep links return the SPA's `index.html`; `/home` or `/foo` do not reach the SPA (404 from the edge). | task, L9 |
| AC6 | The T01 test suite still passes unchanged. | L12 |

## Tests required
- **`routesResolveUnderAppPrefixOnly`** (named, package.md §8 → L14): AC1–AC4.
- Mutation breaks: basename removed; a route declared outside `/app`; a backend prefix becoming resolvable.
- Live check: AC5.

## Open Questions
None block the task. Choices for Phase 2:
1. Register the full §4c route set now (paths only, no screens) so AC1 can be tested against the real set. The default is
   yes, since the set is final (Q6).
2. Handle unmatched paths under `/app` with a silent catch-all that renders nothing, so React Router's default English 404
   never shows (AC4). T41 turns it into the sign-in redirect. The default is yes.
3. Version: 8.4.0 (5 weeks old) over 8.5.0 (3 days old).
