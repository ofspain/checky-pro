<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T173 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Consumes** | `artifacts/00-repository-understanding.md`, `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## 1 · What this task is

O2 was resolved on 2026-10-10 and **27 units shipped without it**. The tree has no stylesheet, no `className`
and no build step for either (verified in phase 0). This task installs Tailwind, puts a primitive layer between
the screens and the DOM, and makes *every* existing screen go through it — the `Only` in the named test's name
(`screensRenderThroughDesignSystemPrimitivesOnly`) being the criterion, not a sample.

It is the **first task in this package with no requirement behind it.** L11, L13, O2/Q7 and `agents.md:93-97`
are its whole authority (phase 1, §1), and L11 is written per-screen: *"Every screen, in every phase."*

## 2 · The complete element inventory, which is small enough to enumerate

Five production render sites, read line by line in phase 0:

| site | elements it renders |
|---|---|
| `app/AppShell.tsx` | `<div data-testid="app-shell">` |
| `app/RateLimitNotice.tsx` | `<div tabIndex={-1}>` (focus holder), `<div role="status" aria-live="polite">`, `<button type="button">` |
| `auth/SignInScreen.tsx` | `<main>`, `<h1>`, `<p id="sign-in-explanation">`, `<button type aria-describedby aria-disabled aria-busy>`, `<div role="status" aria-live="polite">` |
| `auth/CallbackScreen.tsx` | `<main>`, `<h1>`, `<div role="status" aria-live="polite">` |
| `auth/SignedOutScreen.tsx` | `<main>`, `<h1>`, `<p>`, `<Link to>` |

Six distinct shapes, and **that is the whole surface**: one landmark, one heading level, body text, one control,
one in-app link, one live region, plus the shell's own container and the notice's focus holder. Nothing in the
tree renders a dialog, a dropdown, a popover, a menu, a tooltip, a tab set, a form field, a table or a toast.

**`auth/RequireSession.tsx` and `auth/RedirectToSignIn.tsx` render no element at all** (`<Outlet />`,
`<Navigate />`, `null`), so they are outside this task by construction — which also means the `Only` scan must
not demand a primitive of them.

## 3 · The proposed shape

Offered as a proposal, not a decision: **phase 3's challenge is where the two real design questions get
settled** (§7).

### 3a · Install

`tailwindcss` and `@tailwindcss/vite`, pinned exactly (no `^`, matching every other dependency in this
`package.json`). Registry currently serves `4.3.5` for both.

**Tailwind 4 is CSS-first.** There is no `tailwind.config.js` and no PostCSS step: the Vite plugin plus one
`@import "tailwindcss";`, with design tokens declared in a `@theme` block in CSS. `tasks.md:31` describes the
gap as *"no Tailwind or PostCSS configuration"*, written when version 3 was the shape; the version-4 reading is
what *"configured"* means here (OQ-T173-6).

### 3b · Where the stylesheet is imported — and where it is not

`src/app/mountShell.tsx` imports it, **not `src/main.tsx`**:
`shellBuildsAndMounts.test.tsx:135` pins the entry to *"a single `mountShell` call"*, and an `import './x.css'`
added there would fire a case about something else entirely. `mountShell` is also where the i18n provider and
the shell are composed, so the stylesheet joins the things the shell brings with it.

### 3c · The primitive layer

`src/app/ui/` — **inside `app/`, which `design.md` §6's file map does list**, rather than a new top-level
directory the map has no entry for. Seven files, one per shape in §2, and no more:

| primitive | wraps | keeps |
|---|---|---|
| `Screen` | `<main>` | the landmark |
| `Heading` | `<h1>` | one level-one heading per screen |
| `Text` | `<p>` | an optional `id`, for `aria-describedby` |
| `ActionButton` | `<button type="button">` | `aria-describedby`, `aria-disabled`, `aria-busy`, the ref, the handler |
| `TextLink` | react-router's `<Link>` | `to` |
| `StatusRegion` | `<div role="status" aria-live="polite">` | both attributes, always rendered |
| `FocusHolder` | `<div tabIndex={-1}>` | the ref `RateLimitNotice` focuses when the button unmounts |

Each forwards its ref and takes `children`, and **none takes a `className`**: a primitive that accepted one
would let a screen style around it and the `Only` claim would become decorative. The look lives in the
primitive; the screen names a primitive.

### 3d · What is deliberately NOT installed

**No headless component library, for now.** O2 says *"headless/unstyled components (**e.g.** Radix UI)"* —
an example, not a decision (phase 1, §5) — and §2 shows there is no surface in the tree for one to provide. A
dependency added now would be unused code in the bundle, against L10's spirit and R58's scan, and the first
screen that needs a dialog (MFA enrollment, R11–R13) is the right place to add it with a real consumer.

**This is the task's biggest judgement call and the thing phase 3 should attack hardest.** It is recorded here
as a deviation-in-waiting rather than slipped in: the task statement's words are *"Tailwind CSS with
headless/unstyled accessible primitives"*, and the position taken is that **`src/app/ui/` IS the unstyled
accessible primitive layer** — ours, not a vendor's, which is also what Q7's *"fully custom look, no fighting a
pre-themed library"* asks for.

### 3e · The token set

Nothing in the package states a palette, a type scale or a spacing unit (phase 1, §5). The proposal is the
smallest set the existing screens actually need, declared in `@theme` and documented with the reason each value
exists: two text colours, one surface, one accent for the control, one focus ring, a type scale of three steps,
and Tailwind's default spacing. Every pair is chosen to **meet WCAG AA contrast**, which is the one visual thing
L11 does require — and the one claim a test can hold. See OQ-T173-3: if the owner has a visual direction, this
is the part to replace, and it is confined to one `@theme` block for exactly that reason.

## 4 · Acceptance criteria

| | criterion | source |
|---|---|---|
| **AC1** | Tailwind is installed at an exact version and configured through `@tailwindcss/vite`; the stylesheet reaches the **built output** as an emitted asset, not only the dev server | O2, L13 |
| **AC2** | **Every** one of the five render sites renders through `app/ui/` primitives; **no** screen renders a bare `<main>`, `<h1>`, `<p>`, `<button>`, `<div>` or `<Link>` of its own | `tasks.md:37`, the named test's `Only` |
| **AC3** | The primitive inventory is **exhaustive and pinned**: a screen that reaches past the layer fails, and a primitive added without a consumer fails | the `Only`, T40's AC13 pattern |
| **AC4** | No primitive accepts a `className`, so a screen cannot style around the layer | AC2's integrity |
| **AC5** | Every existing accessibility property survives **by assertion, not by inspection**: the landmark, one `<h1>` per screen, `role="status"` + `aria-live="polite"` always rendered, `aria-describedby`/`aria-disabled`/`aria-busy` on the control, and `tabIndex={-1}` on the notice's focus holder | L11, agents.md:93-96 |
| **AC6** | `RateLimitNotice`'s managed focus still works: dismissing moves focus to the holder rather than dropping it to `<body>` | L11 |
| **AC7** | Every string still comes from the catalogue; **no primitive contains user-facing copy** | L11, agents.md:97 |
| **AC8** | Every screen's **rendered text is unchanged**, word for word, before and after | L12, agents.md phase discipline |
| **AC9** | Colour pairs meet **WCAG AA** contrast, computed from the token values rather than asserted by eye | L11 |
| **AC10** | No new storage key, no network call, no new environment key, no secret in the bundle | L10, L16, R58 |
| **AC11** | The route set, the catalogue and every `data-testid` are unchanged | L14, L12 |
| **AC12** | Instruments of the closed tasks are **grown, not bypassed** — 13 suites assert `innerHTML`, 11 use `querySelector`, 8 read `tagName`, and `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:807` pins *"one landmark holding a level-one heading, a paragraph and the way back, in that order"* | the handoff pattern |

## 5 · Files

**New:** `src/app/styles.css` · `src/app/ui/` (seven primitives) ·
`src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`

**Modified:** `package.json` · `pnpm-lock.yaml` · `vite.config.ts` · `app/mountShell.tsx` · `app/AppShell.tsx` ·
`app/RateLimitNotice.tsx` · `auth/SignInScreen.tsx` · `auth/CallbackScreen.tsx` · `auth/SignedOutScreen.tsx`
· and whichever closed-task instruments fire (phase 5 enumerates them **in advance**; T41's phase 5 predicted
seven and twenty-six fired, so the count here is predicted per-suite and then **measured** before being
believed).

**Not to modify:** `spec/**` · `i18n/catalogue.ts` · `app/routes.tsx` · the two route arrays ·
`auth/accessToken.ts`, `returnTarget.ts`, `cleanReauth.ts`, `RequireSession.tsx`, `RedirectToSignIn.tsx` ·
`pwa/**` · `api/**` · `src/main.tsx` (§3b).

## 6 · Constraints

- **This task restyles; it does not redesign.** No copy change, no structural change, no behaviour change. A
  primitive that dropped an ARIA attribute would be a regression against L11 wearing progress as a disguise.
- **jsdom applies no CSS.** No test here can prove a colour renders; what tests can prove is which *class
  names* and which *token values* are present, and that contrast is satisfied **arithmetically**. `e2e/`
  (Playwright, L13) does not exist, so there is no visual check anywhere in this repository, and the brief says
  so rather than implying coverage it does not have.
- **`aria-disabled`, not `disabled`** — `SignInScreen.tsx`'s comment explains why (a focused element that
  becomes disabled drops the keyboard user to `<body>`). A primitive must not "helpfully" translate one into the
  other.
- The known host failure in `api/lintRejectsHandWrittenBackendFetch.test.ts` stays the only acceptable red.

## 7 · Open questions — two for phase 3, one for the owner

- **OQ-T173-1 → phase 3:** does this task add a headless library at all? §3d argues no, with reasons. This is
  the design challenge's main subject.
- **OQ-T173-2 → phase 3:** `src/app/ui/` versus a top-level `src/ui/`, when `design.md` §6's file map lists
  neither. Either way it is a deviation; §3c picks the one inside a directory the map does list.
- **OQ-T173-3 → the owner, and it does not block:** there is **no stated visual direction** anywhere in the
  package. The proposal invents the smallest token set the screens need and confines it to one `@theme` block so
  that replacing it later is a one-file change. Flagged loudly rather than presented as if the spec had asked
  for it.
- **OQ-T173-4** (carried): `phase1ScreensPassAccessibilityChecks` maps to L11 in `package.md` §8 and **no task
  claims it**. Not adopted here — it would need tooling this statement does not authorize.
- **OQ-T173-5** (carried): L13 names TanStack Query and Zustand; neither is installed.
- **OQ-T173-6** (carried): the version-4 reading of *"configured"*.

**Phase 2 complete.** The whole element surface enumerated (six shapes, five sites), a seven-primitive layer
proposed, the no-vendor-library judgement stated as the challenge's main target, twelve criteria, and the
visual-direction gap flagged for the owner without blocking on it.
