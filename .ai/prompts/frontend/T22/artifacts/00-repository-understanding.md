# frontend · T22 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

Task statement: *"No token is written to local storage, session storage, IndexedDB, a cookie, a URL, or a log.
Verifies `tokensAreNeverWrittenToAnyStorage`. Cites R7, L3."*

T22 is a **verification task**. Tasks 15 through 21 built the flow that could leak a token; this one proves the
leak does not exist, package-wide, and leaves behind an instrument that keeps proving it. Nothing in the task
statement asks for a feature.

---

## 1 · Architecture summary

A React 19 SPA in `frontend/`, served under `/app` behind a Caddy edge that is the single origin (L9). The
prompt's backend vocabulary maps as follows, and where it does not map I say so rather than inventing an analogue:

| Prompt's term | This service |
|---|---|
| Modules | `src/api` (client boundary, session renewal), `src/app` (shell, routes, mount), `src/auth` (the whole OIDC flow), `src/i18n` (catalogue), `src/pwa` (service worker, policies) |
| Persistence | **`sessionStorage` only, and only three keys** (`authorizeRequest.ts:81-84`), under L16's carve-out. No database, no IndexedDB, no `localStorage`, no cookie written by the SPA. The Cache Storage API is treated as client-side storage by `apiRequestPolicy.ts:5-7`, which is the correct reading for this task. |
| Events / outbox | None. The nearest thing is a `BroadcastChannel` carrying renewal outcomes between tabs (`crossTabRenewal.ts:116`) — in flight, not at rest. |
| Security | No token at rest anywhere. The access token lives in one module-scoped variable (`accessToken.ts:24`, T21). The SAS session cookie is `httpOnly` and outside SPA control. |

## 2 · Existing code this task touches

T22 reads nearly the whole package and is expected to change almost none of it.

### The only storage writer in the service

`src/auth/authorizeRequest.ts:81-84` — three `setItem` calls, `state`, `nonce` and `code_verifier`, written
together once the PKCE challenge is in hand. That is the complete set of writes in production source. Measured:

```
grep -rn 'sessionStorage|localStorage|indexedDB|openDatabase|document.cookie|caches.|navigator.storage' src
  → one writer (authorizeRequest.ts:81), four guarded READS
    (CallbackScreen.tsx:105, codeExchange.ts:59, idTokenNonce.ts:97, and the keys module)
grep -rn 'removeItem|\.clear\(\)' src (non-test)   → no matches
grep -rn 'console\.' src (non-test)                → no matches
```

Three facts follow, and each matters to how T22 is scoped:

1. **No token is written to any storage today.** The three items that *are* written are the three L16 permits, and
   none of them is a token: `state` and `nonce` are correlation values, `code_verifier` is a PKCE secret that
   never leaves the tab. So T22's expected finding is "no leak", and the deliverable is the instrument.
2. **Nothing deletes the three items.** L16 says they are "single-use and deleted at callback" and no code path
   deletes them. **This is task 23's**, which owns `stateSurvivesRedirectAndIsConsumedOnce` (`tasks.md:35`) — not
   T22's. T22 must not quietly fix it; recording it is the right move.
3. **The "or a log" half is currently vacuous.** Production source makes no `console` call at all. T22's job is to
   pin that, not to discover it.

### Where a token exists at all

| Module | What it holds | Why T22 cares |
|---|---|---|
| `auth/accessToken.ts:24` | the access token, one `let` | the thing that must not reach storage |
| `auth/codeExchange.ts:35-38` | both tokens, transiently, as the exchange's return value | `id_token` is read for its nonce and dropped |
| `auth/idTokenNonce.ts` | decodes the ID token's payload | a decoded claim set is a token segment |
| `auth/CallbackScreen.tsx:122-152` | the exchange's response inside one `.then` | the one place a token passes through React |
| `api/sessionBoundary.ts:88-108` | the bearer header, a cloned request, a `Map` entry | transient by construction, deleted at `:114`/`:134` |
| `api/crossTabRenewal.ts:116` | posts a renewed token to sibling tabs | a channel message, not storage |

### Already-built instruments T22 should reuse rather than duplicate

- `pwa/apiRequestPolicy.ts` + `serviceWorkerNeverCachesApiResponses.test.ts` (354 lines) — the Cache Storage half.
  `/oauth2/token` is already named there as NetworkOnly, which T20 checked rather than re-asserting.
- `app/bundleContainsNoSecretEnvKeys.test.ts` — the house precedent for a **package-wide sweep**: it builds the
  real bundle, scans it, and feeds synthetic files to the *same* scanner as a negative control, because "a
  verification task whose scanner has never been shown to catch a leak has verified nothing" (its own header).
  T22 is the same shape of task and should follow that shape.
- The partial sweeps T19-T21 already wrote: `storageDump()` and the storage/DOM/title/URL case in
  `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx`. T22 is the package-wide version of these, so the
  risk is duplication rather than absence.

### New in T22

One file, `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.ts(x)` — the named test from
`package.md:157`. No production file is expected to change. I do not know yet whether it belongs in `src/auth/`
or `src/app/`; the sweep is package-wide but the subject is the auth flow, and Phase 1 should settle it.

## 3 · Established patterns to follow

- **A sweep must prove its own instrument.** The house rule, written into T01's scan and repeated in T20 and T21:
  every scan asserts a synthetic violation that the same scan catches, and a scan over an empty tree must fail
  rather than pass.
- **Scan code, not comments.** Measured twice now: a whole-file scan reads the file's own explanation as the
  defect it warns about (`charset` in T20, `sessionStorage` in T21, since `accessToken.ts:12` names it in order to
  forbid it). Either scan one line or strip comments and assert the body survived the strip.
- **Spy on the prototype, not a stub.** The existing "nothing is written" cases spy on
  `Storage.prototype.setItem` / `removeItem` / `clear` so a write through any reference is caught.
- **Guarded reads.** Every `sessionStorage` read in the package is wrapped, because *touching* storage can throw,
  not merely return `null` (blocked site data). A T22 sweep must survive a throwing store.
- **L4 neutrality.** No test may require copy that distinguishes one failure from another.
- **Generated client only.** All backend I/O goes through `@themistra/api-client` (L6), enforced by `oxlint` and
  `lintRejectsHandWrittenBackendFetch.test.ts`.
- **No new dependency** without a decision (L13). A sweep needs none.

## 4 · Testing conventions

- **Vitest 5.0.5 + jsdom 30.1.3**, run as `npx -y pnpm@12.14.0 -C frontend exec vitest run` — bare `pnpm` is not
  on this host.
- **Check the exit code, not the counts.** An unhandled rejection is printed *below* the all-passed line.
- **One known environmental failure**, and only one: `lintRejectsHandWrittenBackendFetch.test.ts:118` (AC4), this
  host's `oxlint@1.87.0` printing no summary line. Suite today: **931 passed / 1 failed of 932**.
- **Mutation testing by hand** after the tests are committed, since the harness reverts with `git checkout`.
- **Node APIs are available in tests** (`/// <reference types="node" />`), which is what makes a source-tree sweep
  possible: `readFile`, `readdir`, and `execFile` for a real build.
- jsdom facts that have already cost time: it provides `BroadcastChannel` but **not** `navigator.locks`, and
  **not** `caches`, `indexedDB` or a real cookie jar beyond `document.cookie`.
- No Testcontainers, no Flyway, no ArchUnit, no fixed `Clock` in this service. The architectural-rule equivalent
  is a source scan inside an ordinary test file.

## 5 · Known gaps and unknowns

1. **What counts as "a token" is not defined by the spec.** L3 says "access tokens". The task statement says "no
   token". The ID token, the authorization `code`, and the PKCE `code_verifier` are all credentials of some kind,
   and `code_verifier` is explicitly *permitted* in sessionStorage by L16. The scope therefore has to be decided,
   not read off. Phase 1 must produce the list and Phase 2 must freeze it.
2. **The authorization `code` stays in the URL after a successful callback.** `CallbackScreen` navigates away with
   `replace: true` on failure, but on success it holds on `/app/callback?code=…&state=…` until the task that owns
   the post-login destination (24) moves the visitor. Whether a one-time authorization code counts as "a token in
   a URL" is part of (1). I do not know whose task a scrub would be if it counts.
3. **How far "a log" reaches.** There is no `console` call in production source and no analytics dependency, so
   today the half is provable by scan. Whether the obligation extends to a thrown `Error`'s message, a React
   error overlay, or `document.title` is a scoping question for Phase 1. T21 already forbids a token in a thrown
   message inside `accessToken.ts` only.
4. **Where the boundary with tasks 23, 26, 69 and 116 falls.** Task 23 owns single-use deletion, task 26 owns
   "sessionStorage holds only the L16 items", 69 owns recovery codes, 116 owns receipts. T22 is the *token* sweep
   across the six named surfaces. Without a frozen line, T22 will either duplicate task 26 or leave a hole.
5. **Whether T22 builds the real bundle.** `bundleContainsNoSecretEnvKeys` does, and a built bundle is the only
   place a leak introduced by a *dependency* would show. It is also the slowest test in the suite by far. Phase 2
   should decide; I do not know the owner's appetite for a second full build in the suite.
6. **The Cache Storage API is unreachable in jsdom.** `caches` is undefined, so a T22 case cannot observe a real
   cache. The existing service-worker tests work against the policy module instead, which is the available
   instrument.
7. **Still unassigned package-wide, carried from T21 and not T22's to fix:** nothing wires the composition root
   (`mountShell.tsx` builds no session boundary), and silent re-authentication has no task. Both are recorded in
   T21's Phase 13 artifact.

---

## Next — Phase 1 — specification extraction.
