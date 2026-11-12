<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T174 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` · **Task** T174 — Home screen |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` — **the only specification phases 5–13 use** |

## Task

`spec/frontend/tasks.md` 174: *"`/app/home` renders a landing screen and moves focus to it on arrival, so a
completed sign-in lands somewhere rather than on a blank document. Verifies
`homeRouteRendersLandingScreenAndManagesFocus`. Cites R28, L11, L1. Execute before 42."*

## Purpose

`/app/home` already resolves, already sits behind the session gate, and is already where `returnTarget` sends
a completed sign-in when no allowlisted target was remembered (L1). It renders **nothing**. This task gives
it a screen and moves keyboard focus to that screen on arrival.

## Scope

**In:** one landing screen component · its catalogue entries · attaching it to `HOME_PATH` inside the gate ·
focus management on arrival · the named test · growing the three instruments built to fire here.

**Out:** any data fetch (the authenticated data path is dormant, OQ-T33-2) · any identity-dependent content
(a name, an email, an account state) · navigation to other authenticated routes (task 42's "authenticated
shell") · a dashboard, cards, widgets or metrics · any new route, endpoint, contract, config key or storage
key · installing TanStack Query or Zustand (OQ-T173-5) · claiming `phase1ScreensPassAccessibilityChecks`.

## Business rules

| ID | |
|---|---|
| **R28** | `/app/home` is an authenticated route: the screen sits behind `RequireSession` and an unauthenticated arrival is redirected to the sign-in start. |
| **R7 / L1** | The post-login return target falls back to the home route, which is why this screen must exist. |
| **R22** | The SPA shall not show a half-authenticated screen — this is the first authenticated screen, so it must render one complete state. |
| **R20** | Profile data comes from `/userinfo` and `GET /accounts/me`, never from the access token. |
| **R29 / L16 / L10** | No tokens, no PII, nothing written to storage. |
| **L4** | The unauthenticated arrival stays indistinguishable from any other blocked path (T41 already owns this). |

## Locked decisions

**L1** · **L4** · **L10** · **L11** · **L12** · **L13** · **L14** · **L16**, plus `agents.md`'s LOCKED
catalogue rule (no hardcoded copy in components) and its accessibility rule (keyboard-operable, managed
focus, live region for async results, WCAG AA).

## Dependencies

`app/ui/` (T173's eight primitives) · `i18n/catalogue.ts` · `app/routes.tsx` (`HOME_PATH`) ·
`auth/RequireSession.tsx` (read only). **No endpoint, no contract, no config key.**

## Inputs

None. The screen takes no props, reads no parameter, no query and no storage.

## Outputs

Rendered DOM only: a `<main>` landmark containing exactly one `<h1>` and explanatory copy, plus one focus
move on arrival.

## State Changes

**None.** No storage write, no network call, no module state, no catalogue key removed or renamed.

## Files to create

- the landing screen component (one file)
- `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` — or `src/auth/…`, wherever the component
  lands; the suite sits beside it

## Files to modify

- `app/routes.tsx` — one element on `HOME_PATH`, inside the gate's children
- `i18n/catalogue.ts` — a new `home` group
- `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — the screen-count pin that names this handoff
- `app/routesResolveUnderAppPrefixOnly.test.tsx` — the same total against its hand-edited list
- `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` — `RENDER_SITES` 5 → 6, union 13 → 14

## Files NOT to modify

`auth/RequireSession.tsx` · `auth/RedirectToSignIn.tsx` · `auth/returnTarget.ts` · any file under
`app/ui/` · `app/styles.css` · anything under `spec/`.

## Acceptance criteria

| | criterion | traces to |
|---|---|---|
| **AC1** | `/app/home` renders a `<main>` landmark holding exactly one `<h1>`, built from T173's primitives, with no lowercase JSX tag in the screen file | task 174, T173 §6e |
| **AC2** | On arrival `document.activeElement` is the screen's own element and **not** `<body>`; that element is programmatically focusable and **not** in the tab order | task 174, **L11** |
| **AC3** | Every rendered string comes from the catalogue, asserted against the entries **and** as literal English, with no raw key in the DOM | **L11**, **O3** |
| **AC4** | An unauthenticated arrival at `/app/home` is redirected to the sign-in start and renders no part of the screen | **R28** |
| **AC5** | Rendering writes no storage and makes no network call | **R29**, **L16**, **R20** |
| **AC6** | Nothing it renders depends on identity — no name, email or account state | **R22**, **R20** |
| **AC7** | The three instruments are grown to six render sites, a union of fourteen, and four attached screens, each naming this task | T173 §6e, the pins' own comments |
| **AC8** | No focus trap: focus can leave the screen by keyboard, and the heading is what a screen reader meets after the move | **L11** |

## Required tests

`homeRouteRendersLandingScreenAndManagesFocus` (`package.md` §8 → **R28, L11**), plus the growth of the three
instruments above. A mutation harness (eleventh copy) with at least one row per criterion, each row
predicting `own` / `inherited`, and **controls as well as mutants** — the harness measures strength, only a
control measures precision.

## Constraints

- **Module boundaries:** the screen may import from `app/ui` and `react-i18next`. It must not import from
  `auth/`, must not read `accessToken`, and must not reach `globalThis.location`, `history`, `document.cookie`
  or any storage.
- **Security:** nothing identity-dependent reaches the DOM; no PII, no token, no account state.
- **Null handling:** the screen takes no input, so there is nothing to be null. The focus ref is the one
  nullable thing and `?.focus()` must tolerate it.
- **Performance / thread-safety / transactions:** not applicable to a browser screen with no state.
- **Phase additivity (L12):** task 42 nests the authenticated shell around this; it must be something to
  build on, not a placeholder to delete.

## Open questions

**No blockers.** Three carried, none of which stops work:

- **OQ-T174-1** — the screen's copy is unspecified anywhere in the package. Proceeding with the minimum that
  is not blank, confined to catalogue entries so the owner can rewrite it without touching a component.
  (OQ-T173-3's shape.)
- **OQ-T174-2** — L11 says *"manages focus"* and does not say to what. `<main>`, `<h1>` and a dedicated
  holder are not equivalent to a screen reader. **This is phase 3's target.**
- **OQ-T174-3** — `phase1ScreensPassAccessibilityChecks` is in this task's header and commissioned by no
  task. Not claimed here.
