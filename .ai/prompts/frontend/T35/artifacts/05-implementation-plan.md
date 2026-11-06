<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T35 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/04-freeze-task.md` (frozen) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code in this phase. Every file below traces to the frozen brief's Files sections; nothing else is
authorized. **Entities, repositories, services, DAOs, schema and migrations do not exist in this service** —
those headings belong to the Spring template this prompt is shared with, and I will not invent frontend
stand-ins for them.

## Files to create

| File | Contents |
|---|---|
| `frontend/src/auth/signOut.ts` | one exported function, no state, no parameter, `void` |
| `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | the named test, arranged by criterion so task 36 appends |

## Files to modify

| File | Change |
|---|---|
| `frontend/src/auth/returnTarget.ts` | **one additive export**, `clearReturnTarget()`. No existing line changes. |

## Public signatures

```ts
// src/auth/signOut.ts
export function signOut(): void;

// src/auth/returnTarget.ts  (additive)
export function clearReturnTarget(): void;
```

`signOut` takes no parameter, which is AC5 as a type-level property rather than a discipline: there is no
session identifier to pass, and no reason or outcome it could accept and then be tempted to branch on.

## Private helpers

**None in `signOut.ts`.** The function body is four calls in a fixed order, and a helper would be a place for
logic to accumulate in the one module that must not have any. `clearReturnTarget` has no helper either: it is
a guarded `removeItem`, deliberately not sharing a body with `takeReturnTarget` (frozen brief, F3).

## Modules used

| Module | What is used | Why it is the right one |
|---|---|---|
| `auth/accessToken.ts` | `clearAccessToken()` | the only writer of `null` to the held token; idempotent by its own contract |
| `auth/signInNotice.ts` | `takeSignInNotice()` | reading clears, so the read *is* the clearing primitive |
| `auth/authorizeRequest.ts` | `consumeAuthorizeState()` | owns the three PKCE keys; idempotent, never throws, never clears the store |
| `auth/returnTarget.ts` | `clearReturnTarget()` *(new)* | removes without consuming — T24-D4's "different consumers, different moments" |

Read-only, for the test alone: `api/crossTabRenewal.ts` (`shareRenewalAcrossTabs`, `RENEWAL_CHANNEL`,
`RENEWAL_LOCK`) for AC9's hazard case, and `AUTHORIZE_STORAGE_KEYS` / `RETURN_TARGET_KEY` for the derived
inventory. No production module is modified to make the test possible.

## Order inside `signOut()`

Fixed, and the order is load-bearing for one of the four:

1. `clearAccessToken()` — **first**, for the same reason T33's handler clears first: every later step can in
   principle throw, and a tab that still holds a token after a partial sign-out is the half-authenticated tab
   R22 forbids. The other three are tidying; this one is the security property.
2. `takeSignInNotice()` — discards a notice `cleanReauth` may have queued, so a later sign-in screen does not
   show `session-ended` to someone who signed out deliberately.
3. `consumeAuthorizeState()` — the three PKCE keys, for a sign-out during a mid-flight sign-in.
4. `clearReturnTarget()` — so a later sign-in is not silently redirected to where this visitor was.

Steps 2–4 are each independently guarded inside their own modules, so none can prevent another. Their
relative order is not load-bearing and the file will say so, rather than implying a significance it does not
have.

## Tests required

One `describe` per criterion, in criterion order, so task 36 appends a block rather than restructuring:

| Criterion | Cases |
|---|---|
| **AC1** | token cleared; and cleared when it was adopted from a sibling rather than exchanged |
| **AC2** | twice in a row; from a cold tab that never signed in |
| **AC3** | notice gone; return target gone; authorize triple gone, from a genuinely mid-flight state built by the real authorize step |
| **AC4** | synchronous — the function's return is `undefined`, not a promise; the state is already clear on the next line; source scan for `async`, `await`, `setTimeout`, `queueMicrotask`, `.then(` |
| **AC5** | `signOut.length === 0`; return value `undefined` |
| **AC6** | a watched store records exactly four `removeItem` calls on exactly the four keys, with no `getItem`, no `setItem`, no `clear()`; plus a console sweep over all six channels |
| **AC7** | the module-scope-mutable scan over production source, with its exclusion list; **control**: a planted `let` in a scanned file is caught |
| **AC8** | the derived inventory's shape; the `themistra.`-literal scan; the no-literal-of-its-own case; **control**: a planted `themistra.` literal outside the inventory is caught |
| **AC9** | the hazard — sign out, post `renewal:succeeded` on the real channel, observe the token return; message names task 39 |
| **AC10** | the primitive-caller allowlist scan; **control**: a planted call in a non-allowlisted file is caught |
| **AC11** | no new route, no new catalogue key, no network call; the changed-file set is the three the brief authorizes |
| **AC12** | a `sessionStorage` that throws on every operation — `signOut` does not throw, and the token is still `null` |

Every scan and sweep gets a control, because a scan that reports clean without ever having been shown to
fire is not evidence. The known traps to avoid, from earlier tasks in this package: no key or pattern literal
is spelled in the test file (a file searching for a string it contains finds itself), jsdom's `Storage` is
proxied so an own-property `vi.spyOn` on it never intercepts, and URL/DOM resets belong in `afterEach`.

## Execution order

1. `clearReturnTarget()` in `returnTarget.ts` — the one new primitive, and nothing else compiles against it yet.
2. Run T22's and T24's suites immediately. They are the two that guard this file; the frozen brief argues the
   addition is safe and this is where that argument is tested rather than asserted.
3. `signOut.ts`.
4. The test file: AC1–AC3 first (the behaviour), then AC4–AC6, then the three scans AC7/AC8/AC10 with their
   controls, then AC12.
5. **AC9 last**, because it is the only case that stands up the real cross-tab channel and the only one whose
   expected result is a hazard rather than a success. It is written last so a mistake in it cannot be
   mistaken for a failure of the clearing.
6. `tsc --noEmit`, `oxlint src`, the T35 file, then the whole suite against the recorded baseline.
7. **Commit the tests** before building the mutation harness — the harness reverts with `git checkout` and has
   silently destroyed uncommitted test edits twice in this package (19 cases in T14, 5 in T15).
8. The harness: mutants per criterion, canaries scored apart, each mutant's failure count recorded, and the
   baseline asserted by the harness itself.

## Risks carried into implementation

- **AC9 may not reproduce as reasoned.** `shareRenewalAcrossTabs` returns an inert object unless both
  `navigator.locks` and `BroadcastChannel` are available (`crossTabRenewal.ts:83-86`), so the case must inject
  a channel and a fake lock manager to reach the listener at all. If the hazard turns out not to reproduce,
  **OQ-T35-3 is withdrawn and the frozen brief's F5 resolution is corrected in phase 7** — the finding is only
  worth raising if it is real.
- **The `themistra.` scan may find a literal I have not predicted.** If it does, the finding is the output;
  the scan is not loosened to make it pass.
- **AC6's exact-four assertion depends on `takeReturnTarget` not being called.** If the count comes out at
  five with a read, something is calling the consuming primitive and the fix is in `signOut.ts`, not the
  assertion.
