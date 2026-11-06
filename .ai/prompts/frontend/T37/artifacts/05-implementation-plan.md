<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T37 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/04-freeze-task.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

## Files to create

| File | Contents |
|---|---|
| `frontend/src/auth/SignedOutScreen.tsx` | one component: `useTranslation()`, three strings, one `Link`. No props, no state, no handler, no effect. |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | T37's clause; header names task 40's. |

## Files to modify

| File | Change |
|---|---|
| `app/routes.tsx` | `export const SIGNED_OUT_PATH = 'signed-out';`, used in `PUBLIC_ROUTE_PATHS` and in the element mapping |
| `i18n/catalogue.ts` | a `signedOut` section **after** `callback` |
| `auth/endSession.ts` | delete the private const; add `SIGNED_OUT_PATH` to the existing `../app/routes` import |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` → three, marked `TASK 37` |

## Public signatures

```tsx
// auth/SignedOutScreen.tsx
export function SignedOutScreen(): ReactElement;

// app/routes.tsx — additive
export const SIGNED_OUT_PATH = 'signed-out';
```

**One export each, and no more.** The screen takes no props: there is nothing a caller could configure that
would not be a claim about session state, which AC13 forbids.

## The component

```tsx
export function SignedOutScreen() {
  const { t } = useTranslation();
  return (
    <main>
      <h1>{t('signedOut.heading')}</h1>
      <p>{t('signedOut.explanation')}</p>
      <Link to={`/${SIGN_IN_PATH}`}>{t('signedOut.action')}</Link>
    </main>
  );
}
```

Four decisions inside eight lines:

- **`SIGN_IN_PATH` is read inside the body**, not at module scope — the cycle discipline
  `CallbackScreen.tsx:99` already follows (F4). The import at module scope is fine; ES bindings are hoisted.
  A source scan pins the read site.
- **`<main>`**, because this is a whole screen and the SPA has no layout chrome yet. `SignInScreen` and
  `CallbackScreen` should be checked in phase 6 for what they use, and this should match them rather than
  introduce a second convention; **if they use a bare fragment, this will too**, and the landmark becomes
  task 173's.
- **`<h1>`** — one per screen, and this screen is the whole document.
- **No `useEffect`, no `useState`, no `useNavigate`.** The emptiness is AC7's deliverable.

## The catalogue entry

Placed after `callback`, following the file's own rule — *"in the order a visitor meets them, not
alphabetical"* — and a visitor meets this screen last of the three. Carries the same kind of comment the
neighbours do, because the reason the words are these words is not obvious from the words.

## `routes.tsx`

```tsx
export const SIGNED_OUT_PATH = 'signed-out';
// …
if (path === SIGNED_OUT_PATH) return { path, element: <SignedOutScreen /> };
```

`PUBLIC_ROUTE_PATHS` keeps its order and its nine values — the literal at index 8 becomes the constant, so
`routesResolveUnderAppPrefixOnly.test.tsx:138`'s "exactly the 20 §4c paths" assertion sees identical strings.
**Checked, not assumed.**

## Tests

`publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, mounting the **real route table** — the technique at
`callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:205`:

```tsx
const router = createMemoryRouter(routes.appRoutes, { basename: APP_BASENAME, initialEntries: [`/${SIGNED_OUT_PATH}`] });
```

Mounting the real table rather than the component alone is what makes AC1 and AC2 real: it proves the route
*resolves* to this screen, not merely that the screen renders when handed to React.

| AC | Cases |
|---|---|
| AC1 | the route resolves and the heading renders |
| AC2 | with no token and empty `sessionStorage`; and a case asserting storage is still empty afterwards |
| AC3 | every rendered string traced to its catalogue key; a scan for user-facing literals in the source, with a control |
| AC4 | the three strings name no cause — a scan for a forbidden vocabulary (`locked`, `suspended`, `deleted`, `expired`, `invalid`, `password`, `revoked`), with a control |
| AC5 | exactly one `role="link"`, `href` ends `/app/sign-in`, **and zero buttons and no second link** |
| AC6 | four query strings render byte-identical output; plus the `useSearchParams`/`useLocation`/`location.search` scan |
| AC7 | storage spy sees nothing; `fetch` spy sees nothing; no timer; a source scan for `fetch`/`XMLHttpRequest`/`sessionStorage`/`localStorage`/`setTimeout` |
| AC8 | a token is held before mounting and **still held after** — the screen clears nothing |
| AC9 | `routesWithElements(appRoutes)` is exactly `['sign-in', 'callback', 'signed-out']` |
| AC10 | `PUBLIC_ROUTE_PATHS` unchanged at nine; no new storage key; no catalogue key outside `signedOut` |
| AC11 | the rendered text contains no `@`, no token-shaped string, no account identifier |
| AC12 | direct arrival with nothing in storage renders and the router's location stays `/signed-out` |
| AC13 | rendered HTML byte-identical with and without a token; `SIGN_IN_PATH` read only inside the body (scan + control) |

**Controls for every scan**, each firing on a planted positive — the house rule since T29.

## Execution order

1. `i18n/catalogue.ts` — the words first, so nothing is written against a placeholder.
2. `auth/SignedOutScreen.tsx`.
3. `app/routes.tsx` — publish the constant and attach. **Run `routesResolveUnderAppPrefixOnly`**: it must go
   red on `SCREENS_ATTACHED_SO_FAR`. A green run there would mean task 16's pin is weaker than its artifact
   claims, which is a finding about T16 rather than a convenience.
4. Extend `SCREENS_ATTACHED_SO_FAR`; that suite green again.
5. `auth/endSession.ts` — the F5 edit. **Run T36's suite**; it must stay green, since the string is identical.
6. The new test file, criterion by criterion.
7. `tsc --noEmit`, `oxlint src`, whole suite.
8. **Commit the tests**, then the mutation harness. Never the other way round — that cost edits twice in T36.

## Risks

- **Step 3's expected red is this plan's one claim about task 16.** Recorded now so a green run is reported
  rather than quietly accepted.
- **`Link` outside a router throws.** Every case must mount through `createMemoryRouter`; a component-only
  render would fail for a reason unrelated to the criterion.
- **`t()` returns the key when a namespace is missing**, so a case asserting `t('signedOut.heading')`'s output
  could pass against a missing catalogue entry by comparing a key to a key. The cases assert the **literal
  English text** and separately that it equals `en.signedOut.heading`, so a missing entry fails.
- **AC13's byte-identical comparison must capture the same subtree both times**, not the whole document, since
  a router's wrapper markup could differ between mounts. Phase 6 should compare the `<main>` element's
  `innerHTML`.
- **The query-string cases depend on the basename.** `createMemoryRouter`'s `initialEntries` are basename-
  relative; a `?state=` appended to the wrong side of the basename resolves to no route and the case would
  pass vacuously on an empty render. Each query case asserts the heading is present as well as identical.

**Phase 5 complete.**
