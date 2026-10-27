# frontend · T21 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

This brief is the **only** specification the implementation and review phases use.

---

## Task

**T21 — Access token in memory only.** The token is held in a module-scoped variable and nowhere else. Verifies
`callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7, L3.

## Purpose

T20 made the exchange real: a callback that survives both halves of R8 now comes back with an access token. That
token is currently **dropped on the floor** — `CallbackScreen.tsx:130` reads `response.idToken` for the nonce check
and never touches `response.accessToken`. T21 gives it a home, and the choice of home is the requirement: in memory,
in one place, for the life of the tab, and nowhere a later reader could find it. The negative half is the security
property. A token in `sessionStorage` survives a tab crash and is readable by any script on the origin; a token in
a URL reaches logs and referrers; a token in a second copy is a second thing to clear at sign-out. L3 forbids all
three, and L16's carve-out is exhaustive and does not name a token.

The holder's **interface is not T21's to invent**. `SessionTokens` (`sessionBoundary.ts:11-14`) and `LocalSession`
(`crossTabRenewal.ts:4-6`) were written in tasks 5 and 6, which say the session is "supplied by the auth module".
T21 is that supply.

## Scope

**In**

- A module in `src/auth` holding one access token in one module-scoped variable.
- Reading it (`current()`), setting it from a successful exchange, and taking a sibling tab's (`adopt()`).
- Satisfying `SessionTokens` and `LocalSession` so the built boundary and cross-tab modules accept it unchanged.
- The call site: `CallbackScreen.tsx` retains the token after — and only after — the nonce check passes.
- The named test's in-memory half, added to the file T20 created.

**Out**

- **The renewal mechanism.** Silent re-authentication via `/oauth2/authorize` is implemented by no task in
  `tasks.md` 1-34; see Open Questions.
- **The systematic no-storage sweep** — task 22 and `tokensAreNeverWrittenToAnyStorage`.
- **Deleting `state`, `nonce`, `code_verifier`** — task 23. All three survive T21.
- **The post-login return target and its allowlist** — tasks 24, 25.
- **Clean re-auth on failed renewal** — task 33.
- **Sign-out and cross-tab sign-out propagation** — R22's second half, a later task.
- Any change to `sessionBoundary.ts`, `crossTabRenewal.ts` or `client.ts`, which are built and tested.
- Any change to T19's nonce decision or R8's ordering.

## Business Rules

- **R7** (`requirements.md:23-24`) — ...**and SHALL hold the access token in memory only (L3)**. The first two
  clauses are built; this third clause is the task.
- **R8** (`requirements.md:25-26`) — a missing or mismatched `state` discards and completes no exchange. A token
  never obtained must never be held.
- **R21** (`requirements.md:68-71`) — one renewal per tab, shared across parallel 401s and sibling tabs. Built
  against an injected session; T21 supplies it, so R21 fixes the holder's shape without being its deliverable.
- **R22** (`requirements.md:72-75`) — no half-authenticated screen. "Holding nothing" must be the default state,
  not an error state.

## Locked Decisions

- **L3** (`design.md:13-20`) — the decision this task implements. Never storage, logs or analytics. There is no
  refresh token for this client, measured, so there is nothing durable to hold even in principle.
- **L16** (`design.md:58-60`) — the sessionStorage carve-out is **the entire carve-out**, and a token is not on it.
- **L18** (`design.md:63-64`) — single-flight renewal and cross-tab coherence; `adopt()` exists for this.
- **L4** (`design.md:21-26`) — the visitor learns nothing about which failure occurred; the holder adds no copy and
  no new way to tell failures apart.
- **L10** (`design.md:46`) — no secret, no non-public env key. T21 reads no configuration.
- **L11** (`design.md:47-48`) — catalogue-only strings; T21 adds none.
- **L13** (`design.md:51-52`) — confirmed stack, no new runtime dependency. TanStack Query and Zustand are named
  in L13 but **not installed**: "module-scoped variable" may not become a store.

## Dependencies

- `src/api/sessionBoundary.ts:11-14` — `SessionTokens { current(): string | null; renew(): Promise<string> }`;
  `current()` is called at `:100`, `:118` and `:127`.
- `src/api/crossTabRenewal.ts:4-6` — `LocalSession extends SessionTokens { adopt(token: string): void }`;
  `:72-75` `shareRenewalAcrossTabs`, whose contract says a renewed token "is never stored, logged or put in a URL
  (L3)" (`:66-67`).
- `src/auth/codeExchange.ts:35-38` — `TokenResponse { idToken: string; accessToken: string }`, and `:103` the
  function. `expires_in`, `scope` and `token_type` are on the wire but dropped at `:133`.
- `src/auth/CallbackScreen.tsx:122-135` — the only caller; `:130` is the nonce check that must precede retention.
- `src/auth/signInNotice.ts` — the package's sanctioned module-scoped-state pattern: a module `let`, a setter, a
  single-read taker, and a doc comment justifying why it is not in storage.
- **Config keys: none.** **Contracts: none newly engaged.**

## Inputs

- `response.accessToken` from a successful `exchangeAuthorizationCode`, after R8's two checks and T19's nonce check
  have all passed.
- A sibling tab's renewed token, through `adopt()`.

## Outputs

- `current()` — the held token, or `null` when none is held.
- Conformance to `SessionTokens` and `LocalSession`, so `createSessionBoundary` and `shareRenewalAcrossTabs` accept
  the holder with no change to either.

## State Changes

**One module-scoped variable, in memory, for the life of the tab.** That is the task's entire state change and the
reason it exists. **Nothing is written to any storage**: no `sessionStorage`, `localStorage`, IndexedDB, cookie,
URL or cache. The three L16 items are untouched — deleting them is task 23's. No React state holds the token, and
no second copy exists anywhere in the app.

## Files to Create

- One module under `frontend/src/auth/` holding the access token. `package.md` §6's file map does not name it and
  `tasks.md` task 21 says only "a module-scoped variable", so the name is the implementer's.

## Files to Modify

- `frontend/src/auth/CallbackScreen.tsx` — retain the token after the nonce check passes.
- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — the in-memory half, added to
  T20's 69 cases.
- `frontend/src/auth/codeExchange.ts` — **only if** the holder is decided to need `expires_in` (Open Questions).

## Files NOT to Modify

- Anything under `spec/`.
- `frontend/src/api/sessionBoundary.ts`, `crossTabRenewal.ts`, `client.ts` — built and tested in tasks 5 and 6;
  they define the contract T21 fills. Widening either interface is rewriting those tasks.
- `frontend/src/auth/idTokenNonce.ts` — T19's decision is settled.
- `contracts/**` — T21 engages no endpoint.
- `libs/ts/api-client/**` — generated; T21 makes no call.
- `frontend/.oxlintrc.json` — the holder's module-level `let` is deliberate and must be justified in source, not
  excused by a rule change.

## Acceptance Criteria

**AC1** The access token from a successful exchange is retained in a module-scoped variable. · **AC2** Retention is
proven over **two unrelated token values**, so nothing passes against a constant. · **AC3** `current()` is `null`
before any exchange. · **AC4** `current()` returns the token after a success, and the same value on repeated reads.
· **AC5** The **real** holder is accepted by the **real** `createSessionBoundary` and its token reaches the
`Authorization` header — conformance proven by use, not by a type assertion. · **AC6** The real holder is accepted
by the real `shareRenewalAcrossTabs`. · **AC7** `adopt()` replaces the held token, and does so with none held. ·
**AC8** No token value or segment reaches `sessionStorage`, `localStorage`, IndexedDB, `document.cookie`, the URL,
the DOM, the tab title or any console channel. · **AC9** The token is held **once**: no second copy in React state,
a prop, a `globalThis` property, another module's closure, or a thrown message. · **AC10** A bad `state`, a bad
nonce and a refused exchange each leave `current()` null. · **AC11** The three L16 items are unaffected; T21
writes, deletes and clears nothing in any storage. · **AC12** No new user-visible string and no catalogue key. ·
**AC13** No `import.meta.env`, no absolute address, no new dependency, no state library. · **AC14** T19's nonce
check and R8's ordering are unchanged; a token is held only after both pass. · **AC15** The whole suite passes with
the one known environmental failure and no other, **verified by the run's exit code**, not its counts.

## Required Tests

**Named:** `callbackValidatesNonceAndKeepsAccessTokenInMemory` (`package.md:111` → R7). T20 owns the verifier half
(69 cases); T21 adds the in-memory half to the same file, because the test's name is both halves of one R7
sentence.

- **Retention** — a token is held; two unrelated values, so no constant passes; repeated reads are stable; nothing
  held before an exchange.
- **Absence** — after a missing `state`, a mismatched `state`, a bad nonce, a refused code, a 5xx, a dropped
  connection, and a response with no usable tokens.
- **Conformance by use** — the real holder passed to the real `createSessionBoundary`, asserting the token arrives
  as `Bearer` on a request; and to the real `shareRenewalAcrossTabs`.
- **`adopt()`** — over a held token, and with none held.
- **Non-leakage** — both storages, IndexedDB, `document.cookie`, the URL, the DOM, the title, every console
  channel; by whole value **and by segment**.
- **Singleness** — exactly one module-level mutable binding in the holder; no `globalThis` property; the token in
  no thrown message.
- **Source scans** — no storage API named in the holder, no `console.`, no `import.meta.env`, no absolute address;
  **each scan proving itself against a synthetic violation**.
- **Controls** — every block carries one proving its assertion is reached, not merely satisfied.
- **Regression** — T20's 69 cases, T19's 123, T18's 134, plus `parallel401sTriggerExactlyOneRenewal` and
  `siblingTabsShareOneRenewal` keep passing; affected assertions **split, not rewritten**.

## Constraints

- **Security.** The token is bearer credential material. It may reach exactly two places: the holder's variable and
  the `Authorization` header the built boundary sets (`sessionBoundary.ts:88-91`). Not a log, not a URL, not the
  DOM, not a thrown message, not a second variable. A holder that could be read by name off `globalThis` would
  defeat the task while passing a storage sweep.
- **Module boundaries.** `src/auth` owns the session's contents; `src/api` owns transport and the boundary. T21
  must not add auth logic to `src/api`, and must not re-declare an interface `src/api` already exports.
- **Null handling.** `current()` returns `string \| null` and **`null` is the ordinary state**, not an error: there
  is no half-authenticated screen (R22). The holder must not throw on absence, and must not invent a sentinel.
- **Thread-safety / concurrency.** JavaScript is single-threaded, so the variable needs no lock — but **tabs are
  not one process**. Cross-tab coherence is `shareRenewalAcrossTabs`'s job and `adopt()` is how it reaches this
  tab; T21 must not add a second cross-tab path. The callback screen's `handled` latch already guarantees one
  retention per mount, including under StrictMode.
- **Transactions.** None; there is no persistence.
- **Performance.** A variable read. Nothing to measure, and nothing may be memoized into a second copy.

## Open Questions

**BLOCKER · `LocalSession` requires `renew()`, and no task implements the renewal mechanism.**

`LocalSession` (`crossTabRenewal.ts:4-6`) is `SessionTokens` plus `adopt`, so a holder that satisfies it must supply
`renew(): Promise<string>`. The mechanism — re-opening `/oauth2/authorize` on the existing SAS session cookie in a
hidden iframe or background navigation (`agents.md:25-34`, R21) — is implemented by **no task in `tasks.md` 1-34**.
Task 5 built single-flight 401 handling, task 6 built cross-tab sharing, task 33 handles a *failed* renewal; all
three take an injected session. The owner's Q5 decision (`package.md`, closed 2026-10-10) chose this model, but no
task was written to build it.

The three exits, costed in `artifacts/01-specification-extraction.md` (OQ-2):

1. **A throwing seam for `renew()`**, exactly as `codeExchange.ts` was from T17 until T20 filled it. The holder
   satisfies `LocalSession` today, the boundary can be wired, and the seam states the truth. The house pattern,
   already proven twice in this package.
2. **Implement silent re-authentication inside T21** — an iframe flow, a second `state`/`nonce` pair, a timeout and
   a failure route. Work no task scopes, and far larger than the task statement.
3. **Narrow the holder to `current()` plus a setter**, leaving `LocalSession` unimplemented and three built modules
   still waiting.

**This brief does not choose.** Phase 3 is the right place to attack it and Phase 4 freezes it as a numbered
decision.

**Non-blocking, carried for Phase 3:** where T21 stops and task 22 begins, given that "and nowhere else" is
enumerated nowhere (OQ-1); whether T21 also wires the composition root, which still does not exist and which no
task claims (OQ-3); whether the holder keeps `expires_in` or the ID token, when both built consumers model a bare
string (OQ-4).

---

## Next — Phase 3 — design challenge. **Kimi 2.7.**
