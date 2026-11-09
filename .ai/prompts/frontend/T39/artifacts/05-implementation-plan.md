<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T39 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/04-freeze-task.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

## Files

| File | |
|---|---|
| `src/api/crossTabSignOut.ts` | **NEW** — channel, latch, broadcast, listener. Imports nothing from `auth/` (AC13). |
| `src/api/crossTabRenewal.ts` | **MODIFY** — one guard in `onMessage`, nothing else |
| `src/auth/signOut.ts` | **MODIFY** — `markSignedOut()` first |
| `src/auth/accessToken.ts` | **MODIFY** — `markSignedIn()` in `retainAccessToken`, the single assignment point |
| `src/auth/endSession.ts` | **MODIFY** — `broadcastSignOut()` after the local sign-out, before the navigation |
| `src/api/signOutPropagatesToAllTabs.test.ts` | **NEW** — the named test; AC1–AC6, AC10–AC13, AC16, AC17 |
| `src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **MODIFY** — AC9 replaced, AC9b deleted, `SIGN_OUT_SET`/`PERMITTED_CALLERS` grown, `NOT_SESSION_STATE` extended (AC7, AC8, AC9, AC14, AC15) |

## `src/api/crossTabSignOut.ts`

```ts
export const SIGN_OUT_CHANNEL = 'themistra.session.signout';
const SIGN_OUT_MESSAGE = { type: 'session:signed-out' } as const;

/** THE ONE module-scope mutable, excused in NOT_SESSION_STATE as the INVERSE of session state. */
let signedOut = false;

export function markSignedOut(): void;   // sets it
export function markSignedIn(): void;    // clears it — see the doc note below
export function hasSignedOut(): boolean; // read by crossTabRenewal's onMessage

export function broadcastSignOut(channel?: BroadcastChannel): void;
export function listenForSignOut(onSignOut: () => void, options?: { channel?: BroadcastChannel }): { close(): void };
```

Three points the code must get right:

1. **`SIGN_OUT_MESSAGE` is `as const`.** `NOT_SESSION_STATE`'s existing entry for `readAccount.ts` records
   that the T35 scan *"reaches object literals (phase 11 F3) and this one is not `as const`"* — so an
   `as const` literal is not counted, and the excusal stays at `declarations: 1` (AC15).
2. **`broadcastSignOut()` creates a fresh channel every call and closes it** (Q3 proves the post survives
   the close; Q4 proves a cached closed channel would throw). With an injected channel it posts and does
   **not** close — the channel is the caller's.
3. **`listenForSignOut()` closes only a channel it created**, and `close()` is idempotent.

Both functions are no-ops returning normally when `BroadcastChannel` is undefined (AC6), and
`listenForSignOut` still returns a callable `close()`.

The parser accepts only `type === 'session:signed-out'` on a non-null object and **ignores additional
properties** (AC11, D-f).

## `src/auth/signOut.ts` — one line, first

```ts
export function signOut(): void {
  markSignedOut();     // ← TASK 39, FIRST
  clearAccessToken();
  takeIdToken();
  takeSignInNotice();
  consumeAuthorizeState();
  clearReturnTarget();
}
```

The comment must say what phase 4 settled: **first because there is no try/catch**, so a throw from any
later step would leave the latch unset — a tab that cleared its token and can still be handed one. Not
because of a delivery window; phase 1's Q2 measured that no delivery can land inside a synchronous call.

## `src/auth/accessToken.ts` — the reset at the single assignment point

```ts
export function retainAccessToken(token: string): void {
  markSignedIn();      // ← TASK 39
  held = token;
}
```

`markSignedIn()`'s doc comment must say it **resets the latch and does not assert the tab is
authenticated** (F15). It is reached on a real sign-in through `CallbackScreen`; `adopt()` delegates here
but cannot reach it while the latch is set, because the guard refuses first.

## `src/api/crossTabRenewal.ts` — one guard

```ts
if (outcome.type === 'renewal:succeeded') {
  // TASK 39: a tab that has signed out does not take a token from a sibling (OQ-T35-3).
  if (hasSignedOut()) return;
  try { local.adopt(outcome.token); } catch (error) { /* unchanged */ }
}
```

**Deliberately below the counting and the waiter wake**, which stay exactly as they are: this task changes
what a signed-out tab *adopts*, not how renewal is counted or how its waiters settle. Changing those is
R21's contract and `siblingTabsShareOneRenewal.test.ts`'s.

**A residual this leaves, recorded now rather than discovered later — `OQ-T39-7`:** `use(outcome)`
(`:160-163`) still *returns* a sibling's token to a local `renew()` caller even in a signed-out tab. It is
never adopted into `accessToken`, so nothing holds it, but a caller awaiting `renew()` would receive it.
Who calls `renew()` in a signed-out tab is R22's half-authenticated question and task 33's; changing `use`
would change renewal semantics, which is R21's. Named, not closed.

## `src/auth/endSession.ts` — where the broadcast goes

```ts
export function endSession(): void {
  const hint = takeIdToken();
  signOut();
  broadcastSignOut();                       // ← TASK 39
  globalThis.location.assign(endSessionUrl(hint));
}
```

- **After `signOut()`**, so this tab has finished its own sign-out before telling anyone.
- **Before `assign()`**, because the navigation may unload the page. Q3 showed a posted message survives an
  immediate `close()`, so the fire-and-forget post is already queued when the unload begins.
- **Not inside `signOut()`** — that is what makes a receiver unable to echo (D2).

## Execution order

1. `crossTabSignOut.ts`, then `signOut.ts`'s one line. **Run T35's suite and capture the failure** — F9's
   requirement, and the only moment the `SIGN_OUT_SET` derivation can be *seen* working.
2. Grow `PERMITTED_CALLERS` (`markSignedOut: ['auth/signOut.ts']`), the derived count, and
   `NOT_SESSION_STATE`. Re-run green.
3. The `crossTabRenewal.ts` guard and `accessToken.ts`'s reset. **Run T35's AC9 and capture it going red** —
   the inherited failing expectation firing is the deliverable, not a nuisance.
4. Replace AC9, delete AC9b.
5. `endSession.ts`'s broadcast; T36's suite will have an opinion about `endSession`'s body.
6. Write `signOutPropagatesToAllTabs.test.ts`.
7. Gates, then commit — **tests committed before any mutation harness runs**.

## Risks

| | |
|---|---|
| **R1** | T36's `SIGN_OUT_SET` derives primitives by regex from `signOut.ts`'s source and asserts a count of five. Growing to six is intended; the risk is that the regex matches `markSignedOut` only if it is in `PERMITTED_CALLERS`, so step 2 must update both the allowlist and the count or the case fails for the wrong reason. |
| **R2** | T35's mutable-state scan reads **every** production file. A new `api/` file with a `let` fails it until step 2. Expected, and step 1 is where it is observed. |
| **R3** | `endSession.ts` is pinned by T36's AC1 source-order case (`takeIdToken()` before `signOut()`) and by an AC10 import scan with an **empty** `PERMITTED_IMPORTERS`. Adding an import to `endSession.ts` is adding an import *by* it, not *of* it, so that scan should be unaffected — **to be confirmed by running, not assumed**. |
| **R4** | Three suites install `shareRenewalAcrossTabs` and adopt tokens (`callbackValidatesNonce…`, `tokensAreNeverWritten…`, `siblingTabsShareOneRenewal`). If any of them calls `signOut()` before expecting an adopt, the new latch will refuse it. The latch is module state shared across cases in a file, so **`markSignedIn()` or a reset in `beforeEach` may be needed in those suites** — a cross-suite effect that must be found by running the whole suite, not the new file. |
| **R5** | AC6 deletes `globalThis.BroadcastChannel`. It must be restored even on failure, or every later case in the run loses the channel. |

## Phase 6

Implementation.
