<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T40 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T40 — Public route set is exact |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

No design and no implementation. Phase 0 left two questions open; both are **answered from the spec and
the repository** below, with the evidence, because guessing either would misshape the task.

---

## Business rules

| ID | |
|---|---|
| **R28** *(requirements.md:99-100)* | **"WHILE a visitor is unauthenticated, THE SPA SHALL serve only the public routes listed verbatim in `design.md` §4c and SHALL redirect any other route to the sign-in start."** Two clauses, and tasks 40 and 41 take one each — see **Q1** below. |
| R19 *(touched)* | The signed-out landing is one of the nine public routes; task 37 attached its screen. |
| R29 *(touched, not scoped)* | Session expiry mid-task. Named here only because a guard that stored anything would violate L16; it stores nothing. |

## Locked decisions

| ID | What it constrains here |
|---|---|
| **L14** *(design.md:53-56)* | The route set under `/app`, **listed verbatim in §4c**. Public: sign-in start, callback, registration, its acknowledgement, verify-from-link, resend verification, password-reset request and its acknowledgement, password reset from token, and the signed-out landing — **nine**. *"Everything else requires a session."* A new public route requires an ADR. |
| **L1–L3** | The session owner and its surfaces; the guard must ask the session owner, not a store. |
| **L11** | Every user-facing string comes from the i18n catalogue. A guard that renders nothing renders no copy — which is also the codebase's established behaviour for a route that must not render. |
| **L12 / L13** | The shell is the root everything mounts inside; `app/` owns the router. |
| **L16** | The entire `sessionStorage` carve-out. A route guard adds nothing to it. |
| **L15** | *"Auth gate first.* Payment surfaces are reachable only after the auth gate is complete." This task is part of that gate, which is why it precedes the payment surfaces rather than being bundled with them. |

## Files involved

### Existing, to read and extend

| | |
|---|---|
| `app/routes.tsx` | publishes `PUBLIC_ROUTE_PATHS` (9) and `AUTHENTICATED_ROUTE_PATHS` (11). Its comment at `:26` already reads *"T40 enforces the split."* |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **the named test. T40 extends this file**; task 37's header says so and says what each task owns. |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | holds *"declares exactly the 20 §4c paths"* and *"attaches a screen only to the routes whose task has run"*. Both will have an opinion about anything T40 adds. |
| `auth/accessToken.ts` | the session owner — `accessToken.current()` is the only question a guard can ask. |

### New, where the spec expects it

`design.md:203` places **guards in `auth/`**:

```
│   ├── auth/                 OIDC client, session owner, guards    (L1–L3, L16, L18)
```

So the new module belongs under `auth/`, not `app/`, and `app/routes.tsx` imports it — the direction
`api/` → `auth/` is forbidden (task 39's AC13) but `app/` → `auth/` is the normal direction and is already
how `routes.tsx` reaches `SignInScreen`.

## Dependencies

| | |
|---|---|
| `accessToken.current()` | returns `string \| null`, memory only |
| `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `APP_BASENAME`, `SIGN_IN_PATH` | `app/routes.tsx` |
| `react-router` | `createBrowserRouter` / `createMemoryRouter`, route objects, `Outlet` |
| config keys · contracts | **none.** This task calls no endpoint and reads no contract. |

## Acceptance criteria

| | | Requirement |
|---|---|---|
| **AC1** | Each of the **nine** public paths resolves and renders **without a session**. | R28, L14 |
| **AC2** | **None** of the eleven authenticated paths serves its content without a session. | R28, L14 |
| **AC3** | **AC2 fails when the guard is removed.** See **Q2** — without this, AC2 passes for a reason that has nothing to do with this task. | R28 |
| **AC4** | **With** a session, an authenticated path is served — so the guard is a split and not a blanket deny. | R28 |
| **AC5** | The two sets are disjoint, and together are exactly §4c's declared set; the counts are **derived from `routes.tsx`**, not re-spelled. | L14 |
| **AC6** | The guard lives in `auth/` and `app/` imports it, never the reverse. | design.md:203 |
| **AC7** | The guard asks the session owner and **stores nothing**, writes nothing, and sends no request. | L16, L3 |
| **AC8** | A blocked route renders **no copy at all** — no text, no fallback sentence. | L11 |
| **AC9** | Adding a path to either list without updating the other **fails a test** rather than silently widening the public set. | L14 |
| **AC10** | A sessionless visit to a public route performs **no navigation** — the claim is "served", not "redirected elsewhere and back". | R28 |
| **AC11** | Where T40 touches the sibling suite's pinned counts (`routesResolveUnderAppPrefixOnly`), those instruments are **grown, not bypassed**. | the handoff pattern |

## Tests required

**Named test (package.md §8:152):** `publicRouteSetIsExactlyTheDeclaredSet` → R28. It already exists as
task 37's file and T40 extends it.

**Also mapped to R28 (§8:151):** `unauthenticatedRoutesRedirectToSignIn` → **task 41's**, not this task's.
The header lists four named tests for T40 because the generator injects every test in the task's
requirement neighbourhood; only `publicRouteSetIsExactlyTheDeclaredSet` is this task's deliverable.
`homeRouteRendersLandingScreenAndManagesFocus` is task 174's and `routesResolveUnderAppPrefixOnly` is an
earlier task's — both are referenced here only because T40 may have to grow them.

**Boundary tests implied:**

- a public path **with a query string** (`/app/verify?token=…`) is still served without a session — the
  2026-10-19 note makes the token a query parameter, so the guard must not key on the full URL;
- a public path with a **trailing slash**;
- an authenticated path visited with a session **and** without one, in the same suite, so AC2 and AC4 are
  read against each other;
- the **catch-all** is neither public nor authenticated and stays silent (an earlier task owns it; T40 must
  not quietly change it);
- the guard under **`StrictMode`** double-render, which is how every other suite here mounts.

## Open questions

### Q1 — Which task introduces the guard: 40 or 41? **Answered; not a blocker.**

R28 has two clauses and the two tasks read as one each:

| | |
|---|---|
| **T40** | *"serve only the public routes listed verbatim in §4c"* — which routes a sessionless visitor is served. |
| **T41** | *"and SHALL redirect any other route to the sign-in start"* — where the others send them. |

The decisive evidence is in the repository, written by an earlier task rather than inferred now.
`routesResolveUnderAppPrefixOnly.test.tsx:132` asserts a route that must not render renders nothing, and
names the owner of the other half in its own title:

> `it('treats the bare /app/ as no route: only the root matches and nothing renders (T41 owns its redirect)')`

So the established behaviour for "must not render" in this codebase is **render nothing, silently** (L11),
and **T41 owns turning that into a redirect**. T40 therefore introduces the split and leaves the
destination to T41. `routes.tsx:26`'s *"T40 enforces the split"* says the same thing from the other side.

Phase 3 should still challenge this, because the alternative reading — T40 is test-only and T41 builds
everything — would make T40's own criterion untestable, which is what task 37 already observed.

### Q2 — T40's criterion is currently **vacuously true**, and that is the task's real problem. **Not a blocker; it is the design content.**

Measured, not assumed: **all eleven authenticated routes are declared as `{ path }` with no element.** Only
three routes in the whole table have an element — `sign-in`, `callback`, `signed-out` — and all three are
public.

So *"exactly the public routes are reachable without a session"* is **already satisfied**, and for a reason
that has nothing to do with a guard: there is nothing authenticated to reach. A suite written against
today's table would pass with no production code at all, and would keep passing after task 42 attaches the
authenticated shell **to a route nothing guards**.

This is why **AC3** exists as a first-class criterion rather than a footnote. Phase 2 must say how AC2 is
measured such that removing the guard turns it red — the candidates are a test-injected element on an
authenticated path, or asserting the guard's decision directly, or both. Phase 3 should pick.

### Q3 — Spec drift at the exact paragraph this task cites. **Recorded, not fixed.**

`design.md:137` reads *"**SPA route set** — PROPOSED paths, pending author confirmation (Q6)"*, while
`package.md:368` records *"Q6. … **Resolved (2026-10-10): confirmed as proposed.**"* and `package.md:28`
lists Q6 among the resolved blockers. The list is authoritative and the label is stale. `spec/` is never
modified by this pipeline.

Related, and a reading hazard rather than a contradiction: §4c's route set must be assembled from the
bullet at `:138-140` **plus** the 2026-10-19 note at `:144-150`, which adds `invoices/:id`, `receipts/:id`
and the `?token=` form of `verify` and `reset`. `evidence`, `disputes`, `reputation` and `fraud` are
**reserved names, explicitly out of this revision's route set** — so "everything in §4c" is nine public and
eleven authenticated, not nine and fifteen.

### Q4 — A consequence worth naming before task 42 meets it. **Not this task's to fix.**

Tokens are memory-only (L16 lists the whole carve-out and tokens are not in it), and **silent
re-authentication is not built** — `accessToken.renew()` always rejects with `RENEWAL_NOT_BUILT`, and
`cleanReauth.ts`'s header records that no task in `tasks.md` builds it. So after any page reload a visitor
holds no token and is unauthenticated by this guard's only available question.

Once task 42 attaches the authenticated shell, **every reload will land on the gate**. That is
spec-consistent — L3's Q5 resolution says silent re-auth through the SAS session cookie is what restores
it — but the restoring half does not exist, so the gate will look broken in a real browser while being
correct against the spec. Named here so task 42 does not discover it as a bug in T40.

## Phase 2

Task implementation brief.
