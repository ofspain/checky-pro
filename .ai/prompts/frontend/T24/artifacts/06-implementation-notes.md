# frontend · T24 · Phase 6 — Implementation Notes

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | the production change, the inherited-assertion updates, this artifact |

`returnTarget.ts` (128 lines, ~30 of them code), one navigation and one cleanup in `CallbackScreen`, one constant
in `routes.tsx` — and **seventeen inherited assertions across five files**, updated in the same commit as the
change that makes them false. Suite **1008 of 1009** by exit code, `tsc --noEmit` exit 0.

The frozen brief predicted eleven inherited breakages. **The suite found seventeen.** The difference is the
substance of this phase, and §3 is where it is recorded, because two of the six surprises were production defects
rather than test updates.

---

## 1 · The probe, run before the code was written

T24-D8 required measuring rather than assuming what `useLocation()` yields inside a router with a basename,
because `rememberReturnTarget`'s contract and T41's handoff both depend on it. Three-line throwaway probe, run
and deleted:

```
useLocation=/callback | router.state=/app/callback | afterNavigate=/app/home
```

So: **`useLocation().pathname` strips the basename** (`/callback`), `router.state.location.pathname` keeps it
(`/app/callback`), and `navigate('/home')` lands on `/app/home`. The stored form T24-D8 froze — a router-relative
path with a leading slash — is therefore **exactly what the one API the future caller will have already yields**,
with no conversion. T41's handoff is now a sentence with a measurement behind it: *pass
`useLocation().pathname` straight to `rememberReturnTarget`.*

It also explains a long-standing asymmetry in this package's tests: they assert `/app/...` because they read
`router.state.location`, not `useLocation()`.

## 2 · What was built

**`auth/returnTarget.ts`** — `RETURN_TARGET_KEY`, `rememberReturnTarget(target: string)`,
`takeReturnTarget(): string`, `isAllowlistedTarget(target: string)`, and two private derivations
(`allowlistedTargets()`, `homeTarget()`).

- **Set membership, nothing else** (T24-D3, D8). No prefix test, no pattern match, no normalisation, no decoding.
  `/app/home`, `/app/homeward`, `//evil.example`, a scheme-qualified address, `javascript:`, `\\evil`,
  `/home/../admin` and `/home?x=1` are each wrong in a different way, and membership refuses all of them for the
  same reason — which is why there is nothing to strip and nothing to get wrong.
- **`takeReturnTarget()` returns a destination, never an option.** One place the fallback can be wrong instead of
  one per call site.
- **Read before delete by construction** (AC7): the value is in hand before the removal, so no arrangement of
  these statements deletes before reading.
- **Total** (T24-D10): the whole body is guarded, and so is the removal separately (T24-D9). An uncaught throw
  here would blank the page at the end of a successful sign-in — `CallbackScreen.tsx` passes `discard` as
  `.then`'s *second* argument, which does not see a synchronous throw from the first.
- **Everything from `routes.tsx` is read inside a function.** This closes the **third strand** of the import
  cycle (`routes → CallbackScreen → returnTarget → routes`). Measured in T16: Vite yields `undefined` rather than
  throwing, and `undefined` here is the worst case, because an empty allowlist **fails closed silently** — every
  target rejected, the SPA apparently working, nothing ever honoured again.

**`auth/CallbackScreen.tsx`** — `if (onScreen.current) navigate(takeReturnTarget(), { replace: true });` after
`consumeAuthorizeState()`, plus the `onScreen` ref and the effect cleanup that clears it.

**`app/routes.tsx`** — `HOME_PATH = 'home'`, used in `AUTHENTICATED_ROUTE_PATHS`, so the home route has one
spelling like `SIGN_IN_PATH` and `CALLBACK_PATH`.

## 3 · Six breakages the frozen brief did not predict — two were defects

Measured by running the whole suite with the production change in place and **before editing any test**, exactly
as the plan required. First run: **20 failures**. The predicted eleven were all there; these six were not, and
they are the reason the plan insisted on measuring rather than trusting the table.

| # | What broke | Diagnosis |
|---|---|---|
| 1 | `bundleContainsNoSecretEnvKeys` — "names no absolute URL anywhere in the app source (L9, L10, R58)" | **A defect in my own doc comment**: it used `https://evil.example` as an example of a rejected target. That rule covers comments, and it is right to. Rewritten without a scheme, with a line saying why. |
| 2–4 | T22's success sweep, its refresh-token case, and `removes exactly the three keys, once each` | **A production defect**: `takeReturnTarget` removed the key **unconditionally**, so every successful callback made a **fourth** `removeItem` for a value that was never written. Three inherited assertions about exactly which keys a sign-in removes were right to object. Now the removal happens only when something was read — which is also the honest reading of "single-use": there is nothing to consume when nothing was stored. |
| 5 | T18's `flips from discard to exchange…` — **a second it.each row** | The brief listed the case, not its two rows. |
| 6 | T22's four console cases — `expect(loggedText(logs)).toBe('')` | **T24-D11, made visible.** React Router logs *"Matched leaf route at location "/home" does not have an element or Component"* on every successful sign-in, because T24 is the first task to navigate **into the authenticated route set** and that set has no screens. T22's absolute-silence instrument caught it. |

**On #6, the fix was a choice and it is worth stating.** Silencing the warning with a no-op element on `home`
would hide a true statement about an incomplete route table from whoever builds that screen. Weakening T22's
assertion to a substring search would blunt a leak instrument. So the warning is **matched exactly** and stripped
by a named helper, `loggedUnexpected`, and every call site keeps asserting **absolute silence for anything else**:
a new console call of any kind still fails. It disappears on its own as screens land (**T24-D13**).

**On #2–4, the production fix is strictly better than the original**, which is the useful kind of test failure:
the three cases were not obstacles, they were correct.

## 4 · The seventeen inherited assertions

| File | Cases | What moved |
|---|---|---|
| `stateSurvivesRedirectAndIsConsumedOnce` (T23) | 3 + the pin's message | `CALLBACK_ROUTE` → `POST_LOGIN_ROUTE = '/app/home'`, with the constant's doc rewritten. **The key-set pin now records an answer instead of asking a question** (T24-D4): the three are deleted for having been consumed by the *exchange*, the return target for having been read by the *navigation* — different consumers, different moments, so each belongs to the module owning its reader |
| `tokensAreNeverWrittenToAnyStorage` (T22) | 7 | the four console assertions → `loggedUnexpected`; the AC5 URL control; the storage allowlist's **second** row; four storage files → five |
| `callbackValidatesNonceAndKeepsAccessTokenInMemory` (T21) | 2 | both route assertions; the StrictMode one carries the `onScreen` ordering reason |
| `callbackValidatesNonce` (T19) | 2, one **renamed** | "holds on the callback screen when the nonce agrees" → "leaves the callback screen for the post-login route…". The property is unchanged — the destination is still what tells the decision apart — but the name said "holds", and after T24 it does not |
| `callbackWithMismatchedStateIsDiscarded` (T18) | 2 cases, 3 rows | "and the reached callback stays put" → "…goes on to the post-login route, not back to sign-in" |

**The AC5 URL control became stronger, not weaker.** T22 proved `routerLocation` was a live surface by requiring
the callback query to be *in* it. After T24 it holds the post-login route, which proves the same thing better: it
is not the initial entry the harness supplied, so the sweep is reading something that moved, and the query is gone
because the SPA navigated rather than because nothing was read.

## 5 · One inherited assertion that did NOT break, which is how T24-D7 is verified

`callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:838-845` — *"survives the screen unmounting while the
exchange is still in flight"*, whose comment reads *"a visitor who leaves the callback screen mid-exchange gets no
navigation yanked out from under them"*. T21 wrote that intention before there was any navigation to break, and
T24 is the first task that could break it. **It passes unchanged**, which is the evidence the `onScreen` ref is
right — and it was the only reason the ref was written at all.

One ordering detail inside that ref, found by reasoning and confirmed by the suite: `onScreen.current = true` is
set on **every** invocation of the effect, before the `handled` latch, because StrictMode runs the cleanup
*between* the two passes. Left to its initial value, a StrictMode sign-in would never navigate — in development
only, with every other assertion green.

## 6 · Verification

- `tsc --noEmit` → exit **0**.
- `vitest run` → exit **1**, `Tests 1 failed | 1008 passed (1009)`; the single failure is the known environmental
  `oxlint`-summary case (`lintRejectsHandWrittenBackendFetch.test.ts:118`). Read by **exit code**.
- The named test does not exist yet — Phase 10's. Nothing above is a substitute for it.

## 7 · What Phase 10 owns

`returnTargetOutsideAllowlistFallsBackToHome.test.tsx`, T24's half, plus `it.todo` rows for T25's rejection
families (F7). Nothing yet proves: an **honoured** target (every case above signs in without remembering one, so
the whole allowlist-admits path is unexercised); the derivation in both directions (AC6, AC17); the corrupted
value and the throwing store (AC15); the throwing removal (AC16); `rememberReturnTarget` round-tripping; that no
discard honours a target (AC4); and the module-scope scan that guards the third strand of the import cycle.

## 8 · Carry-forwards

**Opened here:** `/app/home` has no element and no task builds one, so the destination of every fallback is a
blank route that logs a router warning (**T24-D11**, **T24-D13**). **T24-D5** still costs the notification-email
deep links. **T41** gets the capture call, with the probe's measurement behind it.

**Unchanged:** the composition root is wired by nothing; silent re-authentication has no task; no task owns a
real-browser check; `oidcRoundTripEndToEnd` has no Playwright suite; CI enforces neither the suite total nor the
identity of the `oxlint` failure; `contracts/api/token-claims.md` is false on email/name; `design.md` §4c's
heading is stale; `package.md:172`'s mapping is wrong; `expires_in` is dropped (T21-D2).
