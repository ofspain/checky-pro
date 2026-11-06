<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T35 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Task:** "Sign-out clears in-memory token state. Verifies `signOutClearsMemoryAndEndsSasSession`.
> Cites R19." (`tasks.md:63`)

## 1. Architecture summary

`frontend/` is a Vite + React 19 + TypeScript single-page application served under the `/app` basename
(`src/app/routes.tsx:10`). **The phase-0 prompt's headings assume a Spring service** — persistence,
outbox/idempotency, resource-server security, Flyway, Testcontainers, ArchUnit, a fixed `Clock`. None of
those exist here, and I will not invent frontend analogues for them. What this service actually has:

| Concern | How it works here |
|---|---|
| **Modules** | `api/` the client boundary and cross-tab coordination · `auth/` the OIDC flow and token state · `app/` the shell, route table and mount · `profile/` the two account reads · `i18n/` the string catalogue · `pwa/` the service worker and its path policies |
| **Persistence** | **none.** The only client-side write permitted is L16's exhaustive sessionStorage carve-out — `state`, `nonce`, `code_verifier`, the post-login return target, and R29's mid-task form input. `src/auth/returnTarget.ts` is the piece of that carve-out this task meets. |
| **Events** | no outbox. The one event channel is `BroadcastChannel('themistra.session.renewal')` (`api/crossTabRenewal.ts:18`), L18's mechanism for sharing renewal **and sign-out** across tabs. Today it carries two renewal outcomes only (`:48`). |
| **Security posture** | public OIDC client, PKCE, no secret in the bundle (L1, L10). **The access token lives in one module-level `let` and nowhere else** (`auth/accessToken.ts:24`), which is L3: never written to storage, logs or analytics. |
| **Error handling** | typed result objects, not exceptions, at the module seams (`profile/readAccount.ts`, `auth/codeExchange.ts` return `null` or a tagged failure). Copy for any failure comes from the catalogue and names no cause (L4). |
| **Configuration** | compile-time constants in the module that owns them (`APP_BASENAME`, `SIGN_IN_PATH`, the channel and lock names). No runtime config file, no secrets. |

## 2. Existing code this task touches

**Already built — T35 consumes it, and must not widen it:**

| File | What it already is | Relevance |
|---|---|---|
| `src/auth/accessToken.ts:97-99` | **`clearAccessToken()` already exists and is already idempotent.** Built for the discarded-callback path; its own comment says it exists because `LocalSession.adopt` takes `string`, not `string \| null`, and widening that interface would edit code tasks 5 and 6 are built on. | This **is** "clears in-memory token state" for the access token. T35 does not need a new primitive for it. |
| `src/auth/accessToken.ts:3-21` | The module header states the design that makes T35 small: one copy, one writer, no second module. It says in so many words that another copy would be "a thing sign-out would then have to find and clear". | T35 is the task that cashes that promise. The question T35 must answer is **what else is in memory**, not how to clear the token. |
| `src/auth/signInNotice.ts:34` | The **second** module-level mutable in the package — a single pending notice, single-read. | Is a pending notice part of "in-memory token state"? It is not token state, but a deliberate sign-out that lands on `/app/signed-out` (T37) with a stale `session-ended` notice queued would be wrong. T35 must decide this, not assume it. |
| `src/auth/returnTarget.ts` | The sessionStorage carve-out item that survives a navigation. | A deliberate sign-out should not leave a return target that a later sign-in silently honours. Again a decision for phase 2, flagged here. |
| `src/api/crossTabRenewal.ts:18,48` | The channel L18 names for sign-out, carrying only renewal outcomes today. | **T39** owns propagation. T35 must not build it, and must not design itself into a shape T39 cannot extend. |
| `src/app/routes.tsx:31` | `'signed-out'` is in `PUBLIC_ROUTE_PATHS` with **no element**. | **T37** owns that screen. T35's clearing must not depend on it. |
| `src/auth/cleanReauth.ts:26-27` | Task 33, just closed, records that `/app/signed-out` "was built for a DELIBERATE sign-out (R19)". | The adjacent path, already reasoned about from the other side. |
| `src/i18n/catalogue.ts` | `signIn.*` and `callback.*` only. **No `signedOut.*` group exists.** | If T35 needs user-facing copy it is new; more likely T35 needs none and T37 adds the group. |

**New in this task:** a sign-out module (name not yet chosen — phase 2). **Nothing in `src/` performs or
offers sign-out today:** `grep -rni "signout\|sign-out\|logout"` over production source returns only the
route path, three doc comments, and the service-worker path policies. There is no button, no shell
navigation, and `AppShell.tsx` renders nothing but a `<div>`.

## 3. Established patterns to follow

- **One module-level mutable per fact, and it is scanned.** T19's and T20's suites scan their own production
  files for module-level mutable state and fail on a hit (`accessToken.ts:7-8`). A new sign-out module must
  hold no state of its own.
- **Inject the effect, do not import the router.** `cleanReauth.ts` takes `navigate` as a parameter and a
  source scan forbids it importing `react-router` or assigning `location`. Sign-out faces the same choice and
  the same scrutiny, with the twist that **R19's navigation is a full-page one to SAS** (R57: `/connect/logout`
  is in the navigation denylist, `pwa/navigationDenylist.ts:19`), not a router navigation. That is T36's.
- **Compile-time exhaustiveness where a union drives a table.** `signInNotice.ts:26-32` pins its map with
  `satisfies` plus an `AssertNever` guard from the other direction.
- **Catalogue-only strings** (L11), and copy that names the event, never the cause (L4).
- **Delegate rather than duplicate a write.** `adopt` delegates to `retainAccessToken` so exactly one line
  assigns the token (`accessToken.ts:55-61`). A sign-out that clears several things should route each through
  the primitive that owns it rather than reaching for the variables.

## 4. Testing conventions

- **Vitest 5.0.5 + jsdom 30.1.3**, run as `npx -y pnpm@12.14.0 exec vitest run …` — bare `pnpm` is not on
  this host. **The exit code is the verdict**, not the pass count.
- **One test file per task, named for `package.md` §8's test name**, colocated beside the production module.
- **Source-scanning cases** are a first-class instrument here: a test reads its own production file and
  asserts what it does *not* contain (no storage write, no router import, no reason binding).
- **A per-task mutation harness** in `.ai/prompts/frontend/T35/artifacts/07-mutation-harness.py`, which
  reverts the tree with `git checkout` — **so tests must be committed before it runs.** It must assert its
  own baseline, and canaries are scored apart from regressions.
- **No Testcontainers, no ArchUnit, no injected `Clock`** in this service. There is no backend under test.
- **One known environmental failure on this host:** `src/api/lintRejectsHandWrittenBackendFetch.test.ts`,
  because this `oxlint@1.87.0` prints no summary line for the assertion that parses it. Pre-existing and
  unrelated; the whole-suite baseline is currently **1574 passed / 1 failed of 1575**.

## 5. Known gaps and unknowns

1. **Nothing triggers sign-out, and nothing can.** There is no affordance anywhere in the SPA, and the
   authenticated routes have no elements at all. This is the same dormancy T33 closed on as deviation D6 and
   escalated as **OQ-T33-2**: tasks 5, 6, 30, 31 and 33 are built and uncalled. T35 will make six. I do not
   know whether the owner intends this until tasks 173/174 (design system, home screen).
2. **No id_token is retained anywhere.** `codeExchange.ts:76-79` returns one, `CallbackScreen.tsx:156`
   validates its nonce, and then it is dropped. SAS's end-session endpoint conventionally needs an
   `id_token_hint` **or** a `client_id` to honour `post_logout_redirect_uri`. R19 names only the redirect URI,
   so this may be fine — but if T36 needs the hint, **the token to hint with no longer exists by then**, and
   retaining it is a change to L3's "tokens in memory only" posture that T35 would be the one to make. I do
   not know which; it is the first thing phase 1 must settle, because it decides whether T35's clearing has a
   second thing to clear.
3. **Spec drift on this task's own named test, a third instance of the pattern.** `package.md:123` names
   `signOutRevokesTokensClearsMemoryAndEndsSasSession` — with the **revoke** step in it — while `tasks.md:63`
   and `:64` both name `signOutClearsMemoryAndEndsSasSession`, and **R19 was revised on 2026-10-10 to remove
   the `/oauth2/revoke` call entirely** because this client holds no refresh token. `package.md` §8 was not
   updated with it. Recorded, not fixed (`spec/` is not mine to edit). The sibling instances are
   `package.md:171`'s mapping for the struck-out task 34 and §11's unstruck Q5.
4. **Tasks 35 and 36 cite the same named test**, so the file one creates the other extends. I do not know
   which task is expected to create it; phase 1 must pick a reading and say so.
5. **Whether "in-memory token state" means only the access token.** The literal reading is one line of code
   that already exists. The useful reading includes the pending notice and the stored return target. I do not
   know the owner's intent and will not guess here — phase 1 derives it from R19 and L16, phase 2 freezes it.
6. **Q15 blocks task 38**, not this one, but it is the same requirement: whether sign-out then sign-in shows
   the password step depends on what SAS does with its session cookie, which the SPA cannot assert.

**Phase 0 complete.**
