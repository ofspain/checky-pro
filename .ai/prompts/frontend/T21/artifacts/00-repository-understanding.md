# frontend · T21 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 21):**
> **Access token in memory only.** The token is held in a module-scoped variable and nowhere else. Verifies
> `callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7, L3.

No design, no requirement extraction, no code. Every `file:line` below was read before it was written down.

---

## 1 · Architecture summary

A React 19 SPA under `frontend/`, served at `/app` behind a Caddy edge on `localhost:5173` that is the single
origin for the whole platform (L9). `/oauth2/*`, `/login`, `/userinfo`, `/accounts*` and friends go to
`auth:8080`; `/app*` goes to the SPA (`infra/local/edge/Caddyfile:12-28`).

- **Modules.** `src/app` (shell, router, mount), `src/auth` (the OIDC flow), `src/api` (the client boundary),
  `src/i18n`, `src/pwa` (service worker policy). There is **no state-management library** — TanStack Query and
  Zustand are named in L13 and `agents.md:10` but are **not installed**.
- **Persistence.** None of the backend kind. The only client-side storage is the `sessionStorage` carve-out of
  **L16** (`design.md:58-60`), which is exhaustive: `state`, `nonce`, `code_verifier`, the post-login return
  target, and non-sensitive mid-task form input. "This list is the entire carve-out."
- **Events/outbox.** Not applicable to the SPA. The cross-tab mechanism is a `BroadcastChannel` plus Web Locks
  (`src/api/crossTabRenewal.ts:18-19`), not an event bus.
- **Security.** Public OIDC client, PKCE, no client secret (L1). `checky-spa` is registered with
  `ClientAuthenticationMethod.NONE`. Tokens live in memory only (**L3**, `design.md:13-20`). The visitor may never
  learn which check failed (L4). Every backend call goes through a generated client (L6) — satisfied for the token
  endpoint in T20 by `contracts/api/oidc.yaml` and `createOidcClient()`.

## 2 · Existing code this task touches

**This is the unusual part of T21: almost every consumer already exists, and the thing they consume does not.**

### Already built, and waiting for exactly what T21 produces

| File | What it already declares |
|---|---|
| `src/api/sessionBoundary.ts:11-14` | `interface SessionTokens { current(): string \| null; renew(): Promise<string> }` — "The client boundary's view of the session, **supplied by the auth module**". |
| `src/api/crossTabRenewal.ts:4-6` | `interface LocalSession extends SessionTokens { adopt(token: string): void }` — "The tab's own session, **supplied by the auth module**: its silent re-auth, plus a way to take a sibling's token." |
| `src/api/crossTabRenewal.ts:43-45` | `interface SharedRenewal extends SessionTokens { close(): void }` — what `shareRenewalAcrossTabs` returns. |
| `src/api/sessionBoundary.ts:48-52` | `createSessionBoundary(tokens: SessionTokens, …)` — reads `tokens.current()` on every request (`:100`) and after a 401 (`:118`, `:127`). |
| `src/api/client.ts:20-27` | `createAuthApi(boundary, rateLimit?)` — registers the boundary's middleware on the generated client. |
| `src/api/crossTabRenewal.ts:72-75` | `shareRenewalAcrossTabs(local: LocalSession, …)` — a Web Locks leader renews, a channel carries the outcome, "a renewed token is posted to the app's own same-origin tabs, which **adopt** it; it is never stored, logged or put in a URL (L3)" (`:66-67`). |

So the token holder's **shape is already specified by its consumers**: `current()`, `renew()`, `adopt()`. T21 does
not get to invent an interface; it has to satisfy one that three built modules already depend on.

### Where the token comes from

`src/auth/codeExchange.ts:103` — `exchangeAuthorizationCode` returns `{ idToken, accessToken }`
(`:35-38`), built in T20. `TokenResponse` deliberately carries **only those two**; `expires_in`, `scope` and
`token_type` are on the wire and typed in `contracts/api/oidc.yaml` but not returned, because T20 recorded that
deciding what to do with expiry is T21's.

`src/auth/CallbackScreen.tsx:122-135` is the only caller. It currently uses the response for one thing — the nonce
check — and **drops it**:

```
exchangeAuthorizationCode({ code, state }).then((response) => {
  if (!idTokenNonceMatchesStoredValue(response.idToken).ok) discard();
}, discard);
```

`response.accessToken` is never read. That is the line T21 changes.

### The established pattern for module-scoped state, already in this package

`src/auth/signInNotice.ts` is the model, and it is a close one: a module-level `let` (`:15`), a setter (`:17-19`),
a single-read taker (`:22-26`), and a doc comment that explains **why it is not in storage** — "L16's carve-out is
exhaustive and names four items; this is not one of them. It is not in history state either, which the browser
persists across a reload" (`:5-8`). T21's holder is the same shape of thing for a far more sensitive value.

### New in T21

A module that holds the access token. Nothing else. The file does not exist yet, and neither does any production
line that reads `response.accessToken`.

## 3 · Established patterns to follow

- **Guarded storage reads, every time.** `sessionStorage` can **throw**, not merely return `null`, and **there is
  no error boundary anywhere in production source**. `codeExchange.ts:56-63` and `idTokenNonce.ts:97` both wrap
  the read and treat a denial as absence. T21 should not need storage at all — which is the point — so the
  relevant pattern is the inverse: nothing to wrap.
- **Everything crossing the network boundary is `unknown` until checked** (`codeExchange.ts:74-81`). T21's input
  comes from T20's already-validated `TokenResponse`, so the check has been done once; re-checking at the holder
  is a design question for Phase 2, not a settled pattern.
- **One opaque failure.** L4 means a visitor learns nothing about which step failed; `codeExchange.ts:91-93`
  throws one fixed message for six faults.
- **No module-level mutable state — except where it is the whole point.** T19's and T20's suites scan their
  production files for `^(let|var) ` and fail on a hit. T21's deliverable is *literally* a module-level `let`, so
  that scan cannot be copied blindly; `signInNotice.ts` shows the sanctioned form, and its justification lives in
  the doc comment.
- **Interfaces are consumed, not re-declared.** `sessionBoundary.ts:4` derives `Middleware` from the generated
  client rather than importing `openapi-fetch`, so the frontend needs no direct dependency on it. T21 should
  implement `LocalSession` from `crossTabRenewal.ts`, not define a parallel shape.
- **Error handling.** RFC 9457 `problem+json` is the backend convention (`agents.md:66-67`), but the token
  endpoint does not use it and T20 normalizes every failure to one opaque rejection. Nothing in T21 reads an
  error body.
- **Configuration.** None. `src/auth` reads no `import.meta.env` and must keep reading none (L10).

## 4 · Testing conventions

The prompt's template mentions JUnit, a fixed `Clock`, Testcontainers and ArchUnit. **None of those exist here** —
this is the frontend package, and the conventions are:

- **Vitest 5.0.5 + jsdom 30.1.3**, run with `npx -y pnpm@12.14.0 -C frontend exec vitest run` (bare `pnpm` is not
  on this host). No integration-container layer; the whole platform in `infra/local/` is the manual check.
- **The run's exit code is the verdict, not the pass counts.** T19 found an unhandled rejection reported *below* a
  line saying every test passed, which fails the run with exit 1.
- **Two layers where a screen is involved**: the decision exercised directly for a shape matrix, and through the
  real route table for the outcome. T19 adopted this after a test double resolved the wrong shape and the nonce
  assertions passed having tested nothing (`callbackValidatesNonce.test.tsx:24-29`).
- **Every block carries a control** proving its assertion is reached and not merely satisfied. The recurring
  defect in this package — roughly a dozen appearances — is an assertion compared against the constant it is
  testing.
- **Source scans with self-proving violations**: each forbidden pattern is also asserted to match a synthetic
  violation, so a pattern that matches nothing cannot pass.
- **Mutation runs** with a scratchpad harness; T19 killed 19 of 19, T20 24 of 24. The harness reverts with
  `git checkout --`, so **tests must be committed before it runs**.
- **The one known environmental failure**: `lintRejectsHandWrittenBackendFetch` AC4 at `:113` fails because this
  host's `oxlint@1.87.0` prints no summary line. Suite is currently **892 passed / 1 failed of 893**.

The named test already exists: `src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx`, **69 cases**,
written in T20 for the verifier half. T21 adds the in-memory half to the same file. `package.md:111` maps it to
**R7**. T20's own cases already assert that no token value or segment reaches either storage, the DOM, the tab
title, the URL or the console (`T:756`), so T21 inherits a partial guard and must not duplicate it.

## 5 · Known gaps and unknowns

**G1 · There is still no composition root, and T21 is the task that will feel it.** Nothing in production calls
`createSessionBoundary`, `createAuthApi` or `shareRenewalAcrossTabs` — I grepped all of `frontend/src` excluding
tests and found only their definitions and doc comments. `mountShell.tsx:12-30` builds i18n, the shell and the
router, and stops. Their comments describe wiring that does not exist: "Production wiring creates exactly one and
passes it to every client" (`sessionBoundary.ts:20-22`), "Production wiring passes the tab's rate-limit notice"
(`client.ts:17`). So T21 can build the holder, but whether T21 also *wires* it is a scope question, and no task in
`tasks.md` is visibly the wiring task.

**G2 · No task implements the renewal mechanism, yet `LocalSession.renew()` requires one.** Task 5 built
single-flight 401 handling, task 6 built cross-tab sharing, task 33 handles a *failed* renewal — all against an
injected `SessionTokens`. The thing that actually performs silent re-authentication (re-opening `/oauth2/authorize`
on the SAS session cookie in a hidden iframe or background navigation, per `agents.md:25-34` and R21) is assigned
to **no task I can find** in `tasks.md` 1-34. I grepped for "renew" and that is the whole set. This bears directly
on T21: if the holder must implement `LocalSession`, it must supply `renew()`, which is the unassigned work.

**G3 · `expires_in` is not carried out of the exchange.** T20 deliberately returned only `{ idToken, accessToken }`
and recorded that task 21 owns expiry. Whether the holder needs to know when the token dies — nothing in the task
statement says it does — is a Phase 1/2 question, but the field is currently dropped at `codeExchange.ts:133` and
would have to be added back to reach the holder.

**G4 · "and nowhere else" has no stated enumeration.** The task says the token is held in a module-scoped variable
**and nowhere else**. L3 names storage, logs and analytics; task 22 separately covers local storage, session
storage, IndexedDB, cookies, URLs and logs. What "nowhere else" adds beyond task 22 — React state, a prop, a
closure in another module, a `window` property, an error message, a service-worker cache — is not enumerated
anywhere I read. **I do not know** where the line between T21 and T22 is meant to fall; Phase 1 must derive it
from R7, L3 and task 22's wording, and Phase 2 must state it explicitly.

**G5 · Whether the holder is one token or a session.** `LocalSession.adopt(token)` takes a bare string, and
`SessionTokens.current()` returns `string | null` — so the consumers model a single access token with no identity,
no expiry and no ID token. The ID token T19 validates is currently dropped after the nonce check. Whether T21's
holder keeps it is unstated. **I do not know.**

**No speculation offered on any of the five.** They are Phase 1's and Phase 2's to resolve, and G1, G2 and G4 look
like the ones capable of changing T21's scope.

---

## Next — Phase 1 — specification extraction.
