<!-- MODEL: Claude Opus 5 — Phase 4 (Freeze Task) -->

# frontend · T41 · Phase 4 — Frozen Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Consumes** | `artifacts/02-task-implementation-brief.md` · `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` |
| **Approval** | Human Approval gate — standing owner authorization to run phases back-to-back, stopping only at the Kimi gates. No owner input is needed: every verdict below rests on a measurement taken in this phase or a citation checked against source. |

Phase 2's brief stands except where a verdict amends it. **Four candidate mechanisms were measured before
anything was frozen**, because F1 is right that the brief cannot be frozen without the shape — and reasoning
about React Router's behaviour has been wrong twice in this package already.

---

## 1 · The measurement that decides the mechanism

A scratch suite mounted each candidate under the real basename and read the router's own state back:

| candidate | destination | `historyAction` | runs / renders | under `StrictMode` |
|---|---|---|---|---|
| `loader` + `redirect('/sign-in')` | `/app/sign-in` ✓ | **`PUSH`** ✗ | loader ran **1×** | — |
| `loader` + `redirect('/app/sign-in')` | — | — | **infinite loop → V8 OOM crash** | — |
| `loader` + `replace('/sign-in')` | `/app/sign-in` ✓ | **`REPLACE`** ✓ | loader ran **1×** | **still 1×** |
| component + `<Navigate … replace />` | `/app/sign-in` ✓ | **`REPLACE`** ✓ | rendered **1×** | **2×** |

Three facts fall out of this that no amount of reading would have produced:

- **`redirect()` PUSHES.** A loader redirect built the obvious way fails **AC6** and leaves precisely the
  back-button trap `returnTargetOutsideAllowlistFallsBackToHome.test.tsx:599` was written to forbid. The
  router's `replace()` helper is the one that replaces.
- **`redirect('/app/sign-in')` double-applies the basename** — `/app/app/sign-in` matches nothing, the
  catch-all's loader fires again, and the process dies of memory exhaustion. The "one spelling" rule is not
  only tidiness here: spelling the basename into a redirect is a crash.
- **A loader runs once even under `StrictMode`; a component renders twice.** That is F4's whole concern,
  measured.

**Frozen: the component.** `auth/RedirectToSignIn.tsx`, one module, used in all three arrivals — Kimi's
**F1**/**F3**/**F5**/**F7** recommendation, adopted, for a reason the loader's own advantage argues against:

> A loader-based redirect carries **no `element`**, so it is invisible to both screen-count pins, to T37's
> AC9 **and** to T40's AC13. T40 closed a hole whose whole lesson was that *an element no pin can see is a
> hazard*. Buying a quieter diff by hiding the redirect where nothing looks would spend that lesson one task
> after learning it. The component is **visible**, the pins fire, and they grow deliberately.

The loader measurements are kept in §5 as a recorded trap, not as a rejected design.

### The frozen shape

```tsx
// auth/RedirectToSignIn.tsx
export function RedirectToSignIn(): ReactElement | null {
  if (Boolean(accessToken.current())) return null;            // F7: an authenticated visitor is outside R28
  rememberReturnTarget(<the arrival, basename-relative>);      // F8: the allowlist decides what is stored
  return <Navigate to={`/${SIGN_IN_PATH}`} replace />;         // F10, AC6, AC11
}
```

Used in three places, and **only** these:

| arrival | where | |
|---|---|---|
| **(a)** a blocked authenticated path | `RequireSession.tsx:42` — `null` becomes `<RedirectToSignIn />` | the gate keeps deciding; it delegates the *negative* branch |
| **(b)** a path matching nothing | `routes.tsx:78` — `{ path: '*', element: <RedirectToSignIn /> }` | the catch-all's own comment already assigned this to T41 |
| **(c)** the bare `/app/` | `routes.tsx` — a new `{ index: true, element: <RedirectToSignIn /> }`, first child of the root | **measured**, §3 |

`RequireSession` keeps its own session read and gains no path read, so **T40's 50-pattern scan on it stays
valid and untouched** — `<RedirectToSignIn />` trips none of its patterns, including `/\bredirect\s*\(/` and
`/<Navigate\b/`. That is **F3**, and it is why the division of labour is this way round rather than putting
`useLocation` in the guard.

## 2 · F2's arithmetic is wrong, and the instrument it missed is worse

Kimi: *"The count will go from three to **five**."* **Measured: three to four.** An element on an
`index: true` route is invisible to both walkers, because both record a path only when
`route.path !== undefined` and an index route has none:

```
elementWalk([{ path: '/', children: [{ index: true, element }, { path: 'sign-in', element }] }])  ->  []
pathTree(same)                                                                                   ->  [["/", [[null,[]], ["sign-in",[]]]]]
```

Only the **catch-all** redirect pushes the counts up, because only it has a path.

**And the instrument Kimi did not name is the one that matters.** An index route carrying an element is a
**pathless route carrying an element** — which is exactly what **T40's AC13** was written for, one task ago:

> *"exactly one pathless route in the whole tree carries an element, and it is the gate"*

T41 makes that **two**. AC13 fires, by design, on the first task after the one that wrote it, and it must
**grow to admit the second** — still forbidding a third, still forbidding `errorElement` and `Component` on
either. **This is the handoff working, and it is the single strongest argument for the component over the
loader**: the loader form would have slipped past AC13 in silence.

### Both of F2's remedies are refused

- **No `SCREEN_PATHS` constant in `routes.tsx`.** T40's phase 4 refused the same shape for the same reason:
  it moves a test's vocabulary into production so a test need not grow.
- **No excluding routes by element type** from `routesWithElements`. That pin currently sees **every**
  element; teaching it to ignore a class of them is how a pin stops being one. The walker keeps counting
  everything and the **expectation** grows, naming what is new — the resolution T40 used for `pathTree`.

## 3 · The index route, measured (F6)

| arrival | matches | |
|---|---|---|
| `/app/` | `['/', '(index)']` | ✓ **(c)** is handled |
| `/app` | `['/', '(index)']` | ✓ the no-slash form too, so no second mechanism is needed |
| `/app/sign-in` | `['/', 'sign-in']` | ✓ **the index route does not steal a public path** — F6's own question |
| `/app/nope` | `['/', '*']` | ✓ it does not steal the catch-all either |
| `/app/` **today** | `['/']` | which is what `routesResolveUnderAppPrefixOnly.test.tsx:132-142` pins, and why that pin — whose title says *"T41 owns its redirect"* — fires |

## 4 · Verdicts on all eighteen findings

Every citation checked. One line range is slightly off (**F3**: the table is `:311-380`, not `:311-355`);
one arithmetic claim is wrong (**F2**); everything else holds.

| | finding | verdict |
|---|---|---|
| **F1** | the shape is unspecified and the brief cannot be frozen without it | **ACCEPT** — frozen in §1, after measuring four candidates rather than three |
| **F2** | the redirects fire the screen-count pins | **ACCEPT the concern · REJECT both remedies · CORRECT the arithmetic** (§2). Three to **four**, and the instrument Kimi missed is T40's **AC13** |
| **F3** | `RequireSession` must not be the one to read the path | **ACCEPT** — this is why the component owns the redirect and the guard delegates. T40's scan on the guard stays **untouched** |
| **F4** | the return-target write is a render-phase side effect, doubled by `StrictMode` | **ACCEPT, measured** — the component renders **2×** under `StrictMode` (a loader would run 1×). `rememberReturnTarget` writes the **same value** twice, so **tests assert the stored value, never the call count**, and a `StrictMode` case pins it. `useLayoutEffect` is **refused**: it would order the write after the navigation decision and add a hook the source scan otherwise forbids |
| **F5** | one component for all three arrivals makes them identical | **ACCEPT** — and the frozen shape is why **L4** holds at the *mechanism* level, not only in the outcome |
| **F6** | the bare `/app/` needs an index route | **ACCEPT, measured** (§3), including both halves of its own question |
| **F7** | the catch-all must not redirect an authenticated visitor | **ACCEPT** — `null` when a token is held. R28 is conditioned *"WHILE a visitor is unauthenticated"*, so this is the line between what the spec asks for and what it does not say. **This closes phase 2's open question 1** |
| **F8** | the allowlist means (b) and (c) need no branch | **ACCEPT** — and `returnTarget.ts:76-78` goes further than the finding: *"Nothing in production calls this yet: **R28's redirect (task 41) is its caller**, and this exists so that task is a one-line call rather than a design (T24-D2)."* **T24 built this for T41 by name** |
| **F9** | parameterised paths are not remembered | **ACCEPT, and do not fix.** `returnTarget.ts:51-58` excludes a path whose *segment* begins with `:` (T24-D5), so `/invoices/:id` and `/receipts/:id` redirect and remember **nothing** — the visitor lands on home. Pinned as a boundary, not changed: `returnTarget.ts` is on the NOT-to-modify list and four closed tasks measure it |
| **F10** | use `replace` | **ACCEPT** — and §1 shows why it has to be stated: the loader form's obvious spelling **pushes** |
| **F11** | build the destination from `SIGN_IN_PATH`, read inside the function | **ACCEPT** — with a source scan. §1's OOM is the cost of the alternative |
| **F12** | the redirect renders no copy | **ACCEPT, no action** — `<Navigate>` renders nothing; AC10 already says so |
| **F13** | structure the new file so task 42 can extend it | **ACCEPT** — describes prefixed `T41` from the first case, which is the collision T40 found between its describes and T37's in one shared file |
| **F14** | public paths with a query string or trailing slash must not redirect | **ACCEPT** — boundary cases, `historyAction` included |
| **F15** | assert the return target is readable by the callback's own reader | **ACCEPT** — assert `sessionStorage[RETURN_TARGET_KEY]` **and** that `takeReturnTarget()` returns it, so the two ends of L1 meet in one case |
| **F16** | no new storage key | **ACCEPT, no action** |
| **F17** | the component must not import from `app/` beyond route constants | **ACCEPT** — source scan, both directions |
| **F18** | it must not read or assert account state | **ACCEPT** — `fetch`, `XMLHttpRequest`, `accounts/me` forbidden by scan. The gate claims only what it checks: a SUSPENDED account keeps a working token (task 31) |

**14 accepted as recommended · 4 accepted with the method or arithmetic amended (F2, F4, F9, F10) · 0 deferred ·
1 citation corrected · 1 open question closed (F7) · 1 instrument the findings missed (T40's AC13).**

## 5 · Recorded for whoever revisits this: the loader traps

Kept because the loader form is the natural "improvement" a later reader will reach for:

1. **`redirect()` pushes.** Use `replace()`. `historyAction` is the only thing that tells you, and no type
   error will.
2. **`redirect('/app/…')` double-applies the basename** and loops until the process dies. Loader redirect
   targets are basename-relative.
3. A loader-based redirect has **no element**, so it is invisible to `routesWithElements`, T37's AC9 and
   T40's AC13. If it is ever adopted, those three pins must gain a redirect-aware claim *first*, or the
   redirect becomes deletable with no failing test.

## 6 · Scope, files, criteria — final

Scope is phase 2's, unchanged, with phase 2's **open question 1 now closed by F7**: an authenticated visitor
at an unmatched path renders **nothing**, as today, because R28 does not speak about them.

**Create**

- `src/auth/RedirectToSignIn.tsx` — the redirect. One component, three call sites.
- `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` — the named test. **Task 42 extends it**;
  describes prefixed `T41`.

**Modify**

- `src/auth/RequireSession.tsx` — `null` → `<RedirectToSignIn />`. Nothing else; its own scan is untouched.
- `src/app/routes.tsx` — the index route and the catch-all's element.
- `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — the `/app/` match pin (`:132`), the catch-all silence
  pin (`:124`), `pathTree` (`:154`, the index route's level) and the screen-count expectation (`:175`,
  three → four, **naming the redirect as a redirect**).
- `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — T37's AC9 (`:663`), **T40's AC13** (one pathless
  element → two), and T40's blocked-path cases, which asserted blankness and `POP` and now assert the
  redirect. One of them names this task in its own title.

**NOT to modify**

`spec/**` · `auth/accessToken.ts` · **`auth/returnTarget.ts`** (it already does what is needed; four closed
tasks measure it) · `auth/cleanReauth.ts` · `auth/CallbackScreen.tsx`, `SignInScreen.tsx`,
`SignedOutScreen.tsx` · `app/mountShell.tsx`, `AppShell.tsx` · `i18n/catalogue.ts` · the two route arrays ·
T40's `GUARD_FORBIDDEN` and its cases other than those listed · every suite of tasks 21, 22, 24, 25, 35, 36,
39.

**Acceptance criteria:** phase 2's **AC1–AC13**, unchanged, plus one the measurement produced:

| | | ID |
|---|---|---|
| **AC14** | The arrival is read **basename-relative**. `useLocation().pathname` includes the `/app` prefix — the router's own state proves it (`path=/app/sign-in` in §1) — so a target handed to `rememberReturnTarget` with the prefix still on it would be rejected by the allowlist and **the deep link would be silently lost**. A case pins the stored value for a real authenticated path, not merely that *something* was stored. | **L1**, L14 |

## 7 · What downstream phases may not renegotiate

The component shape and its three call sites · `null` for an authenticated visitor · `<Navigate … replace />`
built from `SIGN_IN_PATH` read inside the function · `rememberReturnTarget` called unconditionally, with the
allowlist as the only filter · the index route for the bare `/app/` · **no** `SCREEN_PATHS`, **no**
element-type exclusion in `routesWithElements` · tests asserting the **stored value**, never the write count ·
`returnTarget.ts` untouched · T40's guard scan untouched.

**Phase 4 complete. STATUS: FROZEN.**
