# frontend · T23 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

Task statement: *"`state`, `nonce`, and `code_verifier` survive the full-page redirect in session storage and are
deleted at callback. Verifies `stateSurvivesRedirectAndIsConsumedOnce`. Cites R8, L16."*

---

## Order of work

**The production change lands before the tests that measure it**, as T18 did, and the inherited assertions are
updated in the same commit — otherwise the suite is red between two commits and the reason is spread across both.

### 1 · `auth/authorizeRequest.ts` — `consumeAuthorizeState()` (T23-D1, D3, D4, D12)

Appended after `buildAuthorizeRequest`, at the end of the file (`:98`). The keys constant is at `:35-39`, in this
file, which is the whole reason the function lives here.

```ts
/**
 * Removes the three single-use items the authorize step stored, which L16 calls "deleted at callback".
 *
 * Contract: removes exactly the three keys `AUTHORIZE_STORAGE_KEYS` names, is idempotent, never throws, never
 * clears the store, and never touches another key. It is NOT a general cleanup helper - L16's fourth item, the
 * post-login return target, does not exist yet and belongs to task 24, and R29's form input is task 26's.
 *
 * Each removal is guarded on its own. `removeItem` can throw rather than fail quietly - the same reason every
 * read in this package is wrapped - and the realistic failure is a store that is denied outright, which throws
 * for all three and leaves all three. sessionStorage has no transaction, so "all three or none" is a property of
 * there being ONE call site, not of atomicity this code could provide.
 *
 * Why this is not three removals at the call site: the keys are spelled here, once, and a second spelling is
 * what T22's scan forbids. Why it is not inside the two readers: `codeExchange.ts` and `idTokenNonce.ts` are
 * pure, and three unit tests assert that they are.
 */
export function consumeAuthorizeState(): void {
  for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) {
    try {
      globalThis.sessionStorage.removeItem(key);
    } catch {
      // A cleanup that cannot run is not a failed sign-in (T23-D4). The visitor's outcome does not change, and
      // the next authorize request overwrites all three anyway.
    }
  }
}
```

`Object.values` rather than three named calls, so adding a key to the constant cannot leave a fourth item behind
— and so the function cannot drift from the writer three lines above it.

### 2 · `auth/CallbackScreen.tsx` — one call, after the nonce check (T23-D1, D3)

At `:149`, where `retainAccessToken(response.accessToken)` already sits, immediately after it:

```ts
      retainAccessToken(response.accessToken);
      // R8's "single-use", and the last thing this flow does with storage. Here and nowhere earlier: the
      // verifier is read INSIDE the exchange (`codeExchange.ts:59`) and the nonce after it resolves, so a
      // deletion any sooner strands a consumer with a null it cannot recover from - and the discard that
      // followed would be indistinguishable from a real failure.
      //
      // Only on this path. A discarded callback leaves all three in place (T23-D2): this route is public, so a
      // discard that deleted would let anyone who gets a signed-in visitor to open /app/callback?state=x end
      // that tab's pending sign-in. The code behind a failed exchange is already spent server-side - a replay
      // returns 400 invalid_grant, measured in T20 - so keeping the verifier costs nothing.
      consumeAuthorizeState();
```

One import added: `consumeAuthorizeState` from `./authorizeRequest`, which already imports
`AUTHORIZE_STORAGE_KEYS` from there — no new module edge.

The doc comment at `:23-27` currently reads *"It writes nothing and deletes nothing: the `code_verifier` is task
20's, and deleting all three is task 23's."* It is rewritten to state what the screen now does and what it still
does not: it writes nothing, and it deletes all three **only** when the callback was consumed.

### 3 · The eight inherited assertions (T23-D8, D9, D10, D11)

| File | What changes |
|---|---|
| `callbackValidatesNonce.test.tsx:563-577` | **nothing but the comment** (T23-D9). The pure check still touches no storage; the case that was pinned to "fail by design" is answered by saying why it does not. |
| `callbackWithMismatchedStateIsDiscarded.test.tsx:405-419` | the no-`state` arrival still deletes nothing; the comment becomes T23-D2's frozen position with its reason. |
| `callbackWithMismatchedStateIsDiscarded.test.tsx:1008-1025` | same, for the mismatched arrival. |
| `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:671-689` | the success path now **removes three**. The block's name ("nothing is written, deleted or cleared") becomes false and changes with it. |
| `tokensAreNeverWrittenToAnyStorage.test.tsx:333-343` | split into `expectAuthorizeWritesOnly` and `expectAuthorizeStateConsumed` (T23-D10); the two call sites at `:509` (success) and `:652` (nine failure rows) take one each. |
| `tokensAreNeverWrittenToAnyStorage.test.tsx:345-357` | `STORAGE_ALLOWLIST` gains `removeItem` for `auth/authorizeRequest.ts`, with the reason — the row its own comment demanded from this task. |
| `tokensAreNeverWrittenToAnyStorage.test.tsx:545-563` | the whole-store equality: after a **consumed** callback sessionStorage is **empty**; the three-item equality moves to a discard case, where it is still true. |

### 4 · Then the named test

`frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx`. Planned shape:

```
├── header: what survives, what is consumed, and T23-D5's limit on "full-page redirect"
├── harness: startSignIn() (the real authorize step), arriveAt() (real routes, stubbed fetch),
│            presentItems() / storedTriple(), watchStorage() installed BEFORE the flow
├── describe('the three survive the journey')              AC1, AC9
├── describe('a consumed callback takes all three with it') AC2, AC5, AC6, with D13's presence control
├── describe('and only once')                               AC3 replay, AC10 a fresh sign-in
├── describe('a discarded callback leaves them')            AC4, five failure shapes
├── describe('when the store will not cooperate')           AC8 throwing removeItem
├── describe('under StrictMode')                            AC7
└── describe('the order is not a coincidence')              AC6's ordering case
```

## 5 · Verification

`tsc --noEmit`; the named file; the four edited files; the whole suite by **exit code**; then a mutation harness
whose mutants are **ordering and scope** rather than leaks: move the call before the exchange, move it into the
discard closure, delete two keys instead of three, drop the guard, make it clear the store, call it twice.

## 6 · What this plan does not do

No change to `codeExchange.ts` or `idTokenNonce.ts` (purity, T23-D1). No change to any T22 artifact (T23-D11).
No new dependency, no catalogue key, no contract. The return target stays task 24's (T23-D6).

---

## Next — Phase 6 — implementation.
