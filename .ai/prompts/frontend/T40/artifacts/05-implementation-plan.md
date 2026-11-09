<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T40 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-freeze-task.md` (STATUS: FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code written. Every file below traces to the frozen brief's **Files to create / modify**; nothing else is
touched.

---

## 1 · Files to create

### `src/auth/RequireSession.tsx`

The guard. One component, no state, no effects, no navigation, no storage.

```tsx
export function RequireSession(): ReactElement | null
```

- Returns `<Outlet />` when `Boolean(accessToken.current())`, `null` otherwise. Frozen condition — F12, on
  the measurement that `accessToken.ts:90` is `held = token;` with no empty-string guard.
- Imports exactly `Outlet` from `react-router` and `accessToken` from `./accessToken`. Nothing else, because
  **AC7** scans this file's source and every other import is a surface.
- Header comment carries three things the source scan cannot say: that the redirect is **task 41's** and why
  rendering nothing is the established behaviour here; that a held token does **not** prove the account is
  usable (a SUSPENDED account keeps a working token — task 31), so the guard claims only what it checks; and
  **OQ-T40-1**, that the read is not reactive and why a subscription is not this task's to add.

**No other module is created.** No barrel, no `index.ts`, no shared helper.

## 2 · Files to modify

### `src/app/routes.tsx`

One structural change, in `appRoutes`:

```
root '/'
├── ...PUBLIC_ROUTE_PATHS      nine, three with elements           unchanged
├── { element: <RequireSession />, children: AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })) }   NEW
└── { path: '*' }                                                   unchanged, still a sibling
```

- The eleven `{ path }` entries move from the root's children into the guard route's children. The `.map()`
  that produces them is unchanged; only where its result is spliced changes.
- The guard route is **pathless** — no `path` key at all, not `path: ''` (which would be an index-like
  match and would appear in `pathTree` as `['', …]` rather than `[undefined, …]`, and would change leaf
  resolution for the bare `/app/`, which `routesResolveUnderAppPrefixOnly.test.tsx:132` pins).
- `import { RequireSession } from '../auth/RequireSession';` joins the three screen imports. Direction
  `app/` → `auth/` is already established (`routes.tsx:6-8`) — **AC6**.
- The comment at `:26` ("T40 enforces the split") stops being a promise; it gains one line saying where the
  split is and that the catch-all is deliberately outside it.
- `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` keep their exact contents and order. No
  `SCREEN_PATHS`, no `GUARDED_PATHS` — frozen, F3/F4 refused.

### `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — the one instrument that fires

`pathTree(appRoutes)` at `:154` currently expects twenty flat leaves plus the catch-all. The new level makes
it fail, because `pathTree` maps `route.path`, which is `undefined` for a pathless route. **AC11** requires
it grow to admit the level **explicitly**, not be loosened:

```ts
expect(pathTree(appRoutes)).toEqual([['/', [
  ...PUBLIC_ROUTE_PATHS.map((path) => [path, []]),
  [undefined, AUTHENTICATED_ROUTE_PATHS.map((path) => [path, []])],
  ['*', []],
]]]);
```

Three properties are kept by writing it this way rather than relaxing the assertion: the **order** of the
root's children, the fact that the authenticated paths are **nowhere else**, and that the guard level's path
is **`undefined`** rather than a string. `DECLARED_PATHS` stays as it is — the other four cases that use it
are unaffected.

Also at `:161`, a **comment only**: `routesWithElements` keeps its three, and the reason it does — the
`route.path !== undefined` filter — is now load-bearing, so the line points at the named suite's AC13, which
is what pins it. `SCREENS_ATTACHED_SO_FAR` is **not** touched.

Nothing else in that file changes. Checked, per instrument:

| case | why it is unaffected |
|---|---|
| `:96` every declared route resolves to its own leaf | the pathless route is an intermediate match; `matches[last].route.path` is still the authenticated path |
| `:102` emailed link with a query string | public paths, untouched |
| `:111` nothing resolves without `/app` | prefix, not structure |
| `:117` no backend edge prefix resolves | ditto |
| `:124` unmatched `/app` path → catch-all | the catch-all is still a sibling of the guard |
| `:132` bare `/app/` matches only the root | depends on the guard being pathless, not `path: ''` |
| `:140-153` exactly 20 §4c paths | the arrays are unchanged; only this case's last line (`:154`, `pathTree`) fires |
| `:161` a screen only where its task has run | the guard carries no `path`, so it is not counted |
| `:173-178` `createAppRouter` | takes `appRoutes` whole, and compares `pathTree(router.routes)` to `pathTree(appRoutes)` — both sides move together |

### `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — the named test (phase 10)

Extended, not replaced. Its header already reserves this: *"Task 40 (to be added here): that these nine
paths and only these nine are reachable without a session."* Planned additions in §4.

## 3 · Files NOT to modify

`spec/**` · `auth/accessToken.ts` and the rest of the session owner · the three screens · `app/mountShell.tsx`,
`app/AppShell.tsx` · the catch-all's own definition · `i18n/catalogue.ts` · every suite of tasks 21, 22, 35,
36, 39 · T37's AC9 count at `:429` and task 16's `SCREENS_ATTACHED_SO_FAR` at `:17-19`, **both of which stay
at three** (frozen §6).

## 4 · Tests required

All in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, added below the existing cases.

### 4.1 The instrument: one derived table, probes on every declared path

```ts
function withProbes(guard: ReactElement): RouteObject[]
```

Copies `appRoutes`, replaces the element of **every** declared path with a distinct probe
(`<i data-probe={path} />` rendering the path as text), and replaces the pathless route's element with
`guard`. It **asserts its own baseline** before returning — exactly one pathless child under the root,
exactly 20 probed paths plus the catch-all — so a table that silently probed nothing cannot produce a green
arm. (A harness that does not assert its baseline measures nothing; T29's and T39's harnesses both learned
this.)

```ts
async function arriveWithTable(routes: RouteObject[], location: string, options?: { strict?: boolean })
```

Mounts a `createMemoryRouter` over the given table at the given `/app` location, **without** `AppShell`:
the shell is not part of the route table (`AppShell.tsx` is a bare `<div>` that `renderAt` in the sibling
suite wraps around the provider), so the container **is** the outlet's subtree and `container.innerHTML ===
''` is the exact assertion **AC8** wants, with no scoping needed. Wrapped in `I18nextProvider` so a public
arm that renders a real screen is not a second variable.

### 4.2 The three arms — AC2, AC3, AC4, and AC1's dual

| case | arm | asserts | AC |
|---|---|---|---|
| each of the eleven authenticated paths serves nothing with no session | real guard, no token | per path: `container.innerHTML === ''` | **AC2**, **AC8** |
| …and the same under StrictMode | real guard, no token, `strict: true` | same for one path | **AC2** (F9) |
| …and an empty-string token is not a session | `retainAccessToken('')`, real guard | same for one path | **AC2** (F12) |
| each of the eleven is served **with** a session | real guard, token held | per path: the probe's text renders | **AC4** |
| **CONTROL:** removing the guard turns the AC2 case red | pass-through `() => <Outlet />`, no token | per path: the probe's text renders | **AC3** |
| each of the nine public paths is served with no session | real guard, no token | per path: the probe's text renders | **AC1**, F16's behavioural half |
| the catch-all is served with no session | real guard, no token | the catch-all probe renders | **AC1**, F14's behavioural half |

The AC3 control and the AC2 case differ in exactly one value — the element handed to `withProbes` — which is
what makes "no content" a measurement rather than an absence.

### 4.3 Structure — AC12, AC13

| case | asserts |
|---|---|
| the guard's reach is exact | the root's children are nine public paths, then **one pathless route**, then the catch-all; the pathless route's `element.type === RequireSession`; its children's paths `toEqual(AUTHENTICATED_ROUTE_PATHS)`; the nine public paths and `'*'` are **siblings**, not descendants — all derived from the exported arrays, nothing re-spelled |
| no element hides where the count pins cannot see it | walk the whole tree for routes with an element and **no path**; expect exactly one, and `.type === RequireSession`. With a control: a planted pathless route carrying an element is counted, and a planted pathless route with none is not |

### 4.4 The guard's source — AC7, AC6

| case | asserts |
|---|---|
| the guard reaches for nothing it should not | `guardSource()` (comment-stripped, a second reader beside the existing `source()` rather than a rewrite of it — eleven call sites, and duplication is cheaper than the abstraction here) scanned against `GUARD_FORBIDDEN`: storage, cookies, IndexedDB, `fetch`/XHR/`sendBeacon`, `console.`, timers, every state/effect hook (`useState`, `useEffect`, `useLayoutEffect`, `useRef`, `useMemo`, `useCallback`, `useReducer`, `useSyncExternalStore`), navigation (`useNavigate`, `useHref`, `<Navigate`, `redirect`, `location.assign`/`.replace`/`.href`), and route/query reads (`useLocation`, `useParams`, `useSearchParams`, `URLSearchParams`, `globalThis.location`, `window.location`). **The token rows are deliberately absent** — reading the session is this module's whole job — and `retainAccessToken`/`clearAccessToken`/`idToken` are forbidden instead, so the guard can read but not write. |
| **CONTROL:** every pattern in the sweep fires on a planted reach | each row carries its own snippet, as T37's AC7 does, so a typo in any pattern fails the control rather than passing silently |
| the guard imports from `auth/`, and `app/` never the reverse | `RequireSession.tsx` holds no `from '../app/`; `routes.tsx` imports it |

### 4.5 Boundaries — AC1, AC9, AC10

| case | asserts |
|---|---|
| a public path with a trailing slash | `/app/verify/` — **measured, then pinned to what it does** (F15). Not asserted from belief |
| a public path with a query string | already built at `routesResolveUnderAppPrefixOnly.test.tsx:102-109`; cited, not duplicated |
| a sessionless visit to a public path performs no navigation | the router's `location.pathname` is unchanged after mount, and `history.length` / `location.assign` are untouched — **AC10** |
| adding a path to one list without the other fails a test | already pinned at `routesResolveUnderAppPrefixOnly.test.tsx:141-148` (both arrays asserted literally) **and** newly by AC12's children assertion; cited, not duplicated — **AC9** |
| the two sets are disjoint and total twenty, derived | already at `:149`; AC12 adds that the guard's children are exactly one of the two — **AC5** |

## 5 · Public methods · private methods · entities · repositories · services

| | |
|---|---|
| **Public** | `RequireSession(): ReactElement \| null` — the only new export in `src/` |
| **Private** | none. The guard has no helpers |
| **Entities** | none |
| **Repositories** | none |
| **Services** | `accessToken.current()` (`auth/accessToken.ts`) — read only. No endpoint, no contract, no config key, no catalogue entry, no storage key |

## 6 · Execution order

The frontend has no schema or DAO layer, so the front-loading rule maps onto *the thing every later step
depends on* — the route table's shape.

1. **`src/auth/RequireSession.tsx`** — the guard, with its header comment. Nothing depends on it yet, so it
   compiles alone.
2. **`src/app/routes.tsx`** — nest the eleven under the pathless guard route. This is the moment the suite
   goes red, by exactly one assertion.
3. **`src/app/routesResolveUnderAppPrefixOnly.test.tsx:154`** — grow `pathTree`'s expectation; add the
   `:161` pointer comment. Suite green again, and the *only* change to that file is the assertion the
   structural change made fire.
4. **Gates:** `tsc --noEmit`, `oxlint src`, then the whole suite — the three screens' suites and tasks 21,
   22, 35, 36, 37, 39 all mount the real table, so a regression shows up there, not here.
5. **Phase 10** — the named suite's extension in §4, written against the finished table: the instrument
   (4.1), the three arms (4.2), the structural pins (4.3), the source scan and its control (4.4), the
   boundaries (4.5).
6. **Phase 7's harness** — mutants on the guard's condition, its `null`, the nesting (flatten it back), the
   guard route gaining a `path`, the catch-all moved inside it, a public path moved inside it, the
   pass-through control itself, and the two count-pin filters. Each must kill with a **named** case.

## 7 · Risks carried into phase 6

| | |
|---|---|
| **A pathless route changes match chains, not leaves** | `matchRoutes` returns one more element for an authenticated path. Only `:96` reads the chain's last entry, and that is still the path — but `routesResolveUnderAppPrefixOnly.test.tsx:127` asserts a full chain (`['/', '*']`) for the catch-all, which is outside the guard, so it holds. Verified by reading, to be verified by running |
| **Tasks 21/22/35/36/37/39 mount the real table** | none of them mounts an authenticated path — there is nothing there to mount — so the nesting should be invisible to them. Step 4 is what proves it |
| **`Outlet` with no children** | an authenticated path with a session renders the guard's `<Outlet />` over a `{ path }` route with no element; React Router renders its own implicit `<Outlet />`, i.e. nothing. The **probe** is what gives AC4 something to see; without it AC4 would be as vacuous as AC2 is today |

**Phase 5 complete.** One file created, two modified in phase 6, one extended in phase 10. No code written.
