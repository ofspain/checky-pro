<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T41 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-freeze-task.md` (STATUS: FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code written. Every file traces to the frozen brief's Files sections.

---

## 1 · Files to create

### `src/auth/RedirectToSignIn.tsx`

```tsx
export function RedirectToSignIn(): ReactElement | null
```

Four statements, in this order, and the order is load-bearing:

1. **`if (Boolean(accessToken.current())) return null;`** — F7. R28 is conditioned *"WHILE a visitor is
   unauthenticated"*, so an authenticated visitor at an unmatched path keeps today's behaviour. This is also
   what makes the component safe at **all three** call sites: for arrival (a) the gate has already asked, and
   asking twice costs one property read and buys one component instead of two.
2. **the arrival, basename-relative** — **AC14**. `useLocation().pathname` carries the `/app` prefix (phase 4
   measured `path=/app/sign-in` in the router's own state), so the prefix is stripped here. `cleanReauth.ts`'s
   `inAppPath()` does the same job from `globalThis.location`, which a memory-router test cannot drive, and
   `cleanReauth.ts` is on the NOT-to-modify list — so this is **four lines duplicated deliberately**, the
   second instance of this shape. A third would justify extracting it; two do not (T39 F12 settled the same
   trade).
3. **`rememberReturnTarget(arrival)`** — unconditionally, with the allowlist as the only filter (F8). No
   branch for (b) or (c); no branch for a parameterised path either (F9 — `/invoices/123` redirects and
   remembers nothing, by `returnTarget.ts:55-57`).
4. **`return <Navigate to={`/${SIGN_IN_PATH}`} replace />;`** — F10, F11, AC6, AC11. `SIGN_IN_PATH` read
   **inside** the function; phase 4's OOM is what the module-scope or basename-spelled alternative costs.

Imports: `useLocation`, `Navigate` (`react-router`) · `accessToken` (`./accessToken`) ·
`rememberReturnTarget` (`./returnTarget`) · `APP_BASENAME`, `SIGN_IN_PATH` (`../app/routes`) ·
`type ReactElement` (`react`). Nothing else — the source scan is what holds that.

Header comment carries what the source cannot: that **`StrictMode` calls the write twice with the same
value**, measured, so a test asserting a call count would be asserting React's internals; that
`rememberReturnTarget` already applies the allowlist, so the three arrivals differ only in what the allowlist
keeps; that a held token does **not** prove the account usable (task 31); and the **three loader traps** from
the frozen brief §5, because this is the module a later reader would "improve" into a loader.

### `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx`

The named test. Describes prefixed **`T41`** from the first case (F13), since task 42 extends this file and
T40 found its own describes colliding with T37's in a shared file.

## 2 · Files to modify

| | |
|---|---|
| `src/auth/RequireSession.tsx` | `:42` — `null` becomes `<RedirectToSignIn />`. **Nothing else.** T40's 50-pattern scan on this file stays untouched, because `<RedirectToSignIn />` trips none of its rows — not `/\bredirect\s*\(/`, not `/<Navigate\b/`, not `/\buseLocation\b/`. Its header's *"IT RENDERS NOTHING RATHER THAN REDIRECTING"* paragraph becomes **false** and is rewritten to say what changed, as T40's phase 9 did for two statements of task 37's |
| `src/app/routes.tsx` | `{ index: true, element: <RedirectToSignIn /> }` as the root's **first** child, and `element: <RedirectToSignIn />` on the catch-all at `:78`. The catch-all's comment — *"T41 replaces it with the sign-in redirect"* — comes true and is rewritten to say why the catch-all is still **outside** the gate |

### The five instruments that fire, each with what it becomes

| | instrument | fires because | grows to |
|---|---|---|---|
| `routesResolveUnderAppPrefixOnly.test.tsx:124` | *"sends an unmatched /app path to the **silent** catch-all"* — asserts `matches === ['/', '*']` and `shellText === ''` | the catch-all now redirects | the match chain is `['/', 'sign-in']` after the redirect and the sign-in screen renders. The case's **title** stops being true and is rewritten |
| `…:132` | *"treats the bare /app/ as no route … **(T41 owns its redirect)**"* — asserts `matches === ['/']` | the index route matches too | `['/', 'sign-in']` after the redirect. The title's own parenthesis is the handoff; it is honoured here |
| `…:159` | `pathTree` | an index route is a node with `path: undefined` | an explicit `[undefined, []]` entry **before** the nine public leaves, named rather than loosened — the resolution T40 used for the gate's level |
| `…:175` | `routesWithElements` → `['sign-in','callback','signed-out']` | the catch-all has a path **and** now an element | four entries, with the fourth **named as a redirect, not a screen**. `SCREENS_ATTACHED_SO_FAR` stays at three and a separate constant holds the redirect |
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:663` | T37's AC9, *"exactly three screens are attached"* | same | same shape; its describe title becomes false and is rewritten |

### And the two of T40's that fire hardest

| | |
|---|---|
| **T40's AC13** (`…:1016`) | *"exactly one pathless route carries an element, and it is the gate"* → **two**: the gate and the index redirect. Still exactly two, still forbidding `errorElement`/`Component` on either, still naming which is which. **This is the instrument the phase-3 challenge missed**, and it fires one task after T40 wrote it |
| **T40's blocked-path cases** (`…:906`, `:913`, `:921`, `:988`, `:990`, `:1120`) | they assert `innerHTML === ''` and `historyAction === 'POP'` on a blocked path, and one says so in its title: *"rather than redirecting (R28, **task 41**)"*. Each becomes a claim about the **redirect**: the sign-in route renders and the action is `REPLACE`. T40's AC3 control arm (pass-through gate) is **unaffected** — a pass-through has no redirect in it |

T40's AC4 (served with a session), AC1 (the nine public paths), AC12 (the gate's reach) and AC7 (the guard's
source scan) must all **keep passing unchanged**. If any of them moves, T41 has overreached.

## 3 · Files NOT to modify

`spec/**` · `auth/accessToken.ts` · **`auth/returnTarget.ts`** · `auth/cleanReauth.ts` ·
`auth/CallbackScreen.tsx`, `SignInScreen.tsx`, `SignedOutScreen.tsx` · `app/mountShell.tsx`, `AppShell.tsx` ·
`i18n/catalogue.ts` · the two route arrays · T40's `GUARD_FORBIDDEN` and every T40 case not listed in §2 ·
every suite of tasks 21, 22, 24, 25, 33, 35, 36, 39.

## 4 · Tests required

In `unauthenticatedRoutesRedirectToSignIn.test.tsx`. The instrument is simpler than T40's: the **real** route
table is mounted, because after T41 a blocked path *does* render something — the sign-in screen — so no probe
is needed to give the arms something to see.

| | case | AC | kills |
|---|---|---|---|
| **(a)** | each of the eleven authenticated paths, no session → lands on `/app/sign-in`, `REPLACE`, the sign-in screen rendered | AC1, AC6 | the redirect removed; `replace` dropped |
| **(b)** | an unmatched path, and one *resembling* a public path (`/app/sign-in-x`) | AC2 | the catch-all's element removed |
| **(c)** | the bare `/app/` **and** `/app` | AC3 | the index route removed |
| | the nine public paths → served, **not** redirected, `POP`, the route's own screen | AC4 | the redirect applied too widely — T40's RS13 shape |
| | with a session, an authenticated path is **not** redirected | AC5 | the session read inverted |
| | **with a session, an unmatched path renders nothing** — today's behaviour kept | F7, R28's WHILE | `null` replaced by an unconditional redirect |
| | the return target after (a) for a **non**-parameterised path: `sessionStorage[RETURN_TARGET_KEY]` **and** `takeReturnTarget()` both give `/account` | AC7, **AC14**, F15 | the basename left on the arrival — the deep link silently lost |
| | after (b), after (c), and after a **parameterised** path (`/invoices/123`): nothing stored | AC7, F9 | a branch added where the allowlist belongs |
| | `StrictMode`: the **stored value** is right after a double render, and the destination and action are unchanged | F4 | — (and a case asserting a write *count* would be asserting React's internals) |
| | the three arrivals are **indistinguishable**: same destination, same action, byte-identical rendered output | AC9, L4 | one arrival given different treatment |
| | a public path with a query string (query preserved) and with a trailing slash | AC4, F14 | — |
| | the component's source: `fetch`, `XMLHttpRequest`, `accounts/me`, storage, `console`, timers, state hooks, no `../app/` import beyond the route constants; `SIGN_IN_PATH` read **inside** the function | AC10, AC11, F17, F18 | a side effect or a module-scope read added |
| | **CONTROL:** every pattern in that sweep fires against the real source plus its probe, the real source trips none, the length is pinned, no two rows share a pattern | — | a typo in any pattern |
| | no catalogue entry added; the redirect renders no copy of its own | AC10 | — |

## 5 · Public / private / entities / repositories / services

| | |
|---|---|
| **Public** | `RedirectToSignIn(): ReactElement \| null` — the only new export |
| **Private** | the basename strip, four lines, local to the module |
| **Entities · repositories** | none |
| **Services** | `accessToken.current()` (read) · `rememberReturnTarget` (write, allowlisted, into the **existing** `RETURN_TARGET_KEY`) · `useLocation`, `Navigate` (`react-router`). No endpoint, contract, config key, catalogue entry or new storage key |

## 6 · Execution order

1. **`src/auth/RedirectToSignIn.tsx`** — compiles alone; nothing imports it yet.
2. **`src/auth/RequireSession.tsx`** — the delegation, and the header paragraph that is now false.
3. **`src/app/routes.tsx`** — the index route and the catch-all's element. **This is the moment the suite goes
   red**, and the plan expects it to go red in **seven** named places (§2).
4. **The five firing instruments**, in the order of §2's table, each grown to name what is new rather than
   loosened.
5. **T40's blocked-path cases and AC13** — the two that fire hardest, and the ones to read most carefully:
   AC13 must still forbid a **third** pathless element after admitting the second.
6. **Gates:** `tsc --noEmit`, `oxlint src`, whole suite. Tasks 16, 22, 24, 25, 33, 35, 36, 37, 39 and 40 all
   mount the real table, so an overreach shows up there.
7. **Phase 7's harness** — mutants on: the session read (inverted, `!= null`, removed), the `null` arm
   replaced by an unconditional redirect, `replace` dropped, the destination hardcoded or basename-spelled,
   the basename strip removed (AC14's mutant — the deep link lost), `rememberReturnTarget` removed, a branch
   added for (b)/(c), the index route removed, the catch-all's element removed, the redirect applied to a
   public path, and each of the five grown instruments reverted.

## 7 · Risks carried into phase 6

| | |
|---|---|
| **Seven instruments firing at once** | phase 5 of T40 predicted *two* and got *three*; here the count is seven and they are enumerated with what each becomes. The risk is not that one fires unexpectedly but that one fires and is **loosened** instead of grown. Step 4 is where that is decided, case by case |
| **`useLocation` inside a redirect** | the component is only ever rendered when it is about to navigate away, so the hook's value is read once per arrival. If React were to re-render it before the navigation commits, the write would repeat with the same value — measured as harmless under `StrictMode`, which is the double-render case |
| **An index route changes what `/app` matches** | measured in phase 4: `/app` and `/app/` both match it, `sign-in` and the catch-all are untouched. The pin at `:132` is what will prove it in the real table rather than a scratch one |
| **Over-reach into T40** | T40's AC1/AC4/AC7/AC12 and its AC3 control must pass **unchanged**. They are the boundary of this task: if the redirect leaks onto a public path or into the guard's own source, those four fail |

**Phase 5 complete.** Two files created, two modified, five instruments grown, seven expected failures named
in advance. No code written.
