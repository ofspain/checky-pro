# frontend · T29 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim, `spec/frontend/tasks.md` task 29):** *Uniform sign-in failure. Every SAS failure
shows one uniform message. Verifies `identityErrorsIndistinguishableAcrossAccountStates`. Cites R10, R30.*

No code written. Everything below was read or measured, not assumed.

---

## 1 · Architecture summary

The prompt template is written for a Spring service and asks about JPA, Flyway, an outbox and a resource
server. **None of that applies.** `frontend/` is a React 19.3.1 / TypeScript SPA built by Vite 8.3.5 in a pnpm
workspace, with no persistence, no events and no server of its own. The equivalent list for this package:

| Concern | Where it lives |
|---|---|
| Composition | `app/mountShell.tsx` → `<StrictMode><I18nextProvider><AppShell><RouterProvider>` |
| Routing | `app/routes.tsx` — one route table, `APP_BASENAME = '/app'`, `createBrowserRouter` |
| Copy | `i18n/catalogue.ts` (one locale, `en`, `as const`) via `i18n/i18n.ts`'s `createI18n()` |
| Identity | `auth/` — 9 production modules: the authorize request, PKCE, the code exchange, the nonce check, the two screens, the in-memory token, the return target, the sign-in notice |
| API boundary | `api/` — `client.ts`, `sessionBoundary.ts` (the single shared 401 renewal), `rateLimitNotice.ts` |
| Offline | `pwa/` — the worker, the navigation denylist, the API request policy |
| Persistence, such as it is | `sessionStorage`, exactly four keys, enumerated by T26 |

**Security posture relevant to T29.** The access token lives in one module-scoped variable
(`auth/accessToken.ts`, L3) and is never stored. Interactive login is **not in this bundle**: L2 puts the
password, TOTP and recovery-code steps on the Spring Authorization Server, so `/login` is a server-rendered
page that the SPA denylists (`pwa/navigationDenylist.ts:20`) and never renders.

## 2 · Existing code this task touches

Nothing is new. Every module T29 reasons about already exists.

| Module | What it contributes to R10/R30 | Status |
|---|---|---|
| `auth/SignInScreen.tsx:21` | a three-state machine — `'idle' \| 'starting' \| 'failed'` — and **one live region** (`:70-76`) that renders at most one of three sentences | exists |
| `auth/SignInScreen.tsx:41-47` | the `catch` that produces `'failed'`. **The rejection is discarded, not logged**, and the comment says why: its text can name the PKCE verifier contract | exists |
| `auth/CallbackScreen.tsx:89-99` | the single `discard` closure every callback failure shares — `clearAccessToken()`, `setSignInNotice('discarded')`, `navigate('/sign-in', { replace: true })` | exists |
| `auth/signInNotice.ts:13` | `SignInNotice` is a **one-member union**, in memory, single-read | exists |
| `i18n/catalogue.ts:10-19` | the whole identity vocabulary: six `signIn.*` keys, two of which are failure sentences (`failed`, `discarded`) | exists |
| `api/sessionBoundary.ts:48` | `createSessionBoundary(tokens, onRenewalFailed, …)` — where a session that cannot be resumed surfaces | exists, **but unwired** (§5) |
| `api/rateLimitNotice.ts:19` | the 429 notice, deliberately blind to the body and `Retry-After` so the copy is the same for every 429 | exists, **but unwired** (§5) |
| `app/routes.tsx:51-64` | the route table. **Exactly two routes have an element**: `sign-in` and `callback` | exists |

**What the SPA can observe about an account state, measured in the backend.** This is the fact T29 turns on,
and it is stronger than R30 asks for:

`authn/AccountUserDetailsService.java:43-58` collapses every account state into one of two `UserDetails`
flags, and `authn/TotpAuthenticationProvider.java` then throws **the identical
`BadCredentialsException("Bad credentials")`** for all of them:

| Account state | Path | Where it throws |
|---|---|---|
| unknown, deleted | `findLoginView` empty → `UsernameNotFoundException("Bad credentials")` | caught in `loadUserDetailsOrFail`, rethrown as `BadCredentialsException("Bad credentials")` |
| `PENDING_VERIFICATION`, `SUSPENDED` | `.disabled(true)` | `verifyAccountUsableOrFail` |
| `LOCKED` **and still inside the interval** | `.accountLocked(true)` | `verifyAccountUsableOrFail` |
| active, wrong password | — | `verifyPasswordOrFail` |
| MERCHANT/ADMIN, no confirmed enrollment | — | `authenticate`, the `isMfaRequired` branch |

Its class comment records that a distinct signal was **considered and rejected** as a password-correctness
oracle. The source comment at `AccountUserDetailsService.java:45` says it outright: *"uniform message: unknown
and deleted are indistinguishable (enumeration defense)."*

Two consequences for T29's scope, both of which need to be settled at Phase 1, not here:

1. **A login failure never returns to the SPA.** It re-renders SAS's own `/login`. The SPA's first observation
   of a sign-in attempt is the callback, which is reached only once the authorize request has succeeded or
   been refused — so the five account states are not merely indistinguishable to the SPA, they are
   **unobservable** by it. This is the same shape as T28-D1.
2. **The check order is load → usable → password.** An unknown, locked or suspended account is refused
   *before* any password comparison, which is a timing difference. R30 closes that door explicitly: *"Server
   response timing is out of SPA scope."*

## 3 · Established patterns to follow

- **Copy comes from the catalogue, never from a literal in a component or a test.** Nine suites assert against
  `en.signIn.*`.
- **One closure per outcome, not one per cause.** `CallbackScreen`'s `discard` has five call sites and exists
  in that shape specifically so the failures cannot drift apart (L4).
- **Comment-stripping before any source scan.** `CallbackScreen.tsx:84-85` names task 28 inside a comment
  while explaining this very rule, so an unstripped scan reads a file's own justification as the defect.
- **Narrow, name-keyed scan patterns, each held by a case from both directions.** T28's measured example: a
  bare `state ===` scan has three legitimate hits in this repository. And a pattern wide enough to catch
  `locked`/`suspended`/`role` in copy would block work tasks 81–89 are *specified* to do — the same error
  class as a pattern that is too narrow.
- **Vacuity is the dominant risk in an absence proof.** Every instrument must be shown to report DIRTY first
  (T22-D14). T28 ran 27 controls against 20 claims for this reason.
- **A mutation harness is committed with the artifacts and run only against committed source** — it reverts
  with `git checkout -- src/`.
- **`tsc --noEmit` and `oxlint src` clean; the vitest exit code is the verdict, not pass counts.**

## 4 · Testing conventions

| | |
|---|---|
| Runner | `vitest@5.0.5` with `jsdom@30.1.3`, `environment: 'jsdom'` in `vite.config.ts`, **no `setupFiles`** |
| Invocation | `npx -y pnpm@12.14.0 exec vitest run` from `frontend/` — bare `pnpm` is not on this host |
| Rendering | `createRoot` + `act` by hand; `createMemoryRouter` with `initialEntries` for an arrival |
| Isolation | `vi.resetModules()` in `afterEach`, which is why a stateful module must be imported **dynamically inside** the function that reads it — a static import is a different instance than the screens hold |
| Stubs | `vi.stubGlobal`, `Storage.prototype` spies, a `fetch` spy; all installed **before** the code under test runs, with a control proving the ordering |
| Source scanning | `node:fs/promises` walk of `src/`, `.test.tsx?` excluded, comments stripped, assertions over *files* rather than `path:line` |
| Known failure | `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints no summary line. Pre-existing and unrelated; fails before and after every change |
| Baseline at T28 close | **1342 passed / 1 failed (1343)**, 24 files |

No Testcontainers, no fixed `Clock`, no ArchUnit — those are the backend packages' conventions.

## 5 · Known gaps and unknowns

1. **The named test has three owners.** `identityErrorsIndistinguishableAcrossAccountStates` is claimed by
   task **29** (this one, "every SAS failure shows one uniform message"), task **58** (*"Unknown, missing,
   locked, suspended, and deleted accounts render identical error copy and treatment"*) and task **136**
   (*"Enumeration gate"*). Three tasks, one file name. **Which slice is T29's is a Phase 1 question I cannot
   settle from the task statement alone** — T29 cites R10 and R30, 58 cites R30 and L4, 136 cites L4 only. The
   obvious reading is that T29 owns the *sign-in* axis and 58 owns the *rendered account-state* axis, but that
   is inference and it needs to be frozen deliberately at Phase 4, because the three tasks will otherwise
   write over each other's file.
2. **The composition root does not exist.** `mountShell.tsx:20-28` wires the i18n provider, the shell and the
   router — and **nothing else**. It never calls `createSessionBoundary` or `createRateLimitNotice`, so
   `onRenewalFailed` has no production caller and `<RateLimitNotice>` is never mounted. Any part of R30 that
   depends on what the SPA renders when a session cannot be resumed has **no production code to verify**.
   Memory records this root as unowned by any task.
3. **No screen renders an account state, because no screen renders anything.** Measured: two routes have an
   element; every other path in the table, including the catch-all, is `{ path }` and renders blank. There is
   no CSS file in `src/`, no Tailwind, and no `className` in any production component — so R30's *"visual
   treatment"* clause is trivially satisfiable and task **173** owns making it non-trivial. T28 recorded the
   same thing as T28-D2.
4. **`oidcRoundTripEndToEnd` still has no suite and no owning task.** It is the only test that could read the
   page a refused visitor actually sees, which is what leaves T28-D1 standing and will leave T29's equivalent
   standing too.
5. **OQ1 is still open.** The live SAS `/login` form carries `username`, `password` and `_csrf` only, while
   `TotpAuthenticationDetailsSource` requires `mfaCode` in the same POST, so no real browser can complete an
   MFA sign-in today. Carried from T27 and T28. Not T29's to fix.
6. **I do not know** whether R30's *"redirect target"* clause is meant to reach the signed-out route
   (`'signed-out'` is in `PUBLIC_ROUTE_PATHS` with no element) or only the sign-in start. Phase 1 must read
   `design.md` §4a and §4c rather than guess.
7. **I do not know** what, if anything, T29 is expected to assert about the 401/renewal path, given §5.2.
   Phase 1 question.

---

**Phase 0 complete.** Ready for Phase 1 — specification extraction.
