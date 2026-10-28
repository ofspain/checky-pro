# frontend · T23 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

Task statement: *"`state`, `nonce`, and `code_verifier` survive the full-page redirect in session storage and are
deleted at callback. Verifies `stateSurvivesRedirectAndIsConsumedOnce`. Cites R8, L16."*

**T23 is the first task in a while that changes production code**, and the first whose change is a *deletion*.
Six assertions in four existing test files were written to fail the moment it lands, three of them with a comment
saying so by name. That is the most important fact in this artifact.

---

## 1 · Architecture summary

React 19 SPA under `/app` behind a Caddy edge on one origin (L9). Modules: `src/api` (client boundary, session
renewal), `src/app` (shell, routes, mount), `src/auth` (the whole OIDC flow), `src/i18n`, `src/pwa`.

| Prompt's term | This service |
|---|---|
| Persistence | **`sessionStorage`, three keys, one writer** — `authorizeRequest.ts:81-84`. T22 measured this: one storage writer in 23 production modules. No `localStorage`, no IndexedDB, no cookie the SPA writes, and **no `removeItem` or `clear` anywhere** |
| Events / outbox | none; a `BroadcastChannel` carries renewal outcomes between tabs |
| Security | no token at rest; the access token lives in one module-scoped `let` (`accessToken.ts:24`, T21); the SAS session cookie is `httpOnly` and outside SPA control |

## 2 · Existing code this task touches

### The write side, already built (task 15)

`auth/authorizeRequest.ts:71-96` — `buildAuthorizeRequest()` generates `state`, `nonce` and a PKCE pair, checks
the verifier against RFC 7636, then writes the three items **together** once the challenge is in hand, keyed by
`AUTHORIZE_STORAGE_KEYS` (`state`, `nonce`, `codeVerifier`). Its comment states the ordering reason: storing a
state before the derivation could fail would leave a state with no verifier behind it.

### The read side — three readers, in three files, consumed at three different moments

This is the shape that decides where a deletion can go:

| Reader | Reads | When |
|---|---|---|
| `auth/CallbackScreen.tsx:105` | `state` | first, before anything else is trusted |
| `auth/codeExchange.ts:59` | `code_verifier` | inside the exchange, **after** the state check |
| `auth/idTokenNonce.ts:97` | `nonce` | **after** the exchange resolves, on the ID token |

All three reads are wrapped, because *touching* `sessionStorage` can throw rather than return `null` (blocked
site data). So the three items are not consumed at one instant — they are consumed across an `await`, and a
deletion placed at the state check would strand the exchange with no verifier. **I do not know yet** whether the
frozen design will delete all three in one place after the nonce check, or have each reader consume its own; the
trade-offs belong to Phase 1 and 2.

### The screen, which already names this task

`CallbackScreen.tsx:23-27` says, in production source:

> *"It writes nothing and deletes nothing: the `code_verifier` is task 20's, and deleting all three is task 23's.
> A discarded attempt leaves a verifier whose challenge was never used, which T15's next request overwrites."*

The latch at `:38-50` matters to T23 more than to any previous task: `handled.current` is set **before** the
exchange starts, so one mount handles one callback. Under StrictMode the effect runs twice, and a deletion that
happened on the first pass would leave the second pass reading an empty store — the latch is what stops that from
being visible, and T23 must keep it that way.

### The position earlier tasks already took, and where it is written down

Task 18 and task 19 did not leave this open. **A discarded callback leaves all three items in place**, asserted
with the reason:

- `callbackWithMismatchedStateIsDiscarded.test.tsx:405-408` — *"Deleting them is task 23's, by name. Pinned here
  so task 23 inherits a stated position rather than a surprise, and so a discarded callback is distinguishable
  from one that arrived with no attempt at all."*
- `callbackValidatesNonce.test.tsx:571-575` — *"Survival, not just restraint. Deleting the three is task 23's,
  and task 18 left a case that fails by design the moment task 23 lands."*

The security reason behind that position is not stated there but is worth naming now: the callback route is
public, so anyone who can get a signed-in visitor to open `/app/callback?state=anything` could **delete that
tab's pending sign-in state** if a discard deleted. That is a denial of the sign-in, which is the mirror of the
problem T21 weighed when it chose to clear the token on discard. The two cases pull in opposite directions and
the difference is what is being protected.

### The blast radius, measured

| Assertion | File:line | Breaks when T23 deletes on… |
|---|---|---|
| `expect(removeItem).not.toHaveBeenCalled()` + three survival assertions | `callbackValidatesNonce.test.tsx:563-575` | a deletion inside `idTokenNonce.ts` (a unit test of the nonce check) |
| `expect(removeItem).not.toHaveBeenCalled()` | `callbackWithMismatchedStateIsDiscarded.test.tsx:413-419` | a callback with **no state** — the arrival is `/callback?code=abc` |
| store-key equality + three survival assertions | `callbackWithMismatchedStateIsDiscarded.test.tsx:1008-1011` | a **mismatched** state |
| `expect(removeItem).not.toHaveBeenCalled()` | `callbackWithMismatchedStateIsDiscarded.test.tsx:1014-1025` | a **mismatched** state |
| `expect(removeItem).not.toHaveBeenCalled()` + `clear` | `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:678-689` | the success path (T19/T20/T21's own) |
| `expectThreeAuthorizeWrites` — no `removeItem`, no `clear`, used by **11 cases** | `tokensAreNeverWrittenToAnyStorage.test.tsx:341` | any deletion in any swept path |
| whole-store equality: sessionStorage holds exactly the three items **after a successful callback** | `tokensAreNeverWrittenToAnyStorage.test.tsx:545` | the success path |
| `STORAGE_ALLOWLIST` permits `removeItem` **nowhere** | `tokensAreNeverWrittenToAnyStorage.test.tsx:345-357` | any deletion at all, with a failure message naming the file and telling the author to add a row |

Two more `removeItem` assertions exist and are **not** affected, because they cover the authorize side with no
callback: `authorizeRequestsExactlyTheDeclaredScopes.test.ts:391` and
`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:545-558`.

T22's own Phase 13 artifact records this handoff, so the edits are planned work rather than collateral: *"it is
the expected next entrant in `STORAGE_ALLOWLIST`, and it will also have to change the AC2 whole-store equality."*

### New in T23

`frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.ts(x)` — the named test (`package.md:159` → R8).
Nothing else is obviously new; the deletion itself is a change to existing files.

## 3 · Established patterns to follow

- **One key spelling.** `AUTHORIZE_STORAGE_KEYS` is imported everywhere; no file re-spells a key as a literal,
  and `tokensAreNeverWrittenToAnyStorage` and the L6 scans both check for hand-spelled keys.
- **Guarded storage access.** Every read is wrapped because touching storage can throw. **A deletion can throw
  for the same reason** and nothing in the package yet deletes, so there is no local precedent — the read
  guards in `codeExchange.ts:50-62` and `idTokenNonce.ts:85-99` are the pattern to copy.
- **The one-outcome rule (L4).** A missing `state`, a mismatched `state`, a refused exchange and a bad `nonce`
  share one `discard` closure so they cannot drift apart. Anything T23 adds on a failure path goes in that
  closure or it will drift.
- **Single-invocation latch.** `handled.current` is set before the first await; StrictMode cases exist in three
  files to hold it.
- **Tests that pin a successor's inheritance.** The pattern this task is on the receiving end of: when a task
  leaves work for a named later task, it asserts the current behaviour *and says in the case which task will have
  to change it*.

## 4 · Testing conventions

- **Vitest 5.0.5 + jsdom 30.1.3**, `npx -y pnpm@12.14.0 -C frontend exec vitest run`; bare `pnpm` is not on this
  host. **Check the exit code, not the counts.**
- Suite today: **978 passed / 1 failed of 979**, the one failure environmental
  (`lintRejectsHandWrittenBackendFetch.test.ts:118`, this host's `oxlint` printing no summary line).
- **Hand-rolled mutation testing** after the tests are committed, since the harness reverts with `git checkout`.
- Facts that have already cost time in this package: `createMemoryRouter` writes neither `window.location` nor
  `history`; jsdom has `BroadcastChannel` but no `navigator.locks`, no `indexedDB`, no `caches`; a prototype spy
  must be installed before the code under test runs; a comment-stripping scan collapses lines.
- No Testcontainers, Flyway, ArchUnit or fixed `Clock` here; the architectural-rule equivalent is a source scan
  inside an ordinary test file.

## 5 · Known gaps and unknowns

1. **"Survive the full-page redirect" cannot be reproduced in jsdom.** A real redirect to SAS and back is a
   document unload and reload; `createMemoryRouter` never even touches `window.location`. What *can* be shown is
   that the three items are written by one module and read by another after a fresh mount with no shared
   variable between them — an honest approximation whose limits must be stated, not implied. **I do not know**
   whether a real-browser check is expected; T22 recorded that no task in `tasks.md` 1-39 owns one.
2. **Where the deletion goes is genuinely open**, and the three readers consume across an `await`, so a single
   deletion point is only possible after the nonce check. Phase 1 must lay out the options; Phase 2 freezes one.
3. **What a *failed* consumption should do is the sharp question.** Earlier tasks pinned that a *discard* leaves
   the three in place, with a security reason (a public callback route must not let anyone delete a tab's pending
   state). But a refused exchange is also a discard, and "deleted at callback" in the task statement does not
   distinguish. Both readings are defensible and they differ in what a replay can do.
4. **Whether `state` should be deleted before or after the exchange.** Deleting it early makes a replayed
   callback URL fail on the second use; deleting it late keeps the whole set consistent. Nothing in R8 or L16
   decides it.
5. **The fourth L16 item — the post-login return target — does not exist yet** (task 24's), so "deleted at
   callback" today covers three items, not four. A deletion written as "remove these three keys" will need
   revisiting; one written as "remove everything this flow stored" would pre-empt task 24.
6. **No precedent for a throwing delete.** Every read in the package is guarded; the first `removeItem` needs the
   same treatment and nothing shows what the failure should mean.
7. **Carried, and not T23's:** nothing wires the composition root; silent re-authentication has no task; no task
   owns a real-browser end-to-end check.

---

## Next — Phase 1 — specification extraction.
