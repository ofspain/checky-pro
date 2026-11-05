<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T33 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

## Files, in the order they will be written

| # | File | Change |
|---|---|---|
| 1 | `src/i18n/catalogue.ts` | **edit** — add `signIn.sessionEnded` = "Your session has ended. Sign in again." after `discarded` (G8). |
| 2 | `src/auth/signInNotice.ts` | **edit** — widen `SignInNotice` to `'discarded' \| 'session-ended'`, and add the notice→catalogue-key map with a completeness pin. |
| 3 | `src/auth/cleanReauth.ts` | **new** — the failure routing (G9). |
| 4 | `src/auth/SignInScreen.tsx` | **edit** — render the copy for the notice that was set, instead of always rendering `discarded`. |
| 5 | `src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` | **edit, sanctioned cross-task** — add `sessionEnded` to `SIGN_IN_KEYS` with the justification that instrument demands of itself. |
| 6 | `src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.ts` | **new** — the named test. |

## 1–2. The notice and its copy

`SignInNotice` gains a member, and the **mapping from member to catalogue key moves into
`signInNotice.ts`** rather than living in the screen. That is T31's A2 lesson applied before a reviewer has
to ask for it: a union and the table keyed by it belong in one module, so a scan can license a file rather
than a regex, and so adding a third member is a compile error in one place rather than a silently missing
branch in a component.

The completeness pin is the package's existing two-directional idiom:

```ts
export const NOTICE_COPY_KEY = { discarded: 'signIn.discarded', 'session-ended': 'signIn.sessionEnded' }
  as const satisfies Record<SignInNotice, string>;
type AssertNever<T extends never> = T;
type NoUnmappedNotice = AssertNever<Exclude<SignInNotice, keyof typeof NOTICE_COPY_KEY>>;
```

## 3. `src/auth/cleanReauth.ts`

```ts
export function createRenewalFailureHandler(navigate: (to: string) => void): () => void
```

**The returned handler takes no parameter at all.** A zero-argument function is assignable to
`onRenewalFailed: (reason: unknown) => void`, so G11 — "the module ignores `reason`" — is enforced by the
type system rather than by a scan that hopes nobody adds a branch. It is the strongest available form of
that guarantee and it costs nothing.

Body, in this order, and the order is load-bearing:

1. `clearAccessToken()` — **first**. Any later step could throw; a notice set while a token is still held
   is precisely the half-authenticated tab R22 cl. 2 forbids.
2. Remember the allowlisted return target, derived from `globalThis.location.pathname` with
   `APP_BASENAME` stripped. `rememberReturnTarget` filters non-allowlisted targets itself and ignores them
   silently (`returnTarget.ts:88-90`), so being already at `/app/sign-in` needs no special case (G4, F7).
3. `setSignInNotice('session-ended')`.
4. `navigate('/' + SIGN_IN_PATH)`.

**`SIGN_IN_PATH` and `APP_BASENAME` are read inside the function, never at module scope** (G9). This is not
caution for its own sake: `returnTarget.ts:40-49` measured in T16 that a module-scope read of a route
constant yields `undefined` rather than throwing, and an `undefined` allowlist **fails closed silently** —
the SPA looks like it works and nothing is ever honoured again.

The basename strip mirrors `authorizeRequest.ts:60`'s trailing-slash handling, so `/app` and a future
`/app/` behave the same.

## 4. `SignInScreen.tsx`

One line changes: `t('signIn.discarded')` becomes `t(NOTICE_COPY_KEY[notice])`. Today the screen renders
the discarded sentence for **any** notice, so without this a `'session-ended'` notice would display a
sentence that is false — which is the whole reason G3 rejected reusing the member.

Checked against the two suites that own this screen: `Object.values(en.signIn)` must not appear as
literals in the component's source (`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:294-300`)
— satisfied, the screen gains a key lookup and no string; and that case's `>= 5` floor still holds at 7.

## 5. The sanctioned cross-task edit

`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx:254` pins the `signIn` key set as
an exact set, and its own comment says what to do: "a seventh `signIn.*` key is a new sentence somebody
added, **and this fails until they justify it here**." So the edit is what the instrument asks for, not a
circumvention of it: add `'sessionEnded'` in sorted position and write the justification in that file.

Two checks done before touching it, because an exact-set pin is easy to break in a way that looks fine:

- `sessionEnded` matches **no** alternative in `FORBIDDEN_KEY_SOURCE` (`:242-244`) — not `lockout`, not
  `locked`, not `suspended`, none of the factor words.
- The copy matches none of the forbidden copy alternatives either.

Nothing else in `src/` pins the catalogue's shape: the only other enumeration is
`Object.keys(globalThis.sessionStorage)).toHaveLength(3)` in the scopes suite, which is about storage, not
copy.

## 6. The test file

`src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.ts`, beside the module it instruments —
this package's convention is that a test sits with its subject. It still drives the **real** boundary from
`src/api/`, because AC2, AC3, AC7 and AC8 are statements about how the hook is called, and a test that
stubbed the boundary would prove only that the module can be called the way the test chose to call it.

The fake transport, the fake session and the deferred-per-attempt shape are copied from
`parallel401sTriggerExactlyOneRenewal.test.ts:30-100`. That is the third copy of that harness, and it is
recorded as such in the frozen brief under Kimi F11 rather than extracted, for the same reason OQ-T32-4
stays open: the two existing copies belong to closed tasks.

Cases, by criterion:

| AC | Cases |
|---|---|
| AC1 | the token is cleared; `clearAccessToken` is the only clearing route (source) |
| AC2 | one transition per renewal under three parallel 401s; two successive failed renewals give two transitions; repeating the handler while already at the destination is harmless |
| AC3 | every waiting call still gets its 401; the second caller starts no second renewal |
| AC4 | the destination is `SIGN_IN_PATH`, is public, is not spelled again; no module-scope route read |
| AC5 | the rendered sentence is `signIn.sessionEnded`, is not `discarded`, names no cause |
| AC6 | no failure information on seven surfaces or six console channels; the return target is the only write |
| AC7 | a follower tab adopting `renewal:failed` transitions once and renews nothing |
| AC8 | a throwing handler does not reach the waiting callers |
| AC9 | two different rejection reasons give an identical outcome; the handler takes no parameter (type-level) |
| AC10 | `SIGN_IN_PATH` is in `PUBLIC_ROUTE_PATHS` and absent from `AUTHENTICATED_ROUTE_PATHS` |
| AC11 | every authenticated route has no element — the pinned environmental assumption |

Plus the controls this package requires: a dirty-proof for every absence claim, `refuseEmpty` on any scan,
and the leak sweep driven dirty before it is trusted clean.

## Risks

- **The cross-task edit (#5) is the one that can break a closed task.** It is committed in the same change
  as the catalogue edit so the suite is never left red between two commits.
- **AC11 is designed to fail later.** When task 173/174 lands a real screen on an authenticated route, this
  case fails by construction. Its message will say so and name T41, so the next author reads an instruction
  rather than a puzzle.
- **The handler's zero-parameter signature is load-bearing for AC9.** A later maintainer "fixing" the
  signature to accept `reason` would silently remove the guarantee, so a case asserts the arity.

**Phase 5 complete.**
