# frontend · T47 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Task** | T47 — Registration acknowledgement is identical |
| **Requirement** | R2 |
| **Named test** | `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` (`package.md` §8, Phase 1a) |
| **Produced** | 2026-11-27 |
| **Branch** | `spec/service-specs-and-ai-framework` @ `03e78b78` |

> **Task statement (verbatim, `spec/frontend/tasks.md` task 47):**
> **Registration acknowledgement is identical.** Existing and new emails show the same acknowledgement screen. Verifies `registrationAcknowledgementIsIdenticalForExistingAndNewEmails`. Cites R2.

No code was written in this phase. No file under `spec/` was read for modification.

---

## 1. Architecture summary

This is **not** a backend service, and the generated prompt's default vocabulary (JPA/Flyway, outbox,
resource-server security, Testcontainers) does not apply. The target is `frontend/` — a React +
TypeScript mobile-first PWA. The prompt header says the implementation lands in `services/frontend/`;
**that directory does not exist.** The package is at repository-root `frontend/`. This is a generator
artefact, not a relocation, and is logged under §5.

**Stack as actually installed** (`frontend/package.json`, not the spec's aspiration): Vite, React 19,
React Router 7 (`react-router`, not `react-router-dom`), i18next + react-i18next, Vitest + jsdom,
oxlint, Tailwind 4, `vite-plugin-pwa` with Workbox, and the generated `@themistra/api-client`.

**Two of the five technologies `agents.md` lists as "confirmed stack" are still not installed:
TanStack Query and Zustand.** `RegisterScreen`'s own header records this and the reason (L13 has had
no consumer in 46 tasks; a one-shot public mutation has no cache to invalidate and no cross-screen
state to share). This matters to T47 only if the acknowledgement needs state carried across a
navigation — see §5.

**Module layout** (`frontend/src`, 90 source files / 37 test files):

| Directory | Role |
|---|---|
| `app/` | Router, route table, basename, layout, home screen |
| `app/ui/` | 13 design-system primitives (T173). `className` and `style` are absent from their types. |
| `auth/` | The OIDC flow only — sign-in, callback, signed-out, session gate, redirect |
| `features/account/` | Account operations that are **not** the OIDC flow. Registration lives here. |
| `api/` | The client boundary; `createPublicAuthApi()` |
| `i18n/` | `catalogue.ts` (the single string catalogue) + `i18n.ts` |
| `profile/`, `pwa/` | Out of scope for this task |

**Persistence / events / outbox:** none. There is no client-side persistence in scope. `agents.md`
LOCKS sessionStorage as the *only* sanctioned client storage, for PKCE and resume state alone
(`design.md` L16), and forbids persisting any PII. **A registration email address is PII.** Any design
that carries the submitted address across a navigation must not reach for storage — §5.

**Security:** the SPA is a public OIDC client; registration is a *public, unauthenticated* operation.
`RegisterScreen` is explicitly forbidden from importing `auth/` — it holds no token and starts no
session.

## 2. Existing code this task touches

### Already exists

| Path | State relevant to T47 |
|---|---|
| `features/account/RegisterScreen.tsx` | 111 lines. **Already satisfies R2's "no client-side branching".** |
| `app/routes.tsx` | Declares `'register/sent'` in `PUBLIC_ROUTE_PATHS` — **with no element.** |
| `i18n/catalogue.ts` | `register.*` holds six keys (D7 froze the count), incl. `success` |
| `app/ui/*` | `Screen`, `Heading`, `Text`, `StatusRegion`, `TextLink`, `Banner`, `Surface` all exist |
| `features/account/passwordLength.ts` | T43–46's advisory bounds. Untouched by R2. |
| Two registration suites (T44/T45 named tests) | Must keep passing; T47 must not re-prove their table |

**The single most important finding.** `RegisterScreen` already refuses to branch on the response, and
says so in its own header:

> **NOTHING BRANCHES ON THE RESPONSE** (R2, L4). The handler awaits the call and reports success; it does not
> read the body, does not look at the status, does not retry and adds no delay.

It is deliberate that `openapi-fetch` *resolves* for 202, 409 and 500 alike, so all three reach the
same success branch. **So R2's request-handling clause is already met by T43's work** — T47 does not
have to build it, and a test that only re-asserts it would measure nothing new.

### Does not exist, and is this task's

1. **An acknowledgement screen.** No component renders the "check your email" state as a screen.
2. **An element for `register/sent`.** The path is declared and deliberately element-less:

   > `register/sent` stays element-less because task 47 owns the acknowledgement.

   Today, navigating there renders a blank document. This is the same defect class as the already-recorded
   `/` blank-page and blank verify-link findings.
3. **A named suite** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx`.

### The design tension this task inherits (not resolvable in Phase 0)

T43 chose to **announce** success in a `StatusRegion` rather than **navigate**, and recorded why:

> It announces its result and does not navigate (D1, OQ-T43-5). `POST /accounts` always answers 202, and
> task 47 owns both the acknowledgement screen and `register/sent` — which is a declared path with no
> element today, so navigating there would hand the visitor a blank document.

So the deferral was explicitly conditional on T47 filling the route. T47 must decide between an inline
acknowledgement state and a navigation to `/app/register/sent`, and that choice determines whether
`RegisterScreen` changes at all. **That is a Phase 1/2 decision and is deliberately not made here.**
It carries a real constraint: a navigation makes `register/sent` directly addressable, so the screen
must render correctly for a visitor who arrives with no preceding submit — a deep link, a refresh, a
shared URL — and must do so **without** the email address, which cannot be persisted (§1).

## 3. Established patterns to follow

- **No hardcoded copy.** Every string comes from `i18n/catalogue.ts` via `useTranslation()`.
  `agents.md` LOCKS this `[ALL]`.
- **One spelling of a route.** Paths are exported constants in `routes.tsx` (`REGISTER_PATH`, …) and
  attached by *matching an existing constant*, never by adding a literal to the map (L14).
- **Primitives only.** Screens compose `app/ui`; `className`/`style` are not in the primitives' types,
  so visual choices belong in the primitive, not the screen.
- **No hand-written `fetch`.** All backend calls go through the generated client (`agents.md`, LOCKED).
- **Enumeration safety is copy-level, not just logic-level.** `agents.md` requires copy, redirect
  targets, visual styling *and* the SPA's own rendering timing to be identical across account states.
  **R2's "identical copy and layout" therefore includes the redirect target.** Two different
  destinations would violate it even with identical words.
- **Dense explanatory headers.** Every file carries a comment explaining *why*, citing requirement and
  task IDs, and naming what it deliberately did not do. This is the house style and is not optional.
- **Deferrals are named, not implied** — `OQ-T43-5`, `FU-T43-2`, `OQ-T43-9` are all live.

## 4. Testing conventions

- **Vitest + jsdom**, run with `pnpm run test` from `frontend/`. No Testcontainers; no fixed `Clock`
  pattern; no ArchUnit (those are backend conventions and do not exist here).
- **One file per named test from `package.md` §8**, filename = test name + `.test.tsx`.
- **Raw `react-dom/client` + `act`**, not Testing Library. `IS_REACT_ACT_ENVIRONMENT = true` is set
  explicitly at module scope.
- **`createMemoryRouter` + `RouterProvider`** to mount a screen inside a route context.
- **Real i18n**, via `createI18n()` and the real `en` catalogue — not a mock. Assertions compare
  rendered text against catalogue values rather than typed-in literals.
- **Suites are self-contained; helpers are COPIED, not imported.** There is no shared test-util module.
  That is `FU-T43-2`, recorded three times and deliberately not fixed inside a task with no mandate.
- Several suites read their own source file off disk (`node:fs/promises` + `expect.getState().testPath`)
  to assert structural properties that behaviour cannot reach.
- **Commit tests before any mutation run** — the mutation harness reverts with `git checkout` and
  silently destroys uncommitted test edits.

## 5. Known gaps and unknowns

1. **`services/frontend/` does not exist.** The prompt's "Outputs" line names it. The real path is
   `frontend/`. Generator boilerplate; flagged so Phase 13 does not cite a path that is not there.
2. **The navigate-vs-inline decision is open** (`OQ-T43-5`). Phase 1/2 owns it. It is the difference
   between "T47 adds one screen and one route element" and "T47 also modifies `RegisterScreen`".
3. **How the acknowledgement handles a cold arrival is unknown to me.** If T47 navigates, then
   `/app/register/sent` is directly addressable and must work with no prior state and no email address
   (which `agents.md` forbids persisting). Whether R2 intends the screen to name the address at all —
   `catalogue.ts`'s `register.success` does not — is a Phase 1 question. **I do not know** what the
   spec intends here and will not guess.
4. **Whether `register.success` is reused or replaced.** D7 froze `register.*` at six keys. An
   acknowledgement *screen* plausibly needs a heading and body, which would be new keys. **I do not
   know** whether D7's freeze was scoped to T43 only or is a standing constraint; Phase 1 must read
   D7 directly rather than infer it from the comment.
5. **`agents.md` requires a live-region announcement on every screen** and keyboard operability. How
   that applies to a screen that is itself the result of a navigation — announce what, and when — is
   not something Phase 0 can settle.
6. **No backend was consulted.** R2 says the server answers identically whether or not the address
   exists ("backend R2"). I have not verified that against the running auth service in this phase, and
   `agents.md`'s 2026-10-22 note requires tasks to be checked against the local Docker stack. **The
   demo stack and the local stack are different things**; the public demo was torn down on 2026-11-26
   and is irrelevant here. Whether `infra/local/` must be running for this task's verification is a
   Phase 5/6 concern, but worth flagging now: this task's claim is a *client* claim, and a client that
   never branches is identical by construction regardless of what the server returns.
7. **Baseline suite status at the start of this task is being measured separately** and is recorded in
   Phase 1 rather than asserted here.

---

**Phase 0 complete.** One artifact written. Next phase is 1 (Specification Extraction, Claude Sonnet),
which derives the LOCKED decisions from `design.md` §4a and resolves gaps 3, 4 and 5 above from the
spec rather than from inference.
