<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T36 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/04-freeze-task.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

## Files to create

| File | Contents |
|---|---|
| `frontend/src/auth/idToken.ts` | `retainIdToken`, `takeIdToken`. One module-level `let`. No plain reader (AC15). |
| `frontend/src/auth/endSession.ts` | `END_SESSION_PATH`, `endSession`. No module-level mutable state. |

## Files to modify

| File | Change |
|---|---|
| `auth/signOut.ts` | one added call, `takeIdToken()`, second in the body |
| `auth/CallbackScreen.tsx` | one added line, `retainIdToken(response.idToken)`, beside `retainAccessToken` |
| `auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | T36's criteria appended; five T35 structures extended, each marked |

## Public signatures

```ts
// auth/idToken.ts
export function retainIdToken(token: string): void;
export function takeIdToken(): string | null;

// auth/endSession.ts
export const END_SESSION_PATH = '/connect/logout';
export function endSession(): void;
```

**Four exports, and the count is deliberate.**

- No `currentIdToken()` — AC15. With no plain reader the token cannot be obtained without being consumed, so
  the read-before-clear window F2 described cannot be reopened by a later edit, and a module that wanted to
  log it would have to clear it first.
- No exported URL builder. The URL is built by a private helper and reaches the test through the
  hand-over watcher, which is how T16 reads the authorize URL. Exporting the builder would create a way to
  construct the end-session URL without ending the session, which is the thing AC10 exists to prevent.
- No parameters on `endSession()`, as phase 2 froze: nothing to pass and nothing to branch on.

## Private helpers

In `endSession.ts`, neither exported:

- `postLogoutRedirectUri(): string` — `${origin}${de-slashed APP_BASENAME}/${SIGNED_OUT_PATH}`, the same
  derivation `authorizeRequest.ts:59-62` performs for the callback, including the de-slash.
- `endSessionUrl(hint: string | null): string` — builds the URL with `URLSearchParams`; omits
  `id_token_hint` entirely when `hint` is `null` (AC12).

**Both read `APP_BASENAME` inside the function body**, never at module scope. This is not a style choice:
`routes.tsx:2-5` documents the import cycle `routes → SignInScreen → authorizeRequest → routes`, which
resolves only because the basename is read inside a function. `endSession.ts` is not in that cycle today,
but the same discipline keeps it from joining one, and it is what lets the tests move the route table.

## Order inside `signOut()`

```
clearAccessToken();   // first — T35's AC1, pinned behaviourally by a forced throw
takeIdToken();        // second — T36. Value discarded.
takeSignInNotice();
consumeAuthorizeState();
clearReturnTarget();
```

Second, not first and not last. T35's argument for `clearAccessToken()` first is that there is no `try/catch`,
so a throw abandons what follows, and the clear is the one step whose omission is a **security** defect rather
than an untidy store. The `id_token` is a credential too — a signed assertion of identity, and the hint that
ends the SAS session — so it belongs with the first step rather than among the three stores. It is second
rather than first because T35's existing case pins `clearAccessToken()` as the first statement, and because
between two credentials the bearer token is the one an attacker can spend.

`endSession()` calls `takeIdToken()` **before** `signOut()`, for the hint. `signOut()`'s own call then returns
`null`. That is not redundant: it is what makes the `id_token` gone whether sign-out was entered through
`endSession()` or through `signOut()` alone, and both go through the one idempotent primitive. Said in both
modules.

## Order inside `endSession()`

```
const hint = takeIdToken();   // 1. consume, before anything can throw
signOut();                    // 2. clear the rest — AC1
globalThis.location.assign(endSessionUrl(hint));   // 3. hand over — AC2
```

Clear first, hand over second (AC1). The URL is built at step 3 rather than step 1 so that the whole clear has
happened before anything derived from the route table can fail.

## The `'signed-out'` spelling — a gap, and how it is closed

`routes.tsx` exports `SIGN_IN_PATH`, `CALLBACK_PATH` and `HOME_PATH` as named constants, **but not a
`SIGNED_OUT_PATH`**. The route exists only as the literal `'signed-out'` at `PUBLIC_ROUTE_PATHS[8]`
(`routes.tsx:31`). Measured, not assumed.

`routes.tsx` is on the NOT-to-modify list, so T36 does not add the export. Instead `endSession.ts` declares
`const SIGNED_OUT_PATH = 'signed-out';` privately, and the suite holds the two spellings equal:
**`PUBLIC_ROUTE_PATHS` must contain it** — which also proves the post-logout target is a *declared* route
(L14) and a *public* one, since a logged-out visitor must be able to reach it. A rename of the route then
fails here rather than as a `400` from SAS in a browser nobody is watching.

This is the same instrument as AC16's, for the same reason: two spellings held equal by a test cost less than
a coupling, and neither edits a closed module. **Recorded as a deviation** — the criterion says "derived from
the route table", and this is one derivation step short of that. The honest description is *checked against*
the route table.

## Modules used

| From | Used for |
|---|---|
| `auth/signOut.ts` | `signOut` |
| `auth/idToken.ts` | `takeIdToken` |
| `app/routes.tsx` | `APP_BASENAME` — read inside a function |
| `globalThis.location` | `origin` (read), `assign` (the hand-over) |

`CLIENT_ID` is **not** used: AC6 freezes the parameter set at two, and the end-session endpoint does not take
a client id. The phase-2 dependency table listed it; it is dropped here.

## Tests required

Appended to `signOutClearsMemoryAndEndsSasSession.test.tsx`, one describe per criterion, plus:

**Reused, not reinvented**

- `watchHandover` from `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:277-300` — all four
  mechanisms (AC2, F5). Copied with a comment naming its origin, since there is no shared helper module
  (OQ-T32-4, now nine reviews).
- `vi.doMock('../app/routes', …)` + `await import('./endSession')` from
  `authorizeRequestsExactlyTheDeclaredScopes.test.ts:288-308` — the proven way to move the route table
  (AC4).

**Cases the existing suites prove are necessary**

- **The basename moved to `/console`**, not merely slashed. `authorizeRequestsExactlyTheDeclaredScopes.ts:286`
  records why: while `APP_BASENAME` is `/app`, `const base = '/app'` and `const base = APP_BASENAME` produce
  the same string, so **no assertion against the real route table can separate derivation from coincidence.**
  T36 inherits that hole exactly and closes it the same way. A mutant hardcoding `/app/signed-out` must die.
- The trailing slash, so `/app//signed-out` cannot ship (AC4).
- Hint omitted, not empty: `takeIdToken()` returning `null` must leave the parameter **absent**, and the case
  asserts `params.has('id_token_hint')` is `false` — not that it equals `''`, which is what a
  `?? ''` would produce and what `URLSearchParams` would happily serialise (AC12).
- A second `endSession()`: two hand-overs, the second with no hint, nothing further cleared (AC13).
- `retainIdToken` after the nonce check in `CallbackScreen.tsx`, with the early return between, plus a
  control proving the scan fires on a planted hoist (AC14).
- The `id_token` appears exactly once across the URL, and nowhere in any store or console channel (AC8).
- `DENYLISTED_NAVIGATION_PATHS` contains `END_SESSION_PATH` (AC16).
- `/connect/logout` in exactly two production files, by name and reason (AC10), with a control.

**Controls for every scan and sweep**, each shown firing on a planted positive — the standing rule since T29.

## Execution order

1. `auth/idToken.ts`.
2. `auth/signOut.ts` — the one added call. **Run T35's suite**: it will go red on `INVENTORY`,
   `PERMITTED_CALLERS`, `DEFINERS` and the `SIGN_OUT_SET` monopoly case. That red is expected and is the
   handoff T35 designed; a green run here would mean T35's scans do not cover what they claim.
3. Extend those four structures plus T35's AC7 declaration counts (AC9), each marked with a T36 comment (F9).
   T35's suite green again.
4. `auth/CallbackScreen.tsx` — the one added line. **Run T19's and T23's suites** to confirm F8's measured
   answer, rather than trusting it.
5. `auth/endSession.ts`.
6. T36's cases, criterion by criterion.
7. `tsc --noEmit`, `oxlint src`, whole suite.
8. **Commit the tests**, then the mutation harness — never the other way round.

## Risks carried into implementation

- **Step 2's expected red is the plan's one real claim about T35.** If T35's suite stays green after a fifth
  primitive is added to `signOut.ts`, T35's AC10 and AC8 are weaker than their artifacts say, and that is a
  finding about T35 to record rather than a convenience to accept.
- **`vi.stubGlobal('location', …)` replaces the whole object**, so the stub must carry `origin` as well as the
  four navigation members, or `postLogoutRedirectUri()` reads `undefined` and the URL is silently wrong while
  the hand-over count still passes. T16's helper is the reference.
- **AC8's sweep must not flag the parameter it requires** (F3). The sweep is scoped to stores and console
  channels, and the URL is asserted separately with an exactly-once count.
- `takeIdToken()` in `signOut.ts` must not trip T35's AC11 scan. Checked: that scan forbids `fetch(`,
  `XMLHttpRequest`, `react-router`, `t(`, `catalogue` and `navigate` in `signOut.ts`
  (`signOutClearsMemoryAndEndsSasSession.test.tsx:888`). `takeIdToken` matches none, and the `/^(let|var)\s/m`
  ban at `:925` is untouched because the `let` lives in `idToken.ts`.
- **The hand-over cannot be in `signOut.ts`** — that same scan's `/navigate/` clause, and the reason
  `endSession.ts` exists rather than an excuse to work around (F14).

**Phase 5 complete.**
