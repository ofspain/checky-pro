<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T173 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The no-headless-library decision is the task's largest judgment call and needs a crisp brief amendment
· **Severity:** **Blocker/Major**
· **Evidence:** `artifacts/02-task-implementation-brief.md §3d` argues against installing Radix UI etc. because the current tree has no surface for one. The task statement says *"Tailwind CSS with headless/unstyled accessible primitives"*.
· **Assessment:** The brief takes the position that `src/app/ui/` IS the primitive layer. This is defensible for today but it is a deviation from the literal task wording. Phase 3 must either ratify it or reject it. If ratified, the brief should state explicitly that no third-party headless library is being installed and why, so later tasks (MFA enrollment, R11–R13) know when to revisit. If rejected, the brief must name the library and the primitives it provides today.
· **Recommended brief amendment:** Add an explicit decision line: "This task installs Tailwind CSS only; `src/app/ui/` is the project's own headless primitive layer. A third-party headless library will be considered when the first screen needs a dialog, dropdown, or other composite control."

### F2 — The primitive inventory is incomplete: no primitive matches `AppShell`'s container
· **Severity:** **Blocker/Major**
· **Evidence:** The five render sites are `AppShell.tsx`, `RateLimitNotice.tsx`, `SignInScreen.tsx`, `CallbackScreen.tsx`, `SignedOutScreen.tsx`. The proposed seven primitives are `Screen` (`<main>`), `Heading` (`<h1>`), `Text` (`<p>`), `ActionButton` (`<button>`), `TextLink` (`<Link>`), `StatusRegion` (`<div role="status" aria-live="polite">`), `FocusHolder` (`<div tabIndex={-1}>`). `AppShell` renders `<div data-testid="app-shell">`.
· **Assessment:** None of the seven primitives wraps a generic container. `Screen` is `<main>` and cannot be used inside `AppShell` without nesting `<main>` elements (invalid HTML). Either `AppShell` is exempt from the "Only" rule, or an eighth primitive (`Shell`, `View`, or `AppContainer`) is needed. AC2 says every render site renders through `app/ui/` primitives, so exemption is a contradiction.
· **Recommended brief amendment:** Add an eighth primitive — `Shell` or `AppContainer` — that renders the shell's `<div data-testid="app-shell">`. Define whether it accepts `children` only or also `data-testid`.

### F3 — The no-`className` rule may be too rigid for legitimate layout variation
· **Severity:** **Major**
· **Evidence:** `artifacts/02-task-implementation-brief.md §3c` says "none takes a `className`". AC4 pins this.
· **Assessment:** If every screen has identical layout, the rule works. But the existing screens have different structures: `SignInScreen` has a heading, paragraph, and button; `CallbackScreen` has a heading and status region; `SignedOutScreen` has a heading, paragraph, and link. If one needs vertical centering and another needs top alignment, the primitive layer must encode every layout variant, or all screens must share one layout. The brief says "This task restyles; it does not redesign," implying layouts may already differ subtly.
· **Recommended brief amendment:** Decide whether `className` is forbidden entirely, allowed only on layout primitives (`Shell`), or allowed via a restricted set of layout props (e.g., `variant`, `align`). State the rule in the brief so the `Only` scan knows what to forbid.

### F4 — How the "Only" claim is enforced is unspecified
· **Severity:** **Major/Blocker**
· **Evidence:** AC2 says every screen renders through `app/ui/` primitives and no screen renders a bare `<main>`, `<h1>`, `<p>`, `<button>`, `<div>` or `<Link>`. The named test is `screensRenderThroughDesignSystemPrimitivesOnly`.
· **Assessment:** A runtime DOM test can check the rendered output of the five known screens, but it cannot prove a future screen will comply. A source scan of each screen file — asserting it imports only from `app/ui/` and uses no bare JSX element literals — is stronger. The brief must specify the enforcement mechanism and the file glob (e.g., `src/**/Screen.tsx`, `src/app/AppShell.tsx`, `src/app/RateLimitNotice.tsx`).
· **Recommended brief amendment:** Specify that the named test uses a source scan across the five render sites (and future `*Screen.tsx` files) to assert imports come only from `app/ui/` and no bare HTML/Link JSX tags are present.

### F5 — `data-testid` and other HTML attributes need a policy for primitives
· **Severity:** **Major**
· **Evidence:** AC11 says every `data-testid` is unchanged. `AppShell` has `data-testid="app-shell"`. `RateLimitNotice`'s focus holder has no testid. `SignInScreen`'s paragraph has `id="sign-in-explanation"`.
· **Assessment:** Primitives must forward some attributes (`id`, `data-testid`, ARIA) but not `className`. The brief needs a policy: which HTML attributes are forwarded, which are set internally, and which are forbidden. For example, should `Text` accept `id` for `aria-describedby`? Should `Shell` accept `data-testid`? Should `Screen` accept `data-testid`?
· **Recommended brief amendment:** List the props each primitive accepts. Forward `ref` and `children` everywhere; forward `id` and `data-testid` where needed; forward all ARIA props on interactive primitives; explicitly reject `className` and `style`.

### F6 — The WCAG AA contrast test needs a concrete arithmetic strategy
· **Severity:** **Major**
· **Evidence:** AC9 says colour pairs meet WCAG AA contrast, computed from token values rather than asserted by eye.
· **Assessment:** The tokens live in a `@theme` block inside `src/app/styles.css`. A test can read the CSS file, parse the `--color-*` custom properties, convert sRGB hex values to relative luminance, and compute contrast ratios. The brief must specify which pairs are tested (e.g., text-on-surface, text-on-accent, focus-ring-on-surface) and the threshold (4.5:1 for normal text, 3:1 for large text/focus indicators).
· **Recommended brief amendment:** State the token names, the pairs to test, and the threshold. Example: test `--color-text` on `--color-surface` ≥ 4.5:1, `--color-text-inverse` on `--color-accent` ≥ 4.5:1, `--color-focus` on `--color-surface` ≥ 3:1.

### F7 — Build output verification must be part of the acceptance gates
· **Severity:** **Major**
· **Evidence:** AC1 says the stylesheet reaches the **built output** as an emitted asset, not only the dev server.
· **Assessment:** Running `pnpm run build` and asserting a `.css` asset exists in `dist/` is required. The brief mentions gates but does not list this command. The vite config change must be tested by the build, not only by `tsc` or `vitest`.
· **Recommended brief amendment:** Add `pnpm run build` to the gates and specify how to locate the emitted CSS asset (e.g., `dist/assets/index-*.css`).

### F8 — Closed-task instruments will break and must be grown in advance
· **Severity:** **Major**
· **Evidence:** AC12 notes 13 suites assert `innerHTML`, 11 use `querySelector`, 8 read `tagName`. `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:807` pins tag order.
· **Assessment:** Changing `<main>` to `<Screen>` etc. changes the React component tree but, if primitives forward to the same tags, the rendered HTML should remain identical. However, tests that import and inspect component types will break. The brief says instruments must be grown, not bypassed. Phase 5 must enumerate which tests will fire and how to update them.
· **Recommended brief amendment:** Add a phase-5 checklist: for each closed task that imports a screen or asserts tag structure, decide whether to update the test to import the primitive or to keep the assertion on rendered HTML.

### F9 — `RateLimitNotice`'s focus management must be preserved by assertion
· **Severity:** **Major**
· **Evidence:** AC6 says dismissing the notice moves focus to the holder rather than dropping it to `<body>`.
· **Assessment:** `RateLimitNotice.tsx` currently holds a ref to the `<div tabIndex={-1}>`. The `FocusHolder` primitive must forward ref correctly. A test should render `RateLimitNotice`, click the dismiss button, and assert `document.activeElement` is the focus holder.
· **Recommended brief amendment:** Specify the focus test: after clicking dismiss, `document.activeElement` equals the focus holder element (reachable via `data-testid` or ref).

### F10 — Primitive props interfaces are not specified
· **Severity:** **Minor/Major**
· **Evidence:** The brief lists what each primitive wraps but not the exact props.
· **Assessment:** Without clear interfaces, two developers could implement `ActionButton` differently (e.g., one accepts `disabled`, the other only `aria-disabled`). The `aria-disabled` constraint must be enforced by type: `disabled` should not be in the props.
· **Recommended brief amendment:** Write a props table for each primitive. Example: `ActionButton: { children: ReactNode; onClick?: () => void; 'aria-describedby'?: string; 'aria-busy'?: boolean; 'aria-disabled'?: boolean; ref?: Ref<HTMLButtonElement> }`.

### F11 — `FocusHolder` children should probably be optional
· **Severity:** **Minor**
· **Evidence:** `FocusHolder` wraps `<div tabIndex={-1}>`. In `RateLimitNotice` it is a focus target and may have no children.
· **Assessment:** The brief says each primitive takes `children`, but a focus holder is often empty. Making children required forces an empty fragment.
· **Recommended brief amendment:** Make `children` optional on `FocusHolder`.

### F12 — Tailwind v4 CSS-first vs v3 config is a real choice
· **Severity:** **Minor/Major**
· **Evidence:** The brief proposes Tailwind 4 with `@tailwindcss/vite` and a `@theme` block. `tasks.md:31` mentions "no Tailwind or PostCSS configuration" from a v3 perspective.
· **Assessment:** Tailwind 4 is stable but newer. The team must be comfortable with the CSS-first API, `@theme`, and the Vite plugin. If the build pipeline or team preference favors v3, the brief should switch now. OQ-T173-6 is carried for this reason.
· **Recommended brief amendment:** Explicitly ratify Tailwind 4 and document the decision, or switch to v3 with a config file. Do not leave OQ-T173-6 open through implementation.

### F13 — Exact-version pinning is right but must be enforced in the lockfile
· **Severity:** **Minor**
· **Evidence:** The brief says pin exactly, no `^`, matching other dependencies.
· **Assessment:** `package.json` should list exact versions. `pnpm-lock.yaml` will reflect them. A test is not needed, but the gate should include inspecting `package.json`.
· **Recommended brief amendment:** Add an AC or gate that `package.json` contains no `^` or `~` for `tailwindcss` and `@tailwindcss/vite`.

### F14 — The `TextLink` primitive must decide how to handle external vs internal links
· **Severity:** **Minor**
· **Evidence:** `SignedOutScreen.tsx` uses react-router's `<Link to>`. The primitive is `TextLink`.
· **Assessment:** `TextLink` should probably accept a `to` prop and use react-router's `Link`. It should not accept `href` (external links are not in the inventory). If external links appear later, a separate primitive may be needed.
· **Recommended brief amendment:** State that `TextLink` accepts `to: string` and renders `Link` from react-router; `href` is not accepted.

### F15 — The `StatusRegion` primitive must always render, even when empty
· **Severity:** **Minor/Major**
· **Evidence:** AC5 says `role="status"` + `aria-live="polite"` is always rendered.
· **Assessment:** If `StatusRegion` is rendered only when there is a message, screen readers won't notice new content. The primitive should render the container always and update its children. The brief implies this but should state it.
· **Recommended brief amendment:** Add: `StatusRegion` renders its wrapper unconditionally; consumers pass children when there is status text.

### F16 — `Screen` landmark and `Heading` count must be asserted per screen
· **Severity:** **Minor/Major**
· **Evidence:** AC5 requires one `<main>` landmark and one `<h1>` per screen.
· **Assessment:** A source scan can check each screen uses `Screen` and `Heading` once. A runtime test can assert the rendered DOM has exactly one `<main>` and one `<h1>` per screen.
· **Recommended brief amendment:** Add both assertions to the named test: source scan for imports/usage and runtime DOM scan for exactly one landmark and heading.

### F17 — The catalogue-unchanged constraint needs a verification plan
· **Severity:** **Minor**
· **Evidence:** AC7 says no primitive contains user-facing copy; AC8 says every screen's rendered text is unchanged.
· **Assessment:** Tests can snapshot or assert rendered text before and after. The brief should say how: either keep a baseline string per screen or assert against `i18n/catalogue.ts` values.
· **Recommended brief amendment:** Specify that the named test asserts each screen's rendered text matches the corresponding catalogue entries exactly.

### F18 — `aria-disabled` must not become `disabled` inside `ActionButton`
· **Severity:** **Minor/Major**
· **Evidence:** Constraints say `aria-disabled`, not `disabled`, because a focused element that becomes disabled drops the keyboard user to `<body>`.
· **Assessment:** The primitive must forward `aria-disabled` to the button and never map it to the `disabled` attribute. A source scan can forbid `disabled` in `ActionButton.tsx`.
· **Recommended brief amendment:** Add a source-scan assertion: `ActionButton.tsx` contains the literal `aria-disabled` and does not contain `disabled`.

### F19 — `mountShell.tsx` importing CSS must not conflict with test isolation
· **Severity:** **Minor**
· **Evidence:** `mountShell.tsx` will import `styles.css`. Tests that import `mountShell` or a screen may pull in the CSS.
· **Assessment:** Under vitest/jsdom, CSS imports may need a stub or may be processed. The brief should note whether a test transform is needed. Tailwind v4 CSS is processed by the Vite plugin; in vitest, CSS may be handled by default.
· **Recommended brief amendment:** Note that CSS imports are expected to work under vitest without extra configuration, and if not, a small stub or plugin change is allowed.

### F20 — The `Only` scan must not flag fragments, conditionals, or JSX spread as violations
· **Severity:** **Minor**
· **Evidence:** A source scan that forbids bare `<div>` etc. might incorrectly flag React fragments (`<>...</>`), conditional rendering, or JSX spread usage.
· **Assessment:** The scan must be precise: it should look for lowercase JSX tag names that are not imported from `app/ui/`, not for all JSX. The brief should specify the scan's logic to avoid false positives.
· **Recommended brief amendment:** Define the source scan as: "Every lowercase JSX tag in a render site must be an `app/ui/` primitive; fragments, expressions, and spread props are allowed."

---

## Summary

T173's design challenge is about the shape and boundaries of the primitive layer. The most important unresolved questions are **F1** (whether to install a third-party headless library at all), **F2** (the missing primitive for `AppShell`'s container), **F3** (the no-`className` rule's rigidity), **F4** (how to enforce the "Only" claim), and **F5** (the attribute-forwarding policy). Secondary but load-bearing are **F6** (WCAG contrast arithmetic), **F7** (build-output verification), **F8** (closed-task instrument breakage), and **F9** (focus preservation). The brief is thorough on intent but needs crisp decisions on these ten points before implementation.

**Phase 3 complete.** Twenty findings returned; no code or contract changed.
