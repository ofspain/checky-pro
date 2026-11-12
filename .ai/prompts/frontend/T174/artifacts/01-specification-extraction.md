<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T174 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## 0 · How this extraction was done, because the last one got it wrong

T173's phase 1 concluded *"there is no requirement to derive"* and was **corrected at phase 4 by running the
suite**: it had searched for a requirement that **requires** styling, found none, and never searched for one
that **constrains** styling. R30 was sitting there the whole time.

So this sweep was run twice on purpose. The first pass asks *what requires a home screen* — the answer is
thin, and it is honest to say so. The second asks **what constrains a screen once it exists**, and that pass
is where the content is: four requirements and two LOCKED decisions bear on this screen without ever
mentioning it.

## 1 · Business rules

### Requires

| ID | one sentence |
|---|---|
| **R28** | While unauthenticated, the SPA serves only the public routes in `design.md` §4c and redirects anything else to the sign-in start — and `/app/home` is on the **authenticated** list, so the screen this task attaches must sit behind the existing gate and must not become reachable without a session. |
| **R7 / L1** | A completed sign-in returns to an allowlisted target, **falling back to the home route** — so the route is already the landing place; only its content is missing. |

### Constrains — the pass T173's phase 1 skipped

| ID | one sentence |
|---|---|
| **R22** | The SPA *"SHALL NOT show a half-authenticated screen"* and shows neutral copy on failure — and **the home screen is the first authenticated screen in the application**, so this is where that prohibition stops being theoretical: it must render a complete state or not be the thing on screen. |
| **R20** | Profile data comes from `/userinfo` and `GET /accounts/me`, **never from the access token** — so any landing screen that greeted a visitor by name would be a network call, not a free read of a claim. |
| **R29 / L16 / L10** | No tokens, no PII, nothing in storage outside the four permitted items — a landing screen stores nothing. |
| **L4** | Enumeration-safe copy on every public or low-trust surface; the home screen is behind the gate, but the **unauthenticated** arrival at `/app/home` is already answered by T41's redirect and must stay indistinguishable from any other blocked path. |

### Shapes the result

| ID | one sentence |
|---|---|
| **L11** (+ `agents.md` §Accessibility) | Every screen is keyboard-operable, **manages focus**, announces async results through a live region, meets WCAG AA, and takes **every** user-facing string from the catalogue. |
| **L12** | Phase additivity: phases 2–5 *re-use* the Phase 1 shell and routing — so this screen is a foundation others nest in, not a placeholder to be replaced. |
| **O3** | A missing catalogue key falls back to English, **never a raw key**. |
| **T173 §6c/§6e** | Screens render through the eight primitives; `className` and `style` are absent from their types. |

## 2 · Locked decisions that constrain this task

**L1** (public PKCE client; return target falls back to home) · **L4** (enumeration-safe surfaces) ·
**L10** (no secrets in the bundle) · **L11** (accessibility and i18n) · **L12** (phase additivity) ·
**L13** (confirmed stack) · **L14** (route set under `/app`, §4c verbatim) · **L16** (the sessionStorage
carve-out) · plus `agents.md`'s catalogue rule, which is LOCKED and `[ALL]`.

## 3 · Files involved

| | |
|---|---|
| **Extend** | `app/routes.tsx` — attach one element to `HOME_PATH`, inside the gate's children |
| **Extend** | `i18n/catalogue.ts` — a new `home` group; there is none today |
| **Extend** | the two route count pins and T173's `RENDER_SITES`, each of which names this handoff in its own comment |
| **New** | the landing screen component — **I do not know** whether it belongs in `app/` or a new directory; phase 2 decides, and T173's sweep now reads all of `src/`, so either is visible |
| **New** | `homeRouteRendersLandingScreenAndManagesFocus` — `package.md` §8 maps it to **R28, L11** |
| **Read, do not change** | `auth/RequireSession.tsx`, `auth/returnTarget.ts`, `app/ui/*` |

## 4 · Dependencies

No endpoint, no contract, no config key, no storage key, no event. The screen's only dependencies are
`app/ui/` (T173), the i18n catalogue, and the route table. **`contracts/api/*.yaml` are listed in this task's
header and none of them is reachable from it** — the authenticated data path is dormant (OQ-T33-2), and
nothing in the task statement asks this screen to fetch anything.

## 5 · Acceptance criteria

| | criterion | traces to |
|---|---|---|
| **AC1** | `/app/home` renders a landing screen: a `<main>` landmark holding exactly one `<h1>`, built from T173's primitives, with no bare element in the screen file | task 174, T173 §6e |
| **AC2** | Focus moves to the screen on arrival — `document.activeElement` is the screen's own element and **not** `<body>` — and the element is programmatically focusable but **not** in the tab order | task 174, **L11**, `agents.md` |
| **AC3** | Every string it renders comes from the catalogue, asserted against the entries **and** as literal English, with no raw key reaching the DOM | **L11**, **O3** |
| **AC4** | The route stays behind `RequireSession`: an unauthenticated arrival at `/app/home` is redirected to the sign-in start and renders no part of the screen | **R28**, and T41's existing instrument |
| **AC5** | Rendering it writes no storage and makes no network call | **R29**, **L16**, **R20** |
| **AC6** | It shows nothing that depends on identity — no name, no email, no account state — so there is no half-authenticated state for it to be caught in | **R22**, **R20** |
| **AC7** | The two route count pins and T173's `RENDER_SITES` are **grown** to six render sites and a union of fourteen, each naming this task | T173 §6e, the pins' own comments |
| **AC8** | It is keyboard-reachable and contains no focus trap; the heading is the first thing a screen reader meets after the move | **L11** |

## 6 · Open questions

- **OQ-T174-1 — what does the landing screen say?** Nothing in `requirements.md`, `design.md`, `package.md`
  or `tasks.md` describes its content; §4c lists `/app/home` as a path and no more. The task statement's own
  justification is *"so a completed sign-in lands somewhere rather than on a blank document"*, which argues
  for the **minimum that is not blank** — a heading and a sentence — rather than a dashboard. **This is
  OQ-T173-3's shape repeating**, and the answer should be confined the same way: new catalogue entries the
  owner can rewrite without touching a component.
- **OQ-T174-2 — what exactly takes focus?** L11 says *"manages focus"*; it does not say to what. The
  `<main>`, the `<h1>` and a dedicated holder are **not equivalent** to a screen reader, and T173 shipped
  `FocusHolder` for a different purpose (catching focus a disappearing button would have dropped). Phase 2
  must choose deliberately and phase 3 should challenge it.
- **OQ-T174-3 — does this task claim `phase1ScreensPassAccessibilityChecks`?** It is in this task's named-test
  header, is commissioned by no task (OQ-T173-4), and T174 is the closest any task comes to its subject. I am
  not claiming it without the owner saying so; a task that verifies a test it was not given is how a package
  loses track of what is covered.

**Phase 1 complete.** Two requirements require, four constrain, eight acceptance criteria — and the
constraining pass is the one that produced AC5 and AC6.
