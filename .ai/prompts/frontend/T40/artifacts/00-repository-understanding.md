<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T40 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T40 — Public route set is exact |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement** (`spec/frontend/tasks.md:71`): *"**Public route set is exact.** Exactly the public routes
in `design.md` §4c are reachable without a session. Verifies `publicRouteSetIsExactlyTheDeclaredSet`.
Cites R28, L14."*

No design and no requirement extraction here — that is phase 1.

---

## 1. Architecture summary

A React 19 + TypeScript SPA under Vite, served at the `/app` prefix, which the edge maps to the SPA and
which is disjoint from every backend prefix (L9, L14). No persistence of its own and no events: the
browser is the runtime, the backend is Spring Authorization Server plus four services behind an edge.

| Module | What lives there |
|---|---|
| `app/` | the shell, the router and the route table — `routes.tsx`, `mountShell.tsx`, `AppShell.tsx` |
| `auth/` | the OIDC client, the session owner, the screens that exist (`SignInScreen`, `CallbackScreen`, `SignedOutScreen`) |
| `api/` | the generated client wrapper, the 401/429 boundary, cross-tab renewal and sign-out |
| `i18n/` | the English catalogue; every user-facing string comes from it (L11) |
| `pwa/`, `profile/` | the service worker's boundaries; account and profile reads |

**Security posture that bears on this task:** tokens live in memory only (L16 names the entire
`sessionStorage` carve-out and tokens are not in it), and there is **no route guard anywhere today** —
which is the whole of §5 below.

## 2. Existing code this task touches

| | Status | |
|---|---|---|
| `app/routes.tsx` | **exists**, 75 lines | publishes `PUBLIC_ROUTE_PATHS` (9), `AUTHENTICATED_ROUTE_PATHS` (11), `appRoutes`, `createAppRouter()`. Its own comment at `:26` already says *"T40 enforces the split"*. |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **exists**, task 37's | **T40 extends this file; it does not create a second one** — task 37's header says so explicitly, and names what each task owns. |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | **exists** | already asserts *"declares exactly the 20 §4c paths, all relative, with the catch-all as the only wildcard"* and *"attaches a screen only to the routes whose task has run"*. |
| `app/mountShell.tsx` | **exists**, 30 lines | mounts `RouterProvider` with `createAppRouter()`. **Unowned by any task**, which is the same fact nine previous tasks have recorded from the other side. |
| `auth/cleanReauth.ts` | **exists** | the one module that sends a visitor to sign-in. Unwired — `createSessionBoundary` has no production constructor (OQ-T33-2). |
| a route guard / loader | **does not exist** | nothing in `src/` guards a route. |
| screens for 6 of the 9 public routes | **do not exist** | `register`, `register/sent`, `verify`, `resend-verification`, `reset/request`, `reset` are declared with no element. |

## 3. Established patterns to follow

The generated prompt's phase-0 body asks about JPA/Flyway, outbox/idempotency and resource-server
security. **None of that applies to this service** — it is backend boilerplate in a phase-generic
template. The frontend's actual patterns:

- **One source for the route set.** `routes.tsx` is the only place a path string is spelled; `endSession.ts`
  and `cleanReauth.ts` import `SIGN_IN_PATH` / `SIGNED_OUT_PATH` rather than re-spelling them. Task 37
  retired the last private copy.
- **Paths are relative and the basename is applied once**, by `createBrowserRouter(..., { basename })`.
- **Every user-facing string comes from the i18n catalogue** (L11), and tests assert the literal English
  *and* the catalogue entry, because `t()` returns the key when an entry is missing.
- **Source scans alongside behaviour.** Where a criterion is about what code may not do — reach a token,
  store something, navigate — the suites scan production source with comments stripped, and each scan
  carries a control proving it can fire.
- **Instruments are shared and grown, not duplicated.** Four closed tasks' suites hold allowlists and
  derived counts that later tasks extend; a count is derived from source rather than written down wherever
  that is possible, so it fails before it goes stale.

## 4. Testing conventions

Again the template is backend-shaped ("plain JUnit with a fixed `Clock`", "Testcontainers Postgres +
Kafka"). This service uses **Vitest + jsdom**, with Playwright named in `agents.md:11` for end-to-end and
not used by any task so far.

- Run everything through `npx -y pnpm@12.14.0 exec …`; bare `pnpm` is not on this host.
- **The exit code is the verdict**, not a pass count.
- `src/api/lintRejectsHandWrittenBackendFetch.test.ts` fails on this host — `oxlint@1.87.0` prints no
  summary line for the assertion that parses it. Pre-existing, unrelated, known.
- Gates per task: the named suite, the whole suite, `tsc --noEmit`, `oxlint src`, and a mutation harness
  under `artifacts/07-mutation-harness.py` whose tests must be **committed before it runs** — it reverts
  with `git checkout -- frontend/src/`.
- `agents.md:107` adds that every frontend task is checked against the whole platform running locally in
  Docker. Nine consecutive tasks have been verified in jsdom only, because what they built has no caller.

## 5. Known gaps and unknowns

### The one that decides this task: "reachable without a session" has no mechanism yet

Task 37's header states it plainly and it is still true:

> *"**Task 40 (to be added here):** that these nine paths **and only** these nine are reachable without a
> session. That claim needs the auth gate, and no route requires a session yet, so it is not merely
> unwritten — it is currently untestable."*

Today **every** declared route is reachable without a session, because nothing guards any of them. So the
public set is not "exactly the declared nine" — it is all twenty. Whether T40 *builds* that gate or only
*measures* its public half is the central question, and it interacts with **task 41** ("an unauthenticated
request for any other route redirects to sign-in", also R28). R28 has two clauses — *serve only the public
routes* and *redirect any other route* — and tasks 40 and 41 look like one clause each of the same
mechanism. **I do not know** which task is meant to introduce the guard; phase 1 must settle it from the
spec rather than from preference.

### Overlap with an existing suite that must be resolved, not duplicated

`routesResolveUnderAppPrefixOnly.test.tsx` already asserts the route **declaration** is exactly §4c's 20
paths. T40's claim is about **reachability under a session's absence**, which is a different claim about
the same list. Phase 2 has to say what T40 adds rather than restating, or the two files will drift into
disagreeing about the same number.

### Spec drift, recorded and not touched

`design.md:137` still reads *"**SPA route set** — PROPOSED paths, pending author confirmation (Q6)"*, while
`package.md:368` records **"Q6 … Resolved (2026-10-10): confirmed as proposed."** The list is
authoritative; the "pending" label is stale. T40's whole criterion points at this paragraph, so the drift
is worth naming before anything is built on it. `spec/` is never modified by this pipeline.

Also: the §4c authenticated list names nine paths in its bullet, and three more were added by the
2026-10-19 note (`invoices/:id`, `receipts/:id`, and the `?token=` query form of `verify` and `reset`).
`AUTHENTICATED_ROUTE_PATHS` holds eleven, which is the nine plus two detail routes — consistent, but the
reader has to assemble §4c from a bullet plus a later note, and `evidence`/`disputes`/`reputation`/`fraud`
are **reserved and explicitly out of this revision's route set**.

### Still open from earlier tasks, unchanged

**OQ-T36-5** nothing calls `endSession()` · **OQ-T33-2** the whole authenticated data path is dormant ·
**OQ-T39-6**, **OQ-T39-7** · **FU-T39-1** the shared module-scope-mutable scan's two holes ·
**FU-T37-2** `routes.tsx:5`'s false consequence · **OQ-T32-4** the harness is copied per task ·
**task 38** "Blocked on Q15", which looks mis-assigned and is the only end-to-end proof of R19.

## Phase 1

Specification extraction.
