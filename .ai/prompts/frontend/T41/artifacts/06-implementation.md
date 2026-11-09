<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T41 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Consumes** | `artifacts/04-freeze-task.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

## Gates

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **49 / 49** |
| `vitest run src/app/routesResolveUnderAppPrefixOnly.test.tsx` | **10 / 10** (was 9; one case added) |
| `vitest run` | **1745 / 1746** — the single failure is the known host-environmental
`src/api/lintRejectsHandWrittenBackendFetch.test.ts` case (this host's `oxlint@1.87.0` prints no summary line) |

## What was written

### `src/auth/RedirectToSignIn.tsx` — new, 75 lines

```tsx
export function RedirectToSignIn(): ReactElement | null {
  const hasSession = Boolean(accessToken.current());
  if (hasSession) return null;
  const { pathname } = useLocation();
  const base = APP_BASENAME.endsWith('/') ? APP_BASENAME.slice(0, -1) : APP_BASENAME;
  const arrival = pathname.startsWith(`${base}/`) ? pathname.slice(base.length) : pathname;
  rememberReturnTarget(arrival);
  return <Navigate to={`/${SIGN_IN_PATH}`} replace />;
}
```

One export, as planned. Five things the source cannot say live in the header:

- **Why a component and not a `loader`.** Phase 4 measured four mechanisms. A loader is technically cleaner —
  it redirects before a render — but a loader has **no `element`**, so it would be invisible to
  `routesWithElements`, to T37's AC9, **and to T40's AC13**, which T40 wrote one task ago precisely because an
  element on a pathless route is invisible to the count pins. Choosing the loader would have spent T40's
  lesson the week it was learned.
- **The three loader traps**, kept in the header so the next task does not re-measure them:
  `redirect('/x')` **pushes**; `redirect('/app/x')` **double-applies the basename** and loops until V8 runs out
  of heap; only `replace('/x')` both replaces and runs once under `StrictMode`.
- **Why the session read comes first.** R28 is conditioned *while unauthenticated*; an authenticated visitor on
  an unmatched path is outside it, so the component returns `null` and today's blank page is preserved (F7).
- **Why there is no branch per arrival.** All three arrivals call `rememberReturnTarget` with the
  basename-relative path, and the **allowlist** inside `returnTarget.ts` is the only filter (F8). `/no-such-route`,
  `/` and `/invoices/123` are refused by it, not by an `if` here.
- **Why the basename is stripped.** `useLocation().pathname` **includes** the basename (phase 4), and the
  allowlist is built from `AUTHENTICATED_ROUTE_PATHS`, which does not. Without the strip every arrival would be
  refused and every deep link silently lost — which is AC14, and the mutant phase 7 must write for it.

### `src/auth/RequireSession.tsx` — one line, and a paragraph that had to be rewritten

`:47` is now `return hasSession ? <Outlet /> : <RedirectToSignIn />;`. T40's 50-pattern source scan on this
file **passes unchanged**: `<RedirectToSignIn />` trips no row — not `/\bredirect\s*\(/`, not `/<Navigate\b/`,
not `/\buseLocation\b/` — because the arrival is read one component down. That was Kimi's F3 and it holds.

The header's **"IT RENDERS NOTHING RATHER THAN REDIRECTING"** paragraph became false the moment that line
changed, and was rewritten rather than deleted, as T40's phase 9 did for two of task 37's statements. It now
says what it used to claim, that task 41 built it, and which three decisions moved one component down.

### `src/app/routes.tsx` — two elements

```tsx
{ index: true, element: <RedirectToSignIn /> },   // arrival (c): the bare /app/ and /app
…
{ path: '*', element: <RedirectToSignIn /> },     // arrival (b): anything unmatched
```

The index route is the root's **first** child. Phase 4 measured that an `index` route matches both `/app/` and
`/app`, and steals neither `sign-in` nor the catch-all. The catch-all's comment — *"T41 replaces it with the
sign-in redirect"* — came true and was rewritten to say why the catch-all remains a **sibling** of the gate: a
catch-all behind the gate could not redirect the only visitor it exists for.

## The deviation: seven predicted, twenty-six fired

Phase 5 named **seven** expected failures and warned that *"the risk is not that one fires unexpectedly but
that one fires and is **loosened** instead of grown."* The real number was **twenty-six**, measured by
restoring the four test files to `HEAD` with the production change in place and running the whole suite:

| file | failures | predicted? |
|---|---|---|
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | 16 | 7 of them, as two groups |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | 4 | yes, all four |
| `auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | 4 | **no** |
| `auth/callbackValidatesNonce.test.tsx` | 2 | **no** |

**D1 — one instrument's own baseline took down nine arms that had nothing wrong with them.**
`withProbes` identified the gate as *the pathless child* (`child.path === undefined`). Task 41 added a second
pathless child, so the derived table found **two** gates, failed its own `expect(gatesFound).toBe(1)` baseline,
and every arm built on it failed — including T40's AC1 (the nine public paths), AC4 (served with a session), the
AC3 pass-through control and both AC1 boundary cases, which phase 5 had named as *the boundary of this task:
they must pass unchanged*. They do pass unchanged; they were never wrong. The fix is in the instrument: the gate
is the pathless child **with children**, task 41's index route is a pathless **leaf**, and the baseline now
asserts one of each. S2 of T40's phase-11 review praised `withProbes` for asserting its own baseline, and that
is exactly what made the blast radius visible in one run instead of nine debugging sessions — but a baseline
that cannot distinguish two shapes it will have to coexist with converts one real failure into nine false ones.

**D2 — the six callback failures were a collapsed discriminator, not a route problem.** Tasks 5, 6 and 23's
callback seams resolved `{ idToken }` only, so `retainAccessToken(undefined)` ran and no session was held.
Before T41 that was invisible: the test navigated to `/app/home`, which rendered nothing either way. After T41
`/app/home` bounces to `/app/sign-in` — and those suites use *home vs sign-in* as their "exchanged vs discarded"
discriminator, one case asserting `.not.toBe('/app/sign-in')` outright. The fix is in the **seam**, not the
assertion: both now return an `accessToken`, which is what a real token endpoint returns and what
`CallbackScreen.tsx:170` retains. Six cases went green with no claim weakened.

The honest reading of 7 → 26: phase 5 enumerated the instruments that **name** the route table and missed the
ones that merely **arrive** at a path. T40's phase 5 made the same class of error at a smaller scale (predicted
two, got three, recorded as its D1) and the correction then was *run the whole suite rather than reasoning about
it*. That correction was applied here — the suite is what produced this table — but it was applied **after** the
prediction rather than to it.

## The instruments grown, and what each became

| instrument | was | is |
|---|---|---|
| `routesResolve…:124` | *"sends an unmatched /app path to the **silent** catch-all"*, `matches === ['/', '*']`, `shellText === ''` | *"…through the catch-all to the sign-in start"*: pathname `/app/sign-in`, `REPLACE`, `matches === ['/', 'sign-in']`, no error, and the text is **not** React Router's own 404 |
| `…:132` | *"treats the bare /app/ as no route … (T41 owns its redirect)"* | *"sends the bare /app/ through its index route to the sign-in start"*. The pin that the **root still matches** is kept, because the index route is a child of it — a redirect that had replaced the root would gate the public nine too, which is T40's RS13 shape |
| **new** | — | the same arrival with **no trailing slash**, `/app`, which phase 4 measured as matching the same index route |
| `…:159` `pathTree` | `[['/', [nine…, [undefined, eleven…], '*']]]` | an explicit `[undefined, []]` **before** the nine, named rather than loosened |
| `…:175` `routesWithElements` | `[...SCREENS_ATTACHED_SO_FAR]` (3) | `[...SCREENS_ATTACHED_SO_FAR, ...REDIRECTS_ATTACHED_SO_FAR]` (4). A new constant holds `'*'`, so `SCREENS_ATTACHED_SO_FAR` keeps meaning what its name says and task 42 extends the **screen** list |
| T37's **AC9** (`:663`) | `['sign-in', 'callback', 'signed-out']` | `[…, '*']`, with the fourth **named as a redirect** in the comment. Up by one, not three, because the walker records only routes with a `path` — which is the hole AC13 exists for |
| T40's **AC13** | *exactly one* pathless route carries an element, and it is the gate | **exactly two**, `[RedirectToSignIn, RequireSession]`, with `errorElement` and `Component` still forbidden on **both**, so a third still fails. **This is the first thing AC13 ever caught — one task after T40 wrote it, and the instrument Kimi's phase-3 challenge missed** |
| T40's **AC12** | `children.filter(path === undefined)` → length 1 | filter **narrowed** to *pathless with children*; a separate assertion pins the total pathless count at exactly 2. Narrowed rather than re-counted, because a length of 2 would pass whether the second route were an index redirect or a second gate |
| T40's three **AC2** arms | `container.innerHTML === ''` | `probeIn(container) === SIGN_IN_PATH` and the pathname is `/app/sign-in`. The surviving claim is the one that mattered: **the authenticated path's own probe never renders**. `probeIn` names the route, so the arm cannot pass on the wrong screen appearing — and emptiness could never tell a block from a crash |
| T40's **AC10** blocked arm | *"leaves the location … rather than redirecting (R28, **task 41**)"*, `POP` | *"**REPLACES** the location … sending the visitor to the sign-in start"*. Inverted by its own parenthesis. Still an exact action and an exact destination; `REPLACE` not `PUSH` is the half that matters, since a pushed redirect leaves the blocked path on the back stack. The **public** arm is untouched, and is what would fail if the redirect leaked |
| T40's **AC1 boundary** | blocked with `?foo=bar` and a trailing slash → empty | → the sign-in probe |

Eleven instruments, three of them task 16's, two task 37's, five task 40's, and one new case. None was deleted
and none was weakened: every grown assertion names the new state instead of admitting any state.

## Files NOT modified, as planned

`spec/**` · `auth/returnTarget.ts` · `auth/accessToken.ts` · `auth/cleanReauth.ts` · the three screens ·
`app/mountShell.tsx`, `AppShell.tsx` · `i18n/catalogue.ts` · the two route arrays · T40's `GUARD_FORBIDDEN`
(50 rows, untouched, and it passes against the delegating guard) · every suite of tasks 21, 22, 24, 25, 33, 35,
36, 39.

Two files beyond the plan were touched, both in §2's spirit and both forced by a measurement rather than a
preference: the two callback seams of D2. The plan's §3 list does not name them, and neither does it name their
suites' assertions, which are unchanged.

## What phase 7 must mutate

The session read (inverted, `!= null`, removed) · the `null` arm replaced by an unconditional redirect, which is
F7 · `replace` dropped · the destination hardcoded, or spelled with the basename (phase 4's OOM) ·
**the basename strip removed — AC14, the deep link silently lost** · `rememberReturnTarget` removed · a branch
added where the allowlist belongs · the index route removed · the catch-all's element removed · the redirect
applied to a public path (T40's RS13 shape) · the guard's delegation reverted to `null` · and each of the eleven
grown instruments reverted, since an instrument that was grown without being needed is a pin that measures
nothing.

Nothing in `src/` claims T41's behaviour in its own file yet; the eleven grown instruments are task 16's, 37's
and 40's. That is the expected shape at phase 6, and phase 7's harness is what will say which mutants have
nothing but an inherited instrument to kill them — the measurement that gave T40 five scheduled survivors.

**Phase 6 complete.** One file created, two modified as planned, two more forced by a measurement; eleven
instruments grown; 7 predicted failures against 26 real ones, recorded as D1 and D2.
