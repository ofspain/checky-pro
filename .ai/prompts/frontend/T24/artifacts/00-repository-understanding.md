# frontend · T24 · Phase 0 — Repository Understanding

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

Read the repository and `spec/frontend/`'s five files. No code written. The single most important thing found:
**nothing in the repository captures, stores, reads or honours a return target today — not one line** — and the
task that would naturally capture one (**T41**, the unauthenticated redirect) is seventeen tasks away. That shapes
everything Phase 1 and Phase 2 have to decide, and it is stated first because it is easy to miss while reading a
callback screen that already works.

---

## 1 · Architecture summary

A React 19 + TypeScript SPA (`frontend/`), Vite 8, built as a **public OIDC client** of the auth service (Spring
Authorization Server). Not a service with persistence: there is **no JPA, no Flyway, no outbox, no Kafka, no
resource-server security** in this package. The analogues the phase prompt asks about map as follows.

| The prompt's term | What plays that role here |
|---|---|
| persistence | `sessionStorage`, and only for L16's exhaustive carve-out: `state`, `nonce`, `code_verifier`, **the post-login return target**, and R29's form input. `localStorage` holds public preferences only. Tokens live in a module variable (L3) |
| events | `BroadcastChannel` for cross-tab renewal and sign-out (L18, `api/crossTabRenewal.ts`) |
| security | no server-side security to configure; the SPA's security *is* the OIDC flow — PKCE, `state`, `nonce`, exact-match redirect URIs, **and the return-target allowlist this task owns** (L1) |
| error handling | one neutral outcome per failure class; L4 forbids copy that distinguishes causes; catalogue-only strings (L11) |
| configuration | only the public keys in `design.md` §4c are baked in (L10); `bundleContainsNoSecretEnvKeys` enforces it |

**Module map** (`frontend/src/`): `app/` the shell, route table and mount (`routes.tsx`, `AppShell.tsx`,
`mountShell.tsx`); `auth/` the OIDC client — `SignInScreen`, `authorizeRequest`, `pkce`, `CallbackScreen`,
`codeExchange`, `idTokenNonce`, `accessToken`, `signInNotice`; `api/` the generated client wrapper, the 401/429
boundary and cross-tab renewal; `i18n/` the catalogue.

## 2 · Existing code this task touches

### Already exists

| File | What it is, and what T24 has to do with it |
|---|---|
| `app/routes.tsx` | The single source of the route set. `APP_BASENAME = '/app'`, `SIGN_IN_PATH`, `CALLBACK_PATH`, `PUBLIC_ROUTE_PATHS` (9) and `AUTHENTICATED_ROUTE_PATHS` (11, including **`home`**). Screens are attached by their own tasks; most paths have no element yet. **This is where an allowlist would be derived from rather than hand-written** — the same way `authorizeRequest.redirectUri()` derives the redirect URI from `APP_BASENAME` rather than configuring it |
| `auth/CallbackScreen.tsx` | The successful path holds the token (`:153`), consumes the three L16 items (`:165`), **and then navigates nowhere**. Only discards navigate (`:82`, to `/app/sign-in`). T23's Phase 7/12 artifacts record this as T24's: the post-login destination |
| `auth/authorizeRequest.ts` | Owns `AUTHORIZE_STORAGE_KEYS` (`:35-39`) and, since T23, `consumeAuthorizeState()` (`:120-128`), which removes **every value of that constant**. A return-target key added to that constant would be deleted at callback; a test pin added in T23 fails with a message saying so |
| `auth/signInNotice.ts` | The closest existing pattern to what a return target needs: a module-scoped, **single-read** value with a `set`/`take` pair, documented as deliberately *not* in session storage because L16's carve-out did not name it. L16 **does** name the return target, which is the difference Phase 1 must reason about |
| `app/mountShell.tsx`, `AppShell.tsx` | The shell and the browser router. `AppShell` renders nothing but children |
| `auth/accessToken.ts` | The in-memory holder. Whether the SPA is "signed in" is `accessToken.current() !== null` — the only signal a guard could use today |

### New in this task (nothing of this exists)

- **Anything that captures a return target.** No module writes one; no key for it exists; `AUTHORIZE_STORAGE_KEYS`
  has exactly three entries.
- **Anything that validates one against an allowlist.** The word "allowlist" appears nowhere in `frontend/src`.
- **Anything that navigates after a successful callback.**
- **The named test** `returnTargetOutsideAllowlistFallsBackToHome` — no such file.
- **A screen at `/app/home`.** `AUTHENTICATED_ROUTE_PATHS` registers the path with **no element**, so navigating
  there today renders an empty route. T24 must decide whether "honoured" means *navigated to* (observable in the
  router's location) or *rendered*, because only the first is possible for most targets.

## 3 · Established patterns to follow

- **Derive, never duplicate.** `redirectUri()` is built from `APP_BASENAME` rather than configured;
  `SIGN_IN_PATH` and `CALLBACK_PATH` are exported so no screen repeats a literal. An allowlist of route paths
  already exists in two arrays — a second, hand-written list would be the thing T22's source scan and this
  package's whole style forbid.
- **One outcome per failure class, in one closure.** `CallbackScreen`'s five failure paths share a single
  `discard()` so they cannot drift apart (L4). A fallback-to-home is the same shape of decision.
- **Guard every storage touch.** Every read in `auth/` wraps `sessionStorage` in `try`/`catch`, because touching
  it *throws* in a context with site data blocked — there is no error boundary, so an uncaught throw blanks the
  page. T23's deletion follows the same rule per key.
- **Keep readers pure; put side effects in the screen.** `codeExchange` and `idTokenNonce` take no action on
  storage, and three tests assert it. T23 put the deletion in the screen's effect for exactly this reason.
- **Published constants, documented contracts.** Exported helpers carry a contract paragraph (what it does, what
  it never does, why it is not a general-purpose helper) — see `consumeAuthorizeState`.
- **Catalogue-only strings** (L11), and no new copy unless the task owns a screen.

## 4 · Testing conventions

Not JUnit/Testcontainers/ArchUnit — that is the backend's stack. Here:

- **Vitest 5.0.5 + jsdom 30.1.3**, one file per named test from `package.md` §8, named exactly as the spec names
  it. Run with `npx -y pnpm@12.14.0 exec vitest run` (bare `pnpm` is not on this host).
- **Read the exit code, never the pass counts** — an unhandled rejection prints *below* the all-passed line.
- **Drive the real route table** through `createMemoryRouter` with only `fetch` stubbed; import the route graph
  **dynamically** after any `vi.doMock`, because `vi.resetModules()` runs between cases and a statically imported
  module is a *different instance* from the one the screens hold.
- **`createMemoryRouter` writes neither `window.location` nor `history`** — assert `router.state.location`. This
  matters more for T24 than for any task so far, since "honoured" is a claim about where the visitor ended up.
- **Install `Storage.prototype` spies before the code under test runs**, or they report zero.
- **Controls against vacuity.** The house style requires each absence assertion to carry a case that proves the
  instrument can report, and each "it ended up at X" to prove it could have ended elsewhere.
- **A mutation harness per task**, run only after the tests are committed (it reverts with `git checkout`).
- One known environmental failure is expected in the suite: `lintRejectsHandWrittenBackendFetch.test.ts:118`,
  where this host's `oxlint@1.87.0` prints no summary line.

## 5 · Known gaps and unknowns

1. **Nothing captures a return target, and the task that would is T41.** T24 is told a target "on the allowlist is
   honoured", but the producer does not exist. Phase 1 must decide whether T24 builds the whole mechanism (capture
   + storage + allowlist + navigate) and T41 later merely calls it, or whether T24 builds only the honouring half
   and the test supplies the stored value itself. **I do not know which the author intends**; the task list is
   silent, and no artifact in T01–T23 assigns the capture.
2. **T24 and T25 share one named test.** `tasks.md:36` and `:37` both say *Verifies
   `returnTargetOutsideAllowlistFallsBackToHome`*, and `package.md:158` maps that single name to R7. So one file
   must serve both halves, and T24 creates it knowing T25 will extend it. Phase 1 should state which cases belong
   to which task so T25 does not find its work already done — or already contradicted.
3. **What the allowlist actually is, is not written down anywhere.** `agents.md:21` and L1 say a target outside it
   falls back to the home route; neither names its contents. The obvious candidate is
   `AUTHENTICATED_ROUTE_PATHS`, but that is an inference. **I do not know** whether the author intends the
   authenticated route set, a narrower list, or an explicit new constant.
4. **Where the target is stored is a genuine fork.** L16 *names* "the post-login return target" as a permitted
   `sessionStorage` item — so storage is sanctioned. But T23's `consumeAuthorizeState()` deletes **every** value of
   `AUTHORIZE_STORAGE_KEYS`, so putting it in that constant means it is deleted at callback, and the read must then
   happen before the deletion. T23 recorded this as an explicit handoff (its Phase 11 Gap 5 and T23-D16).
5. **`/app/home` has no screen.** "Honoured" can only be verified as a navigation, not as a rendered page, for
   every authenticated path. Phase 1 must say so rather than let a test assert something it cannot see.
6. **`oidcRoundTripEndToEnd`** is in this task's named-test list via L1 and is a Playwright-level test
   (`package.md:254`). No Playwright suite exists in the repository. **I do not know** which task owns it; it is
   not T24's deliverable, and the long-standing "no task owns a real-browser check" gap is the same hole.
7. **Carried forward from T23, unchanged:** the composition root (`mountShell.tsx`) is wired by nothing; silent
   re-authentication has no task; CI enforces neither the suite total nor the identity of the `oxlint` failure.

## Next — Phase 1 — specification extraction.
