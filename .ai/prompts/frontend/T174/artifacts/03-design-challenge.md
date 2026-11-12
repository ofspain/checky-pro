<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T174 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The focus target is the central unresolved design question
· **Severity:** **Blocker**
· **Evidence:** AC2 says the screen must move focus on arrival; AC8 says "the heading is what a screen reader meets after the move." OQ-T174-2 states L11 does not say what element receives focus.
· **Assessment:** Three options exist, and the brief must choose one before implementation:
  1. **The `<main>` landmark** (`Screen`). `Screen` already forwards a ref. Focusing the landmark moves the screen reader to the region, but it does not automatically announce the heading.
  2. **The `<h1>`** (`Heading`). This is what AC8's wording points to: a screen reader focused on a heading reads the heading. But `Heading` does not currently accept `tabIndex`, and T173's rule #2 lists `tabIndex` as an attribute primitives set internally.
  3. **A `FocusHolder` with `aria-labelledby`.** This keeps primitives unchanged but focuses a non-semantic wrapper; the heading is announced only via the label, not as the focused element itself.
· **Recommended brief amendment:** Focus the `<h1>`. Add an optional `tabIndex?: -1` prop to `Heading`, restricted to `-1` so it cannot be inserted into the tab order. Document this as a narrow accessibility exception: `tabIndex` is not part of the heading's semantic contract (level is), and the only valid value is `-1` for programmatic focus on arrival.

### F2 — Modifying `Heading` touches T173's primitive invariant
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/ui/index.ts` states rule #2: "`role`, `aria-live`, `tabIndex`, `type='button'` and the heading level are set inside." `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` scans prop types for `className` and `style` but does not forbid `tabIndex`.
· **Assessment:** Adding `tabIndex` to `Heading` does not violate AC4 (no styling escape hatch), but it does violate the prose rule in the barrel file. The rule needs to be refined so it forbids attributes that define the primitive's role, while allowing behaviour attributes like `tabIndex` when justified. If the team wants to keep the rule literal, a new `FocusableHeading` primitive is the alternative.
· **Recommended brief amendment:** Update `ui/index.ts` rule #2 to say: "The attributes that define a primitive's role or ARIA contract are set inside; behaviour attributes such as `tabIndex` may be exposed narrowly when a screen needs to manage focus." Or, if the rule stays literal, create `FocusableHeading` and add it to the primitive inventory.

### F3 — The home screen's file location is not decided
· **Severity:** **Minor/Major**
· **Evidence:** The brief says the component may land in `src/app/` or `src/auth/`, and the suite sits beside it.
· **Assessment:** `src/auth/` already holds sign-in, callback and signed-out screens, but the home screen is the first authenticated app screen, not part of the auth flow. `src/app/` is the natural home and keeps it under the `app/ui/` and `app/` sweep that T173's instruments already cover. If it lands in `src/auth/`, it would be the only authenticated app screen there and would mix auth-flow screens with post-auth screens.
· **Recommended brief amendment:** Place the component at `src/app/HomeScreen.tsx` and the test at `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx`.

### F4 — The three instruments must be grown before the task is complete
· **Severity:** **Major/Blocker**
· **Evidence:** AC7 requires growing `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, `routesResolveUnderAppPrefixOnly.test.tsx`, and `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`.
· **Assessment:** Each instrument has a specific handoff:
  - `routesResolveUnderAppPrefixOnly.test.tsx`: `SCREENS_ATTACHED_SO_FAR` goes from 3 to 4, adding `home`.
  - `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`: the screen-count assertion and the signed-out-screen structural pin must account for the new screen behind the gate.
  - `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`: `RENDER_SITES` goes from 5 to 6, adding `app/HomeScreen.tsx`, and the union goes from 13 to 14.
· **Recommended brief amendment:** List the exact edits for each instrument and the expected values after T174.

### F5 — The screen's focus management may conflict with task 42's authenticated shell
· **Severity:** **Minor/Major**
· **Evidence:** The brief says task 42 nests the authenticated shell around this screen; the home screen must be "something to build on, not a placeholder to delete."
· **Assessment:** If the home screen contains its own focus-management `useEffect`, task 42 may later move that logic to a shell wrapper. That is acceptable as long as the home screen's implementation is clean (a ref + effect, not scattered logic). The brief should acknowledge that this focus move is the screen's responsibility today and may become the shell's responsibility later.
· **Recommended brief amendment:** State that the home screen owns focus on arrival for now, and task 42 may refactor it into the authenticated shell without changing behaviour.

### F6 — The screen must not import from `auth/` but needs the route constant
· **Severity:** **Minor/Major**
· **Evidence:** Constraints say the screen may import from `app/ui` and `react-i18next`; must not import from `auth/`. The screen does not need `HOME_PATH` for rendering, but `routes.tsx` will import the screen.
· **Assessment:** The screen is pure: it renders a heading and paragraph from the catalogue. It does not read tokens, route constants, or auth state. This is correct and must be enforced by a source scan.
· **Recommended brief amendment:** Add an AC/source scan that `HomeScreen.tsx` imports only from `app/ui` and `react-i18next`.

### F7 — The catalogue copy is unspecified and should be minimal
· **Severity:** **Minor**
· **Evidence:** OQ-T174-1 says the screen's copy is unspecified; the brief proposes the minimum that is not blank.
· **Assessment:** Two keys are enough: `home.heading` (e.g., "Home") and `home.explanation` (e.g., "You are signed in."). Any more copy risks creeping toward identity-dependent content or dashboard features that are out of scope.
· **Recommended brief amendment:** Freeze the catalogue keys as `home.heading` and `home.explanation`, with placeholder English that the owner can rewrite without touching the component.

### F8 — Focus must not create a trap
· **Severity:** **Minor/Major**
· **Evidence:** AC8 says focus can leave the screen by keyboard.
· **Assessment:** Focusing the `<h1>` with `tabIndex={-1}` places focus on a non-tabbable element. The next Tab press moves focus out of the heading to the next tabbable element (or `<body>` if none). This is not a trap. A test should verify that after Tab, focus is no longer on the heading (or that it has moved to a sensible place).
· **Recommended brief amendment:** Add a test that presses Tab after the initial focus move and asserts `document.activeElement` is not the heading.

### F9 — The unauthenticated-redirect case belongs to T41 but must be re-asserted for `/app/home`
· **Severity:** **Minor/Major**
· **Evidence:** AC4 requires an unauthenticated arrival at `/app/home` to redirect to sign-in.
· **Assessment:** T41 already tests that all authenticated paths redirect. T174's named suite should include a `/app/home` specific case for two reasons: it documents the screen's own AC4, and it catches a regression where `routes.tsx` accidentally moves `HOME_PATH` outside the gate.
· **Recommended brief amendment:** Add an AC4 case that arrives at `/app/home` without a session and asserts the sign-in screen renders and the original path is not in the DOM.

### F10 — No identity-dependent content must reach the DOM
· **Severity:** **Minor/Major**
· **Evidence:** AC6 cites R22 and R20.
· **Assessment:** The screen takes no props and reads no store. A source scan can enforce that `HomeScreen.tsx` contains no words like "name", "email", "account", or imports from `api/` or `auth/` (other than the allowed `app/ui` and `react-i18next`).
· **Recommended brief amendment:** Add a source scan forbidding identity-related strings and any import outside `app/ui` and `react-i18next`.

### F11 — The mutation harness needs at least one row per criterion
· **Severity:** **Minor**
· **Evidence:** Required tests specify a mutation harness with at least one row per criterion, predicting `own`/`inherited`.
· **Assessment:** Candidate mutants:
  - Remove `tabIndex={-1}` from `Heading` call → focus stays on `<body>` (AC2).
  - Remove the `useEffect` focus call → focus does not move (AC2).
  - Render a bare `<div>` instead of using `Screen`/`Heading`/`Text` → violates AC1/T173.
  - Hardcode English instead of using `t()` → violates AC3.
  - Move `HOME_PATH` outside the gate → unauthenticated arrival sees home (AC4, inherited from T41).
  - Add a network call or storage write → violates AC5.
  - Add identity-dependent copy → violates AC6.
· **Recommended brief amendment:** Include these rows in the harness, marking AC4 as `inherited` (T41 owns the redirect) and the rest as `own`.

### F12 — The screen-count pin in `routesResolveUnderAppPrefixOnly` must distinguish screens from redirects
· **Severity:** **Minor**
· **Evidence:** T173 introduced `SCREENS_ATTACHED_SO_FAR` and `REDIRECTS_ATTACHED_SO_FAR`.
· **Assessment:** The home screen is a screen, so it extends `SCREENS_ATTACHED_SO_FAR`, not `REDIRECTS_ATTACHED_SO_FAR`. The count goes from 3 to 4 screens, and the redirect count stays at 1.
· **Recommended brief amendment:** Update `SCREENS_ATTACHED_SO_FAR` to `['sign-in', 'callback', 'signed-out', 'home']`.

### F13 — The structural pin in `publicRouteSetIsExactlyTheDeclaredSet` may need refinement
· **Severity:** **Minor**
· **Evidence:** T37's suite has a case about "one landmark holding a level-one heading, a paragraph and the way back, in that order" for `SignedOutScreen`.
· **Assessment:** `HomeScreen` will also render `<main>`, `<h1>`, `<p>`, but no link. The existing structural pin is specific to the signed-out screen. A new pin for the home screen should assert `<main>` contains exactly one `<h1>` and one `<p>`, in that order.
· **Recommended brief amendment:** Add a T174-specific structural case in the named suite or extend the instrument to cover the home screen.

### F14 — The `Only` sweep must include the home screen
· **Severity:** **Minor/Major**
· **Evidence:** T173's `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` has `RENDER_SITES`.
· **Assessment:** Adding `app/HomeScreen.tsx` to `RENDER_SITES` makes the sweep check it. The union count becomes 14 (6 render sites + 8 primitives). The derived lowercase-tag set remains 7 because `HomeScreen` uses `Screen`, `Heading`, `Text`.
· **Recommended brief amendment:** Update `RENDER_SITES` and the union assertion to 14.

### F15 — The home screen should not break T41's authenticated-path tests
· **Severity:** **Minor**
· **Evidence:** T41's tests mount the real route table and assert redirects for unauthenticated paths.
· **Assessment:** Adding a real element to `HOME_PATH` does not change redirect behaviour; `RequireSession` still redirects without a session. With a session, `/app/home` now renders content instead of blank. T41's AC5 tests that authenticated paths are not redirected; they don't assert content. So no T41 test should break.
· **Recommended brief amendment:** None required; note that T41 tests remain valid.

### F16 — The `CallbackScreen` return target now has a real destination
· **Severity:** **Minor**
· **Evidence:** `CallbackScreen` navigates to the return target or home. Previously `/app/home` rendered blank.
· **Assessment:** After T174, a successful sign-in with no remembered target lands on a real screen with focus management. This is the end-to-end flow T41 could not test (OQ-T41-2). T174's suite can assert this compositionally: the callback navigates to `/app/home`, and the home screen focuses its heading.
· **Recommended brief amendment:** Optional: add a cross-suite case that mounts `CallbackScreen` with a valid callback and asserts navigation to `/app/home` followed by focus on the home heading. This may be too heavy for T174; document whether it is in or out of scope.

### F17 — The screen must tolerate StrictMode double invocation
· **Severity:** **Minor**
· **Evidence:** `mountShell` renders in `StrictMode`.
· **Assessment:** The `useEffect` focus call must be safe if it runs twice. `headingRef.current?.focus()` is idempotent. No state or side effects should be duplicated.
· **Recommended brief amendment:** None required; ensure the focus effect has no dependencies that could cause issues.

---

## Summary

T174's design challenge is dominated by **F1** (what element receives focus on arrival) and **F2** (how that choice interacts with T173's primitive rules). The best design is to focus the `<h1>` by adding a narrowly typed `tabIndex?: -1` prop to `Heading`, with a documented exception in the primitive rules. The remaining findings are about instrument growth (**F4**, **F12**, **F14**), file location (**F3**), focus-trap avoidance (**F8**), unauthenticated redirect coverage (**F9**), identity-free content (**F10**), and the mutation harness (**F11**). The task is small but touches three closed-task instruments, so the brief must specify each edit precisely.

**Phase 3 complete.** Seventeen findings returned; no code or contract changed.
