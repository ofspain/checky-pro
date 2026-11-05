<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T33 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |
| **Requirement** | R22 |

No code written. What follows is what exists, read from source.

## 1. Architecture summary

The SPA is a React 19.3.1 / TypeScript package at `frontend/`, built by Vite, served under the `/app`
basename behind the local edge. There is no persistence layer, no outbox and no event bus — the backend
conventions the phase prompt recites (JPA/Flyway, Testcontainers, fixed `Clock`, ArchUnit) belong to the
Spring services and do not apply here. The modules this task sits inside:

| Module | What it holds |
|---|---|
| `src/api/` | `client.ts` (generated-client factories), `sessionBoundary.ts` (the single-flight 401/renewal middleware), `crossTabRenewal.ts` (Web Locks leader + BroadcastChannel), `rateLimitNotice.ts` |
| `src/auth/` | `pkce.ts`, `authorizeRequest.ts`, `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts` (the tab's token), `returnTarget.ts`, `signInNotice.ts`, and the two screens `SignInScreen.tsx` / `CallbackScreen.tsx` |
| `src/app/` | `AppShell.tsx`, `routes.tsx` (the whole route set), `mountShell.tsx`, `RateLimitNotice.tsx` |
| `src/i18n/` | `catalogue.ts` — every user-facing string, 26 lines, three groups |
| `src/profile/` | `readProfile.ts`, `readAccount.ts`, `accountStatus.ts` |
| `src/pwa/` | service worker registration, caching denylists |

**Security posture relevant here.** The access token lives in one module-level `let` in
`auth/accessToken.ts` and is never written to storage, a store, a context or a second module (L3, L16).
A reload therefore ends the session by design; silent re-authentication is what was meant to recover from
it. Nothing about why a renewal failed is allowed to reach the visitor (L4) or to cross tabs — the
cross-tab failure message is the bare `{ type: 'renewal:failed' }`, carrying no reason
(`crossTabRenewal.ts:46`).

## 2. Existing code this task touches

**Already exists, and T33 does not get to redesign it:**

| File | What is there now |
|---|---|
| `api/sessionBoundary.ts:48-50` | `createSessionBoundary(tokens, onRenewalFailed, options)`. **The failure hook already exists as a parameter.** |
| `api/sessionBoundary.ts:69-74` | On a rejected renewal it schedules `onRenewalFailed(reason)` in a microtask — "so a throwing hook can't affect the waiters" — and re-throws, so every waiting call gets its original 401. It runs **once per renewal**, not once per waiting call. |
| `api/sessionBoundary.ts:108-116` | A request that waited behind a *failed* renewal returns its 401 without starting another: `waitedOnFailedRenewal`. This is the no-error-loop guarantee R21 asks for, and it is built. |
| `auth/accessToken.ts:65-69` | `renew()` **always rejects**, with the fixed message `RENEWAL_NOT_BUILT`. |
| `auth/accessToken.ts:88-99` | `clearAccessToken()` — idempotent, and its own doc comment already cites R22: leaving a token held after a discarded second sign-in "would otherwise leave the old token held - a half-authenticated tab, which R22 forbids." |
| `api/crossTabRenewal.ts:26-31,46` | `SiblingRenewalFailedError` and the reasonless `renewal:failed` outcome. A follower tab adopts a sibling's failure as its own rather than renewing again. |
| `auth/signInNotice.ts` | A module-scoped, **single-read** notice; `takeSignInNotice()` clears on read. The union `SignInNotice` has exactly one member, `'discarded'`, and the file says so: "a forward-compatibility hook, not a commitment". |
| `i18n/catalogue.ts:18` | `signIn.discarded` — "Your sign-in did not complete. Try again." Neutral by requirement (L4). |
| `app/routes.tsx:33-46` | `PUBLIC_ROUTE_PATHS` (nine, including `sign-in` and `signed-out`) and `AUTHENTICATED_ROUTE_PATHS` (eleven). |
| `auth/SignInScreen.tsx:24,73` | Takes the notice once per mount and renders it only while `status === 'idle'`. |

**New in this task:** the named test `failedRenewalTriggersCleanReauthWithoutHalfSession` does not exist in
any form — `find src -name 'failedRenewal*'` returns nothing. Whatever production module routes a failed
renewal to clean re-auth also does not exist.

**Two facts that change what this task is.** Both read from source, not inferred:

1. **`createSessionBoundary` has no production caller.** `grep -rn 'createSessionBoundary\|onRenewalFailed'
   src --include='*.ts*'` outside test files returns **only doc comments** — `accessToken.ts:29`,
   `accessToken.ts:41`, `crossTabRenewal.ts:42`, and the definition itself. The boundary is built and
   heavily tested (`parallel401sTriggerExactlyOneRenewal.test.ts`, 546 lines;
   `siblingTabsShareOneRenewal.test.ts`, 633 lines) but **nothing in the running SPA constructs it**, so
   `onRenewalFailed` has no production implementation to extend. `mountShell.tsx` wires i18n, the shell and
   the router, and nothing else.
2. **The failed path is the only renewal path that exists.** `accessToken.renew()` always rejects, and
   `accessToken.ts:38-40` states the reason in the file: silent re-authentication "is the model the owner
   chose, and NO TASK IN `tasks.md` BUILDS IT." So T33 is not instrumenting an edge case. Today, *every*
   renewal fails, and `accessToken.ts:29-31` already names T33 as the task that routes that failure:
   "It travels to `onRenewalFailed` (`sessionBoundary.ts:70-77`), **which task 33 will route to clean
   re-auth**."

## 3. Established patterns to follow

- **Seams are declared, not faked.** `accessToken.renew()` rejects rather than resolving with a stale or
  fabricated token, precisely so a gap cannot hide behind a retry that cannot work. `codeExchange.ts` was
  the same kind of seam from T17 until T20 filled it.
- **One spelling of every identifier, exported from the module that owns it.** `SIGN_IN_PATH`,
  `HOME_PATH`, `RENEWAL_CHANNEL`, `RENEWAL_LOCK`, `RENEWAL_TIMEOUT_MS`, `SIBLING_GRACE_MS`. Literals are
  not repeated across modules.
- **Copy lives only in `i18n/catalogue.ts`** (L11), is neutral (L4), and is grouped in the order a visitor
  meets it rather than alphabetically.
- **Failure carries no reason to the visitor or across tabs.** Fixed messages, no status, no server detail.
- **In-memory, single-read, module-scoped notices** rather than `sessionStorage` — L16's carve-out is
  exhaustive and a notice is not on it, and history state is wrong because the browser persists it.
- **Doc comments carry the reason, and name the task that will consume the seam.** Several files already
  point forward at T33 by number.
- **No module-level mutable state** anywhere except `accessToken.ts`, which is scanned for and protected by
  T19's and T20's suites.

## 4. Testing conventions

Vitest 5.0.5 with jsdom 30.1.3, run as `npx -y pnpm@12.14.0 exec vitest run <path>` — bare `pnpm` is not on
this host, and **the exit code is the verdict, not the pass count**. The two existing boundary suites are
the precedent for this area: both build a fake transport and a fake `SessionTokens`, drive real middleware
through the generated client, and assert on observable call sequences rather than on internals.

Package conventions that apply to every task here: a dirty-proof for every absence claim, an empty-tree
refusal (`refuseEmpty`), an allowlist used as both upper **and** lower bound, and a per-task mutation
harness under `.ai/prompts/frontend/T33/artifacts/` that must assert its own baseline **by failing file
name**, must kill its own anchors, and must fail on BROKEN as well as SURVIVED. Tests are committed before
the harness runs, because it reverts with `git checkout`.

One pre-existing, environmental suite failure on this host:
`src/api/lintRejectsHandWrittenBackendFetch.test.ts` — this host's `oxlint@1.87.0` prints no summary line
for the assertion that parses it. Unrelated to T33, pinned by name in every harness baseline.

## 5. Known gaps and unknowns

- **Where "clean re-auth" lands, I do not know.** R22 says "transition to clean re-auth" and `design.md`
  has **no session transition table** — `grep -n 'transition' spec/frontend/design.md` finds only prose in
  other requirements. Whether that means a route change to `/app/sign-in`, a fresh
  `/oauth2/authorize` navigation, or `/app/signed-out`, is not stated in the four spec files. Phase 1 has
  to extract it or log it.
- **There is no route guard to transition *with*.** Every entry in `AUTHENTICATED_ROUTE_PATHS` is a path
  with no element (`routes.tsx:46`), and the catch-all is explicitly deferred: "T41 replaces it with the
  sign-in redirect." So no module currently decides that an unauthenticated visitor must leave a page.
- **No production wiring exists for the boundary at all** (§2, fact 1). Whether T33 is expected to create
  that wiring, or to deliver the failure-routing module that a later wiring task consumes, I do not know.
  It is the single question that most changes the size of this task.
- **Prompt-header drift, three named tests for one task.** The header lists
  `failedRenewalTriggersCleanReauthWithoutHalfSession`, `signOutPropagatesToAllTabs` and
  `reusedRefreshOrFailedRenewalTriggersCleanReauth`. Only the first is T33's. `tasks.md:67` gives
  `signOutPropagatesToAllTabs` to **task 39**, and `reusedRefreshOrFailedRenewalTriggersCleanReauth`
  belongs to **task 34, which `tasks.md:62` struck out** as removed on 2026-10-10 when Q5 resolved — yet
  `package.md:171` still maps it to R22. That is live spec drift, not just a prompt artifact.
- **`signInNotice`'s union has one member and anticipates a second.** Whether a failed renewal reuses
  `'discarded'` or adds a member is a design question (L4 forbids copy that distinguishes causes), and
  phase 2 owns it.
- **Reload ends the session by design**, so "no half-authenticated screen" has an interaction with the PWA
  and with any screen that renders before a token exists. Nothing in the four spec files addresses it.

**Phase 0 complete.** Phase 1 extracts R22, L4, L11, L14, L16 and L18 against this ground, and resolves —
or logs — where clean re-auth lands.
