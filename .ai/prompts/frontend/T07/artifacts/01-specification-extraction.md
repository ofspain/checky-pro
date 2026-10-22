# frontend · T07 · Phase 1 — Specification Extraction

Consumes `00-repository-understanding.md`. Task 7, **429 generic copy**. Verifies
`rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23. Date: 2026-10-22.

## Business Rules
- **R23, the first part (this task):** when an SPA-originated call returns HTTP 429, the SPA shows fixed, generic
  waiting copy with no countdown or interval, and resumes only on user action. Rate-limit responses from the SAS-hosted
  pages are out of SPA scope.
- **R23, the second part (task 8, not here):** the SPA does not replay non-idempotent POSTs automatically. T07 must not
  add any replay, so it doesn't weaken task 8, but the named guarantee and its test cases belong to task 8.
- **Backend R41 (context):** the auth service rejects over-threshold login, `/oauth2/token`, password-reset
  confirmation and MFA verification with 429.
- **Backend R42 (context):** an ingress IP-level limiter may also return 429, on any route, and not necessarily with a
  problem body.

## Locked Decisions
- **`agents.md`, API access:**
  - "The client boundary owns … fixed generic 429 handling … Screens do not reimplement them.";
  - "429 handling shows fixed copy with no countdown and no automatic replay of non-idempotent POST requests.";
  - errors are RFC 9457, mapped to copy, and server detail is never shown.
- **L6:** generated client only. 429 handling lives in the client boundary, with no hand-written `fetch`.
- **L11 / `agents.md`, Accessibility and i18n:**
  - catalogue-only strings;
  - async results announced through a live region;
  - keyboard-operable;
  - WCAG AA.
- **O3 (resolved):** `react-i18next`, English-only at launch. A missing key falls back to English, never a raw key.
- **L4:** no lockout timers, with identical copy and timing on public surfaces. It reinforces "no countdown". The one
  live 429 today is on a public, enumeration-sensitive surface (password reset).
- **L12:** additive. The T05 and T06 behaviour and suites stay as they are.
- **§5:** no persisted 429 state (sessionStorage holds only the L16 items, and localStorage only public preferences).
- **Q12(b) (closed):** a 429 and `Retry-After` are deliberately undocumented in `auth.yaml`. The SPA must not depend on
  the header or the body. With "no countdown or interval", that means `Retry-After` is **ignored**.

## Files involved
- **Read / extend:**
  - `frontend/src/api/client.ts`: `createAuthApi` installs the 429 handling next to T05's middleware;
  - `frontend/src/api/sessionBoundary.ts`: read only, for the `Middleware` type and the hook pattern.
- **New, as the spec places them:**
  - `frontend/src/api/`: the 429 recogniser, part of the boundary (design §6: "api/ … 401/429");
  - `frontend/src/i18n/`: the catalogue (design §6, L11, O3);
  - the notice that renders the copy: in the app shell area (`src/app/`), because it is boundary-wide, not
    feature-owned.
- **Test:** `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.ts(x)`. Task 8 adds its cases to the
  same named test later.

## Dependencies
- The openapi-fetch middleware API (`onResponse`; reverse-order execution).
- `createAuthClient` from `@themistra/api-client`. Today it is the only client, and the handling must be reusable for
  later clients.
- **New packages (O3):** `i18next` and `react-i18next`, pinned exactly and at least about 2 weeks old.
- **Contracts:** `auth.yaml` declares no 429. The live shape is `application/problem+json`, type
  `…/problems/rate-limit-exceeded`, with `Retry-After`. The SPA keys on the **status only**.
- **No config keys.**

## Acceptance Criteria

| # | Criterion | Source |
|---|---|---|
| AC1 | When any SPA call made through the generated client returns 429, the generic rate-limit copy is shown. This applies to protected and public operations, and to a 429 on T05's re-send. | R23; `agents.md` |
| AC2 | The copy is fixed: the same text whatever the endpoint, the `Retry-After` value or its absence, the problem `title`/`detail`, or a body that isn't problem JSON. No server text is shown. | R23 ("fixed, generic"); `agents.md` errors; R42 |
| AC3 | There is no countdown and no interval: no number of seconds or time is shown, and no timer or interval is started by the handling. | R23; L4 |
| AC4 | It resumes only on user action. The notice doesn't disappear or retry on its own, and the user clears it or acts again. | R23 |
| AC5 | The copy comes from the catalogue and is announced through a live region. | L11; O3 |
| AC6 | The 429 handling adds no replay, so the 429 reaches the caller unchanged and is sent once. | R23 / task 8 boundary |
| AC7 | Non-429 responses, including 401 renewal, 503 and 200, don't show the notice. The T05/T06 suites are unchanged and green. | L12 |
| AC8 | Nothing about the 429 is persisted or logged. | §5 |

## Tests required
- **`rateLimitedResponseShowsGenericCopyWithoutReplay`** (`package.md` §8 → R23), the T07 cases:
  - a 429 on a protected operation and on a public operation shows the copy;
  - the same copy for different `Retry-After` values, none, different problem titles, and a non-JSON body;
  - no digits or time in the notice, and no timer started;
  - the notice persists until a user action;
  - a live region;
  - the catalogue key resolves (no raw key);
  - a 429 on T05's re-send;
  - one notice for a burst of 429s;
  - non-429 responses show nothing;
  - the 429 reaches the caller once.
- **Boundary:** the T05 and T06 suites unchanged.
- **Task 8** extends this test with the POST no-replay cases.

## Open Questions
None blocking. These are interpretations for Phase 2 to state, not blockers:
- **Catalogue setup:** no task owns it, and T07 is the first user-facing string.
- **What "resume on user action" means** for the notice.
- **Where the notice mounts** before any screen exists.

`package.md` §11 has no open item on R23. Q12(b) is closed.
