# frontend · T24 · Phase 5 — Implementation Plan

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**FROZEN**) |
| **Produces** | `artifacts/05-implementation-plan.md` |

Four edits of production code, eleven inherited assertions, one new test file. The plan's only real ordering
constraint is the one T23 proved: **the production change lands in the same commit as the assertions that measure
it**, because a commit that changes one side leaves the suite red with the reason spread across two commits.

---

## Order of work

### 1 · `auth/returnTarget.ts` — new (T24-D1, D3, D8, D9, D10)

Four exports and one derived constant.

```
RETURN_TARGET_KEY            the one storage key, spelled once
ALLOWLISTED_TARGETS          Set, derived: AUTHENTICATED_ROUTE_PATHS
                             filtered to paths with no ':' , each prefixed '/'
isAllowlistedTarget(t)       Set membership. Nothing else - no prefix test,
                             no normalising, no decoding (T24-D3, D8)
rememberReturnTarget(t)      one guarded setItem (T24-D8's documented form)
takeReturnTarget()           read -> validate -> delete -> return, total (T24-D10)
```

The shape that matters, and the reason each clause is there:

- **`takeReturnTarget` returns a destination, never an option.** No caller then has to decide what a rejected
  target means, so there is one place the fallback can be wrong instead of one per call site.
- **Read before delete, in one function.** AC7's ordering is satisfied by construction rather than by a comment:
  there is no arrangement of these statements that deletes before reading, because the value is already in hand.
- **Total.** One `try` around the whole body returning `HOME_TARGET` in its `catch`, *plus* a guard on the
  removal, so a throwing removal does not lose a target already validated (T24-D9). A throw here would not be
  caught by `discard` — `CallbackScreen.tsx:132` passes it as `.then`'s second argument, which does not see a
  synchronous throw from the first (T21 measured this) — and would blank the page at the end of a sign-in.
- **Derived, not written.** `ALLOWLISTED_TARGETS` comes from `routes.tsx`, so a route added there is legal with
  no second edit, and a stale entry cannot outlive its route (AC6, AC17).

The module's doc comment carries the contract: what it stores, that the value is **untrusted on read whoever
wrote it**, that `/app/…` is not a member and why there is no stripping, and that parameterised routes are
excluded with T24-D5's named consequence.

### 2 · `app/routes.tsx` — `HOME_PATH` (one line, plus its use)

`export const HOME_PATH = 'home';` and `HOME_PATH` in `AUTHENTICATED_ROUTE_PATHS`, so the home route has one
spelling, exactly as `SIGN_IN_PATH` and `CALLBACK_PATH` already do. Nothing else in that file changes.

### 3 · `auth/CallbackScreen.tsx` — the navigation and the cleanup (T24-D7)

One statement after `consumeAuthorizeState()`:

```
navigate(takeReturnTarget(), { replace: true });
```

`replace` so a completed callback is not reachable with the back button — the same reason `discard` uses it, and
the thing that makes T23-D16's handoff smaller rather than larger.

**And the cleanup, which is the production half of T24-D7.** The effect returns a function that marks the mount
as gone; the navigation is skipped when it has. The token is still held and the three items still consumed,
because those are module-level facts that the visitor's absence does not change — what must not happen is a
navigation yanked out from under someone who left. The existing `handled` ref is not the right instrument (it
guards against a *second* invocation, not a *departed* one), so this is a second, separate flag with its own
sentence of explanation.

**A probe first, not an assumption** (T24-D8's last clause): measure what `useLocation().pathname` yields inside a
router with `basename: '/app'`, so `rememberReturnTarget`'s documented contract and T41's handoff say the true
thing. If it yields `/app/home`, T41 must strip; if it yields `/home`, T41 passes it straight through. The probe
is three lines and it decides a sentence in two artifacts.

### 4 · The eleven inherited assertions (§2 of the frozen brief)

**Measured first.** Run the whole suite with steps 1–3 in place and **before editing any test**, and let it
produce the list. The frozen brief's table is the plan; the run is the authority — T23's inventory was right only
because it was measured that way, and Kimi's was not because it was not.

Then, per file, each with its reason restated in the style it was written in (T23-D8's discipline):

- `callbackValidatesNonce.test.tsx:593` — the case is **named** "holds on the callback screen when the nonce
  agrees". After T24 it does not hold; it leaves. Rename it and say what replaced the old claim.
- `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:838-845` — **must pass unchanged.** If it fails,
  the cleanup is wrong, not the case.
- `tokensAreNeverWrittenToAnyStorage.test.tsx:635-636` — the `code=`/`state=` control is what proves T22's URL
  sweep looks somewhere. It moves to **before** the navigation rather than being deleted, or the sweep goes
  inert — which is precisely the failure mode T22's own header warns about.
- `stateSurvivesRedirectAndIsConsumedOnce.test.tsx` — three route assertions become the post-login destination,
  and the key-set pin's **message** is rewritten to record T24-D4 instead of asking the question.

### 5 · Then the named test

`frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx`. Planned shape:

```
├── header: what is honoured, what is not, T24-D5's and D11's limits said out loud
├── harness: arriveAt() (real routes, stubbed fetch), startSignIn(), watchStorage(),
│            remember() via the real rememberReturnTarget
├── describe('an allowlisted target is honoured')        AC1, with the "would have stayed" control
├── describe('no target at all goes home')               AC3
├── describe('the target is single-use')                 AC7, read-before-delete, and D9's throwing removal
├── describe('the allowlist is derived, not written')    AC6, AC17, both directions
├── describe('no discard honours a target')              AC4, five shapes
├── describe('when the store will not cooperate')        AC10, AC15, D10's corrupted value
├── describe('a visitor who left is not navigated')      AC14, T24-D7
└── describe('a target outside the allowlist falls back to home (T25)')   it.todo rows only
```

## 6 · Verification

`tsc --noEmit`; the named file; the seven edited files; the whole suite **by exit code**; then a mutation harness
whose mutants are **the allowlist and the fallback**, not leaks: make the check a prefix test, make it a
`startsWith`, drop the `:` filter, return `null` instead of home, return the raw stored value unvalidated, delete
before reading, drop the guard, navigate on a discard, navigate after unmount, and remove the `replace`.

## 7 · What this plan does not do

No producer (T41's). No query or fragment handling (T24-D6). No pattern matching (T24-D5). No home screen
(T24-D11). No change to `authorizeRequest.ts` (T24-D4), the two pure readers (T23-D1), the holder, or
`.oxlintrc.json` (T22-D12). No new dependency and no catalogue key.

---

## Next — Phase 6 — implementation.
