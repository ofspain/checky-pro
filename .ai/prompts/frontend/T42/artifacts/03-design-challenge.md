<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T42 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T42 — Authenticated shell |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — OQ-T42-1 must be resolved now; the only defensible reading is option (b)
· **Severity:** **Blocker**
· **Evidence:** `artifacts/02-task-implementation-brief.md §Open Questions` lists three readings: (a) navigation chrome, (b) minimal landmark wrapper, (c) vacuous restatement of T40/T174. No requirement ID commissions authenticated navigation, and `package.md:151` maps the named test to R28 alone.
· **Assessment:** Option (a) would add links to ten routes that have no screen, creating dead-end navigation and implicitly forcing a sign-out affordance decision (OQ-T42-2). Option (c) fails AC7, AC8 and AC9 because the layout must render something, be a render site, and wrap the outlet. Option (b) satisfies every AC without overreach: it gives tasks 43 onward a place to nest, it renders through primitives, and it leaves navigation/sign-out for their owning tasks.
· **Recommended brief amendment:** State explicitly: "The authenticated layout renders a minimal header/banner area and an `<Outlet />`. It contains no navigation links and no sign-out control. Its only purpose is to be the structural shell that later screens nest inside."

### F2 — AC9's "landmark distinct from `<main>`" conflicts with AC7 and the Out list
· **Severity:** **Blocker/Major**
· **Evidence:** AC7 requires the layout to render through `app/ui/` primitives only and bear no lowercase JSX tag. The eight primitives are `ActionButton`, `FocusHolder`, `Heading`, `Screen`, `StatusRegion`, `Surface`, `Text`, `TextLink` (`frontend/src/app/ui/index.ts`). None renders `<header>`, `<nav>`, `<aside>` or `<footer>`. The Out list forbids touching `app/ui/`.
· **Assessment:** A layout that must be a landmark and may only use existing primitives has no valid primitive to render it. `Screen` is `<main>` and cannot be used (nested `<main>`). `Surface` is a `<div>`. `StatusRegion` is a live region, not a content landmark. `Text` is a `<p>`. This is a three-way contradiction: AC9 vs. AC7 vs. the Out list.
· **Recommended brief amendment:** Choose ONE of the following and record it:
  1. **Relax AC9** to "structural wrapper distinct from the screen's `<main>`" and render the layout through `Surface` (or just `Outlet` inside a `Surface` shell). This keeps the Out list intact.
  2. **Authorize a scope change** to add a `Banner` primitive (renders `<header>` or `<div role="banner">`) and grow T173's pinned primitive list from 8 to 9, the union from 14 to 16, and the bearer count from 7 to 8.
  3. **Carve out an exception** to AC7 allowing the layout render site to contain one lowercase JSX tag for the landmark element.
  The recommendation is option (1) for minimal scope, with option (2) noted as the stronger a11y choice if the owner accepts the T173 touch.

### F3 — The layout must not add a second `<h1>`
· **Severity:** **Major/Blocker**
· **Evidence:** AC8 requires exactly one `<h1>` per rendered authenticated page. `HomeScreen.tsx` already renders one via the `Heading` primitive (`frontend/src/app/ui/Heading.tsx`).
· **Assessment:** If the layout uses `Heading` for its own banner copy, the document will have two `<h1>` elements when `/app/home` renders. This violates AC8 and the L11 document-outline requirement.
· **Recommended brief amendment:** Forbid `Heading` in the layout. Use `Text` for any generic copy (e.g., an app name or tagline). The layout's copy must be styled as body text, not a heading.

### F4 — The layout must remain passive; it does not manage focus
· **Severity:** **Major**
· **Evidence:** AC8 says the layout does not take focus on mount. T174's I9 guard will be extended to cover the layout.
· **Assessment:** A layout that autofocuses itself would steal focus from the screen's heading (T174's carefully chosen arrival target) and break the I9 guard for every authenticated screen. The layout must contain no `useEffect`/`useLayoutEffect` that calls `.focus()`, no `autoFocus` prop, and no `tabIndex >= 0`.
· **Recommended brief amendment:** Add an explicit constraint: "The layout does not hold refs, does not call `.focus()`, and renders no element with a non-negative `tabIndex`."

### F5 — The route table change is mechanically simple but must keep screen-count pins honest
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/routes.tsx:89-95` currently has `RequireSession` as a pathless layout whose children are the eleven authenticated paths, with `HomeScreen` attached to `HOME_PATH`.
· **Assessment:** The layout must be inserted as a pathless child of `RequireSession`, and the eleven paths must become children of the layout. This preserves `RequireSession` as the gate, preserves the path list, and keeps the screen-count pins from seeing a new path-attached screen.
· **Recommended brief amendment:** Show the exact structure:
```tsx
{
  element: <RequireSession />,
  children: [
    {
      element: <AuthenticatedLayout />,
      children: AUTHENTICATED_ROUTE_PATHS.map((path) => {
        if (path === HOME_PATH) return { path, element: <HomeScreen /> };
        return { path };
      }),
    },
  ],
}
```

### F6 — The six instruments must be grown, but not all counts move
· **Severity:** **Major**
· **Evidence:** The brief lists six instruments and says some may fire unexpectedly (T174's D-5 precedent).
· **Assessment:** Predicted effects:
  - `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`: `RENDER_SITES` 6 → 7 (add `app/AuthenticatedLayout.tsx`); union 14 → 15.
  - `routesResolveUnderAppPrefixOnly.test.tsx`: `SCREENS_ATTACHED_SO_FAR` stays 4 (sign-in, callback, signed-out, home) because the layout is pathless, not a path-attached screen. **Must be measured**, not assumed.
  - `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`: likely unaffected because it reads path-attached elements; the layout is pathless. **Must be measured**.
  - `homeRouteRendersLandingScreenAndManagesFocus.test.tsx`: cases that mount the real route table will now see the layout wrapper. Assertions about `main.children` order or exact DOM children may need to admit the layout's markup. Direct-mount cases are unaffected.
  - `shellBuildsAndMounts.test.tsx`: AC11 says app-shell `innerHTML` is still `''` when no route matches. The layout only renders when a route matches, so this should be unaffected. **Must be measured**.
  - `unauthenticatedRoutesRedirectToSignIn.test.tsx`: the named test, extended under T41's prefix convention.
· **Recommended brief amendment:** Provide a checklist with predicted vs. measured outcomes for each instrument, and a note that any prediction that is wrong is recorded as a deviation.

### F7 — OQ-T42-2 (sign-out affordance) must be carried, not decided silently
· **Severity:** **Major**
· **Evidence:** `signOut()` (T35), `endSession()` (T36), the signed-out landing (T37) and cross-tab propagation (T39) are built, but nothing in production source calls them. R19 is conditional.
· **Assessment:** Authenticated chrome is the natural home for a sign-out control, and adding it now would give four closed tasks a reachable affordance. But doing so without an explicit owner decision is scope creep and would force identity-independent copy choices (the button label). It also interacts with T38 (blocked on Q15).
· **Recommended brief amendment:** Record: "Sign-out chrome is deliberately NOT added in T42. It is carried to the Phase 4 approval gate / a later task as OQ-T42-2. The layout is designed so a header button can be added later without restructuring the route table."

### F8 — Catalogue entries for the layout should be minimal and generic
· **Severity:** **Minor/Major**
· **Evidence:** AC6 requires every rendered string to come from the catalogue. A minimal layout needs at least one generic string.
· **Assessment:** Suggested keys: `shell.brand` (e.g., "Checky") or `shell.tagline`. These are identity-independent and stable. Avoid "Welcome back" or user-shaped text (AC10). The copy should be literal in tests and asserted against both `en.shell.brand` and the English string.
· **Recommended brief amendment:** Add exactly one key, `shell.brand`, to `i18n/catalogue.ts`. Freeze it as the only layout-owned string.

### F9 — The named test extension must use T41's prefix convention
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:20-23` says describes are prefixed from the first case so task 42 can extend the file without AC-number collisions.
· **Assessment:** New cases should live under `describe('T42 authenticated layout', ...)` or similar, not reuse bare `describe('AC1', ...)`. This preserves the convention and makes failures readable.
· **Recommended brief amendment:** Require the new describe block to be named `T42 — authenticated layout` and place AC1/AC4 cases inside it.

### F10 — Module boundaries need a source scan
· **Severity:** **Minor/Major**
· **Evidence:** Constraints say the layout may import from `app/ui`, `react-router` (`Outlet`) and `react-i18next`, and from `app/routes` if needed. It must not import from `auth/`, must not read `accessToken`.
· **Assessment:** A source scan mirroring T174's AC6 cases is the cheapest durable enforcement. Because the layout is so close to the auth boundary, an accidental `import { accessToken } from '../auth/accessToken'` is a realistic future regression.
· **Recommended brief amendment:** Add an AC10 source scan that asserts `AuthenticatedLayout.tsx` imports only from `app/ui`, `react-router`, `react-i18next`, and (optionally) `app/routes`. Forbid `auth/`, `accessToken`, `idToken`, `fetch`, storage, and `globalThis.location`.

### F11 — Existing T174 tests that assert `main.children` exact order will need updating
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:142-150` asserts `main.children` tags equal `['H1', 'P']`. After T42, `HomeScreen` still renders `<main>` with `H1` then `P`, so this direct-mount assertion remains true.
· **Assessment:** The real-route-table cases in T174 will now include the layout's wrapper outside `<main>`, but assertions scoped to `<main>` are unchanged. Only assertions about the full document structure or the immediate parent of `<main>` need updating. The brief already flags `main.children` as a candidate.
· **Recommended brief amendment:** State that T174's `main`-scoped assertions stay as-is; any assertion about the layout wrapper around `<main>` is new T42 coverage, not a T174 edit.

### F12 — Using `Surface` inside the layout duplicates `AppShell`'s `Surface`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/AppShell.tsx:12-13` renders `<Surface data-testid="app-shell">`. `Surface` renders `<div className="min-h-dvh bg-surface text-text">`.
· **Assessment:** If the layout also renders `<Surface>`, the live DOM will have two nested `min-h-dvh` divs. This is redundant but harmless. If the layout does NOT render `Surface`, it has almost no DOM presence, making AC9 harder to satisfy. A middle path is to render `<Surface>` in the layout only if the brief relaxes AC9 (F2 option 1); the duplication is then the cost of respecting the Out list.
· **Recommended brief amendment:** If option (b) minimal wrapper is chosen with option (1) for F2, document that the nested `Surface` is intentional redundancy caused by the no-touch-`app/ui/` constraint.

### F13 — The layout is the right long-term home for an app-wide `StatusRegion`
· **Severity:** **Minor**
· **Evidence:** L11 requires live regions for async results. Currently each screen that needs one mounts its own `StatusRegion`.
· **Assessment:** Rendering an empty `StatusRegion` in the authenticated layout would give all authenticated screens a pre-mounted app-wide live region. However, `StatusRegion`'s type currently requires `children`, and its comment says the region must be mounted before text arrives. Passing `null` or `''` would work but may feel awkward. Adding this is optional and not required by any AC.
· **Recommended brief amendment:** Optionally include an empty `StatusRegion` in the layout for future async announcements; if it complicates the type, defer it.

### F14 — Parameterised paths (`invoices/:id`, `receipts/:id`) must be exercised with concrete IDs
· **Severity:** **Minor/Major**
· **Evidence:** AC1 says "each of the eleven authenticated paths renders the layout — including ... `invoices/:id` and `receipts/:id` with a concrete id."
· **Assessment:** The layout renders for any matched child route, so a single test with `invoices/<uuid>` and `receipts/<uuid>` is sufficient. The test does not need to assert content for the ten empty paths, only that the layout's generic copy is present and no redirect occurs.
· **Recommended brief amendment:** Add a parameterized loop over `AUTHENTICATED_ROUTE_PATHS` with `path.replaceAll(':id', SAMPLE_ID)`, asserting the layout's brand text is in the DOM and the redirect does not occur.

### F15 — The empty-string token case must be re-asserted for the layout
· **Severity:** **Minor/Major**
· **Evidence:** AC4 says "With no session — and with the empty-string token, which is not a session — every authenticated path redirects to the sign-in start and renders no part of the layout."
· **Assessment:** T41 already has an empty-string token case, but it is owned by T41's AC1. T42 should re-assert it specifically to prove no part of the layout renders. The same `retainAccessToken('')` pattern works.
· **Recommended brief amendment:** Add a T42 AC4 case that arrives at `/app/account` with `retainAccessToken('')` and asserts the layout's catalogue text is absent from the DOM.

### F16 — A mutation harness must cover all eleven ACs
· **Severity:** **Minor**
· **Assessment:** Candidate mutants:
  - Remove the layout route entirely → AC1 fails for empty paths.
  - Replace `<Outlet />` with `{null}` → AC1 fails; HomeScreen does not render (AC2).
  - Add `Heading` to the layout → two `<h1>` on `/app/home` (AC8).
  - Add `useEffect` that focuses the layout root → layout takes focus (AC8/I9).
  - Hardcode English in the layout → AC6 fails.
  - Import `accessToken` → AC10 fails.
  - Move the layout outside `RequireSession` → AC4 fails (layout renders for unauthenticated).
  - Drop `AuthenticatedLayout.tsx` from `RENDER_SITES` → AC7 fails.
  - Add a non-negative `tabIndex` to the layout → AC8 fails.
  - Make the layout call `fetch` on mount → AC11 fails.
· **Recommended brief amendment:** Include these rows in the harness, marking route-table mutants as `own` and instrument-growth mutants as `inherited`/`own` as appropriate.

---

## Summary

T42's central design challenge is **F1** (what the layout contains) and **F2** (the contradiction between AC9's landmark requirement, AC7's no-lowercase-tag rule, and the Out list's ban on touching `app/ui/`). The recommended resolution is a **minimal wrapper (option b)** that renders through `Surface` and `Text`, with AC9 relaxed to "structural wrapper distinct from `<main>`". The alternative — adding a `Banner` primitive — is the stronger accessibility choice but requires a scope change to T173. The remaining findings are about route-table shape (F5), instrument growth (F6), focus passivity (F4), heading avoidance (F3), sign-out scope (F7), catalogue copy (F8), test conventions (F9, F14, F15), and module boundaries (F10). The task is small mechanically but sits at a tension point between closed-task constraints and new accessibility requirements, so the brief must resolve F2 explicitly before implementation.

**Phase 3 complete.** Sixteen findings returned; no code or contract changed.
