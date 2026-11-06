<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T37 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

Every claim below was measured in the repository during this phase. Where I do not know, it says so.

## 1. Architecture summary

React 19 / TypeScript SPA under `frontend/`, Vite, Vitest + jsdom. No persistence, no events, no outbox — this
is a browser client, so items 3's "JPA/Flyway, outbox/idempotency, resource-server security" do not apply and
are not fabricated here.

- **Routing** — `app/routes.tsx` is the single source of the route set. `APP_BASENAME = '/app'`; 9 public and
  11 authenticated paths plus a catch-all, 20 in all, declared relative.
- **Security posture** — tokens in memory only (L3). `auth/accessToken.ts` and now `auth/idToken.ts` each hold
  one module-level `let`. `sessionStorage` holds exactly L16's carve-out, pinned by task 22.
- **Copy** — every user-facing string lives in `i18n/catalogue.ts` (L11), nested by screen, ordered "in the
  order a visitor meets them, not alphabetical."
- **Sign-out** — task 35 clears (`auth/signOut.ts`), task 36 hands over (`auth/endSession.ts`). Both closed.

## 2. Existing code this task touches

**Already exists**

| Thing | Where | State |
|---|---|---|
| the `signed-out` route | `app/routes.tsx:31` | declared in `PUBLIC_ROUTE_PATHS`, **a bare literal with no named constant and no element** |
| `endSession()`'s target | `auth/endSession.ts` | `post_logout_redirect_uri` = `${origin}/app/signed-out` — T36 already points SAS here |
| the screen pattern | `auth/SignInScreen.tsx`, `auth/CallbackScreen.tsx` | `useTranslation()`, a `t('…')` per string, no literal copy |
| the catch-all | `app/routes.tsx:61` | `{ path: '*' }` with no element, so an unmatched path renders nothing (L11) |
| `routesResolveUnderAppPrefixOnly` | `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | **exists**, 10 cases |

**New**

- A screen component for `/app/signed-out`, and its catalogue entry.

**Does NOT exist, and the task statement names it:** `publicRouteSetIsExactlyTheDeclaredSet` is in no source
file — only in `spec/frontend/package.md`, `tasks.md` and `spec/frontend/artifacts/review-resolution.md`.
`tasks.md:71` gives it to **task 40** ("Public route set is exact… Cites R28, L14"). So T37 and T40 share a
named test that neither has created yet.

## 3. The handoff T37 will trip, measured

`routesResolveUnderAppPrefixOnly.test.tsx:17` declares

```ts
const SCREENS_ATTACHED_SO_FAR = ['sign-in', 'callback'] as const;
```

and `:158` asserts `routesWithElements(appRoutes)` equals exactly that. `routesWithElements` counts `element`,
`Component` **and** `errorElement`, recursively.

**So attaching any screen to `signed-out` turns that case red.** This is the same shape of deliberate handoff
T35's AC7 left for T36: a closed suite that fails precisely when the next task does its job, with the failure
naming the list to extend. T37 will have to extend `SCREENS_ATTACHED_SO_FAR` — which is an edit to a closed
task's test, permitted under the rule T36's phase 9 wrote (an instrument that *must grow to admit new state*,
as opposed to one merely incomplete for what the new task owns).

I have **not** verified whether any other closed suite pins the attached-screen set; phase 1 should check
before assuming this is the only one.

## 4. Established patterns to follow

- **Screens** take no props, call `useTranslation()`, and read every string through `t()`. `SignInScreen`
  holds local `useState` and a `useRef` guard; `CallbackScreen` runs its work in `useEffect`. A screen with
  nothing to do needs neither.
- **Catalogue entries** are nested per screen with comments explaining *why* the wording is what it is —
  particularly where L4 (enumeration-safety) constrains it. `signIn.sessionEnded` is the closest model: it
  names an **event** and never a cause.
- **No literal copy in a component**, enforced package-wide by L11 and by existing scans.
- **Tests** are named after the task's named test in `tasks.md`, never after a module (29 files, no
  exceptions). Vitest + jsdom; no Testcontainers, no `Clock`, no ArchUnit in this package.
- **Source scans with controls** are the house instrument: every scan is paired with a planted positive.

## 5. Known gaps and unknowns

- **What the screen should SAY is not specified anywhere I can find.** There is no `signedOut` catalogue entry
  and no copy in `requirements.md` or `design.md` for this screen. `auth/signOut.ts`'s own comment says *"Task
  37's screen will say what a deliberate sign-out means"* — which is a reason for the screen, not its words.
  **I do not know** what copy the owner wants; phase 1 must extract it from R19/L4 or raise it.
- **Whether the screen offers a way back to sign-in is unspecified.** A link or button would be the obvious
  affordance, and it would also be the first thing in the SPA that could lead *to* sign-in from a public page
  other than the sign-in screen itself. **I do not know** whether that is in T37's scope.
- **`signed-out` has no named route constant** (`routes.tsx` publishes `SIGN_IN_PATH`, `CALLBACK_PATH`,
  `HOME_PATH` only). T36 worked around this by declaring the spelling privately and asserting
  `PUBLIC_ROUTE_PATHS` contains it (T36-D2). T37 attaching a route element will need the literal again.
  Whether T37 may add the export — `routes.tsx` was closed to T36 — is a phase 1/2 question.
- **The generated prompt's own named-test list is inconsistent with the task statement.** The task statement
  says T37 verifies `publicRouteSetIsExactlyTheDeclaredSet`; the prompt's "Named tests (`package.md` §8)" line
  lists `signOutRevokesTokensClearsMemoryAndEndsSasSession`, `signOutThenSignInRequiresCredentials` and
  `routesResolveUnderAppPrefixOnly` — **not** the test the statement names. The first of those three also
  still carries `Revokes`, which Q5 removed from R19 on 2026-10-10. Recorded, not resolved: `spec/` and
  `.ai/generate.py` are both outside this task.
- **Nothing calls `endSession()`**, so no visitor can reach `/app/signed-out` by signing out. T37's screen
  will be reachable only by typing the URL. Seventh-and-eighth dormant task (**OQ-T36-5**).

**Phase 0 complete.** No code written. Next is phase 1 — specification extraction.
