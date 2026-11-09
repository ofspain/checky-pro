<!-- MODEL: Claude Opus 5 — Phase 4 (Freeze Task) -->

# frontend · T40 · Phase 4 — Frozen Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Consumes** | `artifacts/02-task-implementation-brief.md` · `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` |
| **Approval** | Human Approval gate — standing owner authorization to run phases back-to-back, stopping only at the Kimi gates (3, 8, 11) or for genuine owner input. No owner input is needed: phase 3 raised no question only the owner can answer, and every finding below is resolved against measured source. |

Phase 2's brief stands as written except where a verdict below amends it. Three amendments change the
*shape* of the work (**F1**, **F2**, **F3/F4**); the rest add criteria or add nothing.

---

## 1 · The one decision phase 3 forced: the guard's shape

Kimi **F1** is right that the brief cannot be frozen without it, and right about the component. It is
wrong — or rather silent — about how the component is *applied*, and that is the decision that changes
every instrument in the task.

**Frozen:** `auth/RequireSession.tsx` exports `RequireSession`, a function component that renders
`<Outlet />` when `Boolean(accessToken.current())` and `null` otherwise. It is applied **once**, as a
**pathless layout route** whose `children` are exactly `AUTHENTICATED_ROUTE_PATHS`:

```tsx
{ element: <RequireSession />, children: AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })) }
```

**Not** eleven `{ path, element: <RequireSession /> }` entries. The two shapes were measured against the
existing instruments and the handoff pattern, and they are not equivalent:

| | pathless layout route (**frozen**) | an element per authenticated path (**rejected**) |
|---|---|---|
| guard instances | one | eleven |
| where task 42 attaches a screen | as a child of the guard — `{ path: 'home', element: <HomeScreen /> }` | nowhere: the path's element slot is already taken by the guard |
| `routesWithElements(appRoutes)` (`routesResolveUnderAppPrefixOnly.test.tsx:161`) | **three**, unchanged | **fourteen** — the screen-count handoff is destroyed on day one |
| T37's AC9 attached list (`publicRouteSetIsExactlyTheDeclaredSet.test.tsx:420`) | **three**, unchanged | **fourteen** |
| `pathTree(appRoutes)` (`routesResolveUnderAppPrefixOnly.test.tsx:155`) | **fires** — a level appears | unchanged |
| leaf resolution, `leafPath()` (`:96`) | unchanged — the pathless route is an intermediate match, the authenticated path is still the leaf | unchanged |

The rejected shape also breaks the thing task 42 exists to do, and it would force a restructure *by*
task 42 of the route table T40 had just written.

One consequence of the frozen shape is a **new hole that T40 itself opens**, and closing it is T40's job,
not a later task's — see **AC13**.

---

## 2 · Verdicts on all eighteen phase-3 findings

Every citation was checked against source before the verdict. Two of Kimi's are wrong, both in **F3**.

### F1 — guard shape unspecified · **ACCEPT, amended**
The component and its `null`/`<Outlet />` contract are taken verbatim. The application shape is amended to
one pathless layout route, for the reasons in §1. Folded into **Files to create**, **AC12**, **AC13**.

### F2 — AC3's test design is the real content · **ACCEPT the demand, REJECT the method**
Kimi's control — *"replace `RequireSession` with a component that always renders `<Outlet />`, mount an
authenticated path with no session, and assert content is rendered"* — **cannot pass as written**, and the
reason is the same fact AC3 was written about: **eleven of eleven authenticated routes are `{ path }` with
no element** (`routes.tsx:66`). A pass-through guard over a route with nothing under it renders nothing.
The control would assert content and find none, in both arms, for a reason that has nothing to do with the
guard.

**Frozen method.** The suite derives a route table from `appRoutes` by finding the single pathless route
and attaching a **probe element** to its children, then mounts the same authenticated path three ways:

| arm | guard | session | expected |
|---|---|---|---|
| **production** | the real `RequireSession` | none | the probe does **not** render — AC2 |
| **permitted** | the real `RequireSession` | a held token | the probe **renders** — AC4 |
| **control** | a pass-through rendering `<Outlet />` unconditionally | none | the probe **renders** — **AC3** |

The control arm is what makes AC2 falsifiable: the production arm's "nothing rendered" is measured against
a mount of the same table, at the same path, with the same probe, that *did* render. The difference between
arm 1 and arm 3 is the guard and nothing else.

Deriving the table rather than hand-writing one is deliberate: the derivation can only find a probe site if
the authenticated paths really are children of a pathless route, so the method is also a structural proof
of **AC12**. A probe is attached to **all eleven**, so AC2 is measured per path, not for one representative.

### F3 — T37's AC9 count pin will fire · **ACCEPT the concern, REJECT both remedies. Citation wrong.**
- **The citation is wrong.** Kimi cites `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:316-348`; those
  lines are AC5's scan forbidding a `navigate()` hand-over in `SignedOutScreen.tsx` (`:316-318`) and AC6's
  query-string indifference cases (`:321-348`) — nothing in that range counts a route or an element. The AC9 pin is
  **`:409-440`**, asserting `['sign-in', 'callback', SIGNED_OUT_PATH]` at `:429`.
- **The prediction is wrong for the frozen shape.** Both walkers record a path only when it has one:

  ```ts
  return [...(attached && route.path !== undefined ? [route.path] : []), ...routesWithElements(route.children ?? [])];
  ```

  A pathless route carrying the guard is invisible to that filter, so the count stays **three**. It would
  have counted fourteen only under the shape §1 rejects.
- Both remedies are therefore refused: **no `SCREEN_PATHS` constant in `routes.tsx`**, and **no eleven-count
  guard pin**. Neither count has to grow, and growing one would have cost the handoff Kimi was trying to
  protect.
- **The concern survives the remedies, inverted.** The same filter that saves the counts makes them *blind*
  to any element on a pathless route — a blindness T40 creates. **AC13** closes it.

### F4 — the sibling instrument needs a guard-aware distinction · **ACCEPT the citation, REJECT the remedy**
`SCREENS_ATTACHED_SO_FAR = ['sign-in', 'callback', 'signed-out']` is at `:17-19`, as cited, and Kimi's
instinct to keep it at three is correct. The remedy is refused for the same measurement as F3: **no
`GUARDED_PATHS` array**, and no count assertion changes. What *does* grow in that file is the instrument
Kimi did not name — `pathTree`'s tree-shape assertion at `:155` — see **AC11**.

### F5 — "nothing" needs a precise assertion · **ACCEPT, narrowed**
The guard returns `null`. The blocked-route assertion is `textContent === ''` **and** no element inside the
route's outlet. Kimi's `container.querySelector('*') === null` is refused as written: `AppShell` renders
landmark chrome around the outlet, so the whole container is never empty. The emptiness claim is scoped to
the outlet's own subtree. Folded into **AC8**.

### F6 — the guard stores, writes and sends nothing · **ACCEPT**
A source scan mirroring T37's AC7 `FORBIDDEN_REACHES`, with its control. Folded into **AC7**.

### F7 — no state or effect hooks · **ACCEPT**
`useState`, `useEffect`, `useLayoutEffect`, `useRef`, `useMemo`, `useCallback`, `useReducer`,
`useSyncExternalStore` added to the forbidden list. The guard is a synchronous read; see **OQ-T40-1** for
what that costs and why it is still right.

### F8 — no navigation · **ACCEPT**
`useNavigate`, `useHref`, `Navigate`, `redirect`, `location.assign`, `location.replace`, `location.href`
forbidden in the guard's source. R28's second clause is task 41's, and the scan is what keeps T40 from
quietly taking it. Folded into **AC7**/**AC10**.

### F9 — test under StrictMode · **ACCEPT**
The production arm of §2 F2's table is mounted a second time under `StrictMode`. Folded into **AC2**.

### F10 — the sets are already disjoint · **ACCEPT, no change**
AC5 stands; counts stay derived from the exported arrays.

### F11 — `app/` → `auth/` is the correct direction · **noted, no action**
Already **AC6**. `routes.tsx:6-8` already imports three screens from `auth/`.

### F12 — empty string is not a session · **ACCEPT**
Frozen as `Boolean(accessToken.current())`. Kimi's own preference, and the measurement backs it:
`accessToken.ts:90` is `held = token;` with **no empty-string guard**, so `retainAccessToken('')` leaves
`current()` returning `''`. `!= null` would call that a session. Folded into **AC2**, with a case.

### F13 — AC2 is vacuously true today · **ACCEPT**
This is AC3's premise, restated. Closed by F2's frozen method.

### F14 — the catch-all must remain unchanged · **ACCEPT, strengthened**
Kimi's remedy — assert the catch-all has no element — is too weak under the frozen shape: a catch-all
*moved under the guard* would still have no element, and `:129`'s existing case expects `shellText === ''`
there anyway, so it would pass while silently guarded. The claim frozen instead is **structural**: the
catch-all is a **sibling** of the guard route, not a descendant. Folded into **AC12**.

### F15 — query strings and trailing slashes · **ACCEPT, mostly already built**
The query-string boundary already exists and already asserts the query survives
(`routesResolveUnderAppPrefixOnly.test.tsx:102-109`, `verify?token=abc` and `reset?token=abc`), and the
bare `/app/` case exists at `:132`. T40 adds the one that does not: a **trailing slash on a public path**,
measured and documented rather than asserted from belief. Folded into **AC1**.

### F16 — the guard must not be applied to public routes or the catch-all · **ACCEPT, method replaced**
Kimi's remedy — assert `PUBLIC_ROUTE_PATHS` and the catch-all do not have `RequireSession` as their element
— measures nothing under the frozen shape, where no path has the guard as its element. The claim is
structural, and it is one claim with F14's: the guard route's children are **exactly**
`AUTHENTICATED_ROUTE_PATHS`, and the nine public paths plus the catch-all are its siblings, all derived from
the exported arrays. Folded into **AC12**; **AC1** is the behavioural half.

### F17 — no catalogue entry · **noted, no action**
Already **AC8** and the "No copy" constraint.

### F18 — the guard must not read the route or the query · **ACCEPT**
`useLocation`, `useParams`, `useSearchParams`, `URLSearchParams`, `globalThis.location`, `window.location`
added to the forbidden list. Folded into **AC7**.

**Summary: 14 accepted** (4 with the method or scope amended) · **4 remedies refused with measured reasons**
(F3's two, F4's, and the methods inside F5/F14/F16) · **0 deferred** · **1 finding's citation corrected**
(F3) · **1 new criterion the findings produced rather than named** (AC13).

---

## 3 · Scope — final

Unchanged from phase 2. **In:** the guard under `auth/`; `routes.tsx` applying it; extending the named test;
growing the sibling instruments T40 makes fire. **Out:** the redirect and its return target (task 41) · any
screen, and the authenticated shell (task 42) · silent re-authentication (unbuilt, no task) · changing the
catch-all's own definition · `spec/**`.

## 4 · Files — final

**Create**

- `src/auth/RequireSession.tsx` — the guard. One component, `<Outlet />` or `null`.

**Modify**

- `src/app/routes.tsx` — nest `AUTHENTICATED_ROUTE_PATHS` under one pathless guard route; the comment at
  `:26` ("T40 enforces the split") becomes true.
- `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — the named test. **Extend, do not replace.**
- `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — **only** the `pathTree` expectation at `:155`, which
  the new level makes fire, plus a note at `:161` pointing at AC13. `SCREENS_ATTACHED_SO_FAR` is **not**
  touched.

**NOT to modify**

`spec/**` · `auth/accessToken.ts` and the rest of the session owner · `SignInScreen.tsx`,
`CallbackScreen.tsx`, `SignedOutScreen.tsx` · `app/mountShell.tsx`, `AppShell.tsx` · the catch-all's own
definition · the i18n catalogue · every suite of tasks 21, 22, 35, 36, 37, 39 unless an instrument of theirs
fires. **T37's `SCREENS_ATTACHED_SO_FAR`-equivalent count at `:429` is not retired** — see §6.

## 5 · Acceptance criteria — final

AC1–AC11 are phase 2's, amended where a verdict says so. AC12 and AC13 are new.

| | | ID |
|---|---|---|
| **AC1** | Each of the nine public paths resolves and renders **without a session** — including with a query string, and with a trailing slash (F15). | R28, L14 |
| **AC2** | None of the eleven authenticated paths serves content **without a session**, measured per path with a probe attached (F2 arm 1), and again under `StrictMode` (F9). An empty-string token is **not** a session (F12). | R28, L14 |
| **AC3** | **AC2 turns red when the guard is removed**: the control arm, same table, same path, same probe, a pass-through guard, no session — the probe renders (F2 arm 3). | R28 |
| **AC4** | **With** a session, the probe renders (F2 arm 2). The guard is a split, not a deny. | R28 |
| **AC5** | The two sets are disjoint and together exactly §4c's declared set; counts **derived from `routes.tsx`**. | L14 |
| **AC6** | The guard lives in `auth/`; `app/` imports it and never the reverse. | design.md:203 |
| **AC7** | The guard stores nothing, writes nothing, sends nothing, navigates nothing, holds no state and reads no route or query — a source scan with a control that fires every pattern on a planted reach (F6, F7, F8, F18). | L16, L3 |
| **AC8** | A blocked route renders **no text at all**: the outlet's subtree is empty, scoped past the shell's chrome (F5). | L11 |
| **AC9** | Adding a path to one list without the other **fails a test**. | L14 |
| **AC10** | A sessionless visit to a public path performs **no navigation**: "served", not "bounced and returned". | R28 |
| **AC11** | Instruments in the sibling suite are **grown, not bypassed**: `pathTree`'s assertion at `:155` grows to admit the guard's level explicitly, rather than being loosened or deleted. | the handoff pattern |
| **AC12** | **The guard's reach is exact.** One pathless route carries it; its children are **exactly** `AUTHENTICATED_ROUTE_PATHS`; the nine public paths and the catch-all are its **siblings**, not its descendants. Derived from the exported arrays (F1, F14, F16). | R28, L14 |
| **AC13** | **No element hides where the count pins cannot see it.** Exactly one pathless route in the whole tree carries an element, and it is the guard — so the screen-count pins' `route.path !== undefined` filter, which T40 relies on to keep its three, cannot be used to smuggle a screen past them (F3, inverted). | the handoff pattern |

## 6 · T37's handoff instruction — examined, and declined

T37's AC9 comment addresses this task directly:

> *"task 40 extends THIS file with the exhaustive public-route claim, and this case is the seed it grows
> from — the total expressed in the route table's own vocabulary. The day task 40 writes that claim, the pin
> to retire is task 16's narrowing one, not this one."*

The first half is honoured: the named file is where AC12 and AC13 are written, and T37's AC9 is the case
they grow from. The retirement is **declined**, for the reason T37's own comment supplies three lines
earlier: task 16's pin *"compares to the LITERAL `'signed-out'`, so it also fails when the published
constant is renamed, while this one spells both sides with `SIGNED_OUT_PATH` and is blind to a rename by
construction."* Retiring task 16's pin would retire the only one of the two that catches a rename, and T40
is not the task that earned that trade. Both stay at three; neither is loosened; the new dimension
(**AC13**) is written once, in the named file, and the sibling gets a pointer rather than a third copy.

## 7 · Open questions — all resolved or deferred with an owner

| | | owner |
|---|---|---|
| **1 — AC3's measurement** | **RESOLVED** in §2 F2. The method is frozen: derived table, probe, three arms. | closed |
| **2 — every reload lands on the gate** | **DEFERRED, not a defect in T40.** Tokens are memory-only and `accessToken.renew()` always rejects (`accessToken.ts:67`); nothing restores a session across a reload. Spec-consistent per L3's Q5 resolution. | **task 42** |
| **3 — spec drift at `design.md:137`** | **DEFERRED, recorded only.** `:137` still reads "PROPOSED … pending author confirmation (Q6)" while `package.md:368` records Q6 resolved 2026-10-10. `spec/` is not modified by this task. | **owner** |
| **4 — §4c must be assembled** | **RESOLVED** in phase 1: the bullet at `design.md:138-140` plus the 2026-10-19 note; the four reserved names are out of this revision. | closed |
| **OQ-T40-1 — the guard is not reactive** *(new, from F7)* | **ACCEPTED as frozen behaviour, with the evidence.** The session owner publishes no subscription: `accessToken.ts` exports `current`, `adopt`, `renew`, `retainAccessToken`, `clearAccessToken` and nothing else, and the only `useSyncExternalStore` in `src/` is `RateLimitNotice.tsx:14`. The guard therefore reads at render, and a token arriving **with no navigation** would leave a mounted blocked route blocked. Nothing in `src/` does that — the callback screen navigates after retaining — so the case is unreachable today. Adding a subscription would be a new surface on the session owner, which L1–L3 and the Files-NOT-to-modify list both forbid this task. | **noted; task 42 if a screen ever mounts across a sign-in** |

## 8 · What downstream phases may not renegotiate

The guard's name, module and `<Outlet />`/`null` contract · its application as **one pathless layout
route** · `Boolean(accessToken.current())` as the condition · the three-arm probe method for AC2/AC3/AC4 ·
the screen-count pins staying at **three** in both files · **no** `SCREEN_PATHS`, `GUARDED_PATHS` or
eleven-count pin · T37's AC9 and task 16's pin both surviving · the redirect staying task 41's.

**Phase 4 complete. STATUS: FROZEN.**
