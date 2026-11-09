<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T173 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Spec section** | Phase 1 — Foundation: presentation layer (added 2026-10-29) |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement** (`tasks.md:37`): *"The design system O2 resolved — Tailwind CSS with headless/unstyled
accessible primitives — is installed, configured, and applied to every existing screen, which render through its
primitives rather than bare elements. Verifies `screensRenderThroughDesignSystemPrimitivesOnly`. Cites L11, L13.
**Execute before 42.**"*

`tasks.md:31` says why it exists: *"O2 resolved the design system on 2026-10-10 and no unit implemented it, so
27 completed units have shipped against an unstyled tree: no CSS file, no Tailwind or PostCSS configuration, no
`className` in any production component."* **Verified, all three:** `grep -rn 'className\|style=' src/` outside
tests returns nothing, there is no `.css` file anywhere under `frontend/`, and `vite.config.ts` has no PostCSS
or Tailwind entry.

## 1 · Architecture summary

A Vite + React 19 + TypeScript SPA, served under the `/app` base path and reached only through the edge (L9,
L14). **No persistence, no events, no outbox** — those are backend concepts; this service's equivalents are:

| | |
|---|---|
| **Modules** | `app/` (shell, router, rate-limit notice), `auth/` (OIDC flow, session owner, guards, the new redirect), `api/` (generated-client wrapper, 401/429, cross-tab renewal and sign-out), `profile/` (two readers, **dormant — OQ-T33-2**), `i18n/` (the catalogue), `pwa/` (hand-written service worker, navigation denylist) |
| **Not yet created** | `features/` — the whole `design.md` §6 subtree (`account/`, `mfa/`, `invoices/`, …) is planned and empty. Phase 1's units have all landed in `app/` and `auth/` |
| **"Persistence"** | the access token is **in memory only**; `sessionStorage` holds exactly L16's inventory, and today that is one key — `themistra.auth.return_to` (`auth/returnTarget.ts:39`). `sessionStorageHoldsOnlyTheL16Items.test.tsx` is the pin |
| **Security posture** | no credential input in this bundle (L2): interactive login runs inside the Spring Authorization Server. No secrets in the bundle (L10, R58) — enforced by a real production build in `app/bundleContainsNoSecretEnvKeys.test.ts`. Tokens are never written to any storage (`auth/tokensAreNeverWrittenToAnyStorage.test.tsx`) |
| **Routing** | one table in `app/routes.tsx`: nine public paths, eleven authenticated behind a single pathless `RequireSession` gate, an index route and a catch-all both carrying `RedirectToSignIn` (T41, closed today) |

## 2 · Existing code this task touches

**Every production site that renders a DOM element — there are five:**

| file | what it renders today |
|---|---|
| `app/AppShell.tsx:6` | `<div data-testid="app-shell">{children}</div>` — one bare div, nothing else |
| `app/RateLimitNotice.tsx:28-38` | `<div tabIndex={-1}>` wrapper, `<div role="status" aria-live="polite">`, a `<button type="button">`. **The one existing managed-focus precedent** (it holds focus on the wrapper so dismissing does not drop a keyboard user to `<body>`) |
| `auth/SignInScreen.tsx:51-76` | `<main>`, `<h1>`, `<p id="sign-in-explanation">`, a `<button>`, and a status region |
| `auth/SignedOutScreen.tsx` | `<main>` / `<h1>` / `<p>` / an `<a>` back to sign-in (task 37) |
| `auth/CallbackScreen.tsx` | the exchange screen (task 5/6) |

`auth/RequireSession.tsx` and `auth/RedirectToSignIn.tsx` render **no element at all** (an `<Outlet />`, a
`<Navigate />`, or `null`), so they are out of scope for a primitive layer by construction.

**Configuration surfaces this task must touch:** `package.json` (dependencies), `vite.config.ts` (the plugin
list), `index.html` or an entry module (where a stylesheet is imported), and whatever CSS entry file is created.
`src/main.tsx` is three statements and a comment today, and `shellBuildsAndMounts.test.tsx:135` pins it to *"a
single `mountShell` call"* — so a `import './x.css'` added **there** would fire that case; added inside
`app/`, it would not.

**There is no `ui/` or `components/` directory, and `design.md` §6's planned tree does not have one.** The tree
lists `app/`, `auth/`, `api/`, `features/*`, `i18n/`, `pwa/` and nothing else. Where primitives live is
therefore not answered by the spec. **I do not know** whether the author intends `app/ui/`, a new top-level
`ui/`, or `features/`-local components; it is a phase 1/3 question, not something to infer here.

## 3 · Established patterns to follow

- **Catalogue-only strings.** Every user-visible word comes from `i18n/catalogue.ts` through `t()` (L11, O3).
  Several suites assert the literal English **and** the catalogue entry, because `t()` returns the key when an
  entry is missing, so comparing `t(k)` to `en[k]` would compare a key to a key.
- **Focus is managed with refs, not autofocus.** `RateLimitNotice.tsx:16-19` is the model: a `tabIndex={-1}`
  wrapper that can receive focus when the element holding it unmounts.
- **ARIA is written by hand today** — `role="status"`, `aria-live="polite"`, `aria-describedby` via
  `id="sign-in-explanation"`. A primitive layer must not silently drop these.
- **A long file header carries the decisions**, including what was considered and rejected, and headers are
  rewritten when a later task makes a statement in them false (T41 did this twice).
- **Source-scan tests**, with comments stripped before scanning, and every forbidden pattern paired with a
  probe that proves the pattern fires. T41's sweep is 37 rows; T40's is 50.
- **One mutation harness per task**, ninth copy now, in `.ai/prompts/frontend/T<n>/artifacts/`. It reverts with
  `git checkout -- frontend/src/`, so tests are committed before it runs.
- **Dependencies are pinned exactly** — no `^`, no `~`, anywhere in `package.json`.

## 4 · Testing conventions

| | |
|---|---|
| **Runner** | Vitest 5 + jsdom, `environment: 'jsdom'` in `vite.config.ts`. **The run recipe is `npx -y pnpm@12.14.0 exec vitest run`** — bare `pnpm` is not on this host, and the **exit code is the verdict**, not the pass count |
| **Rendering** | `createRoot` + `act` directly. **There is no `@testing-library/react` in the tree** — assertions go through `container.querySelector`, `textContent` and `innerHTML` |
| **Real builds** | two suites run a genuine production build: `app/shellBuildsAndMounts.test.tsx:54` (Vite's JS API) and `app/bundleContainsNoSecretEnvKeys.test.ts:212` (the real CLI, because in-process the build inherits `NODE_ENV=test` and ships React's development build) |
| **External tools** | `api/lintRejectsHandWrittenBackendFetch.test.ts` shells out to oxlint — **this is the one known-failing test on this host** (its `oxlint@1.87.0` prints no summary line). `api/generatedClientCompiles.test.ts` shells out to tsc |
| **Current baseline** | **1780 / 1781**, the single failure being that host case |
| **No ArchUnit, no Testcontainers, no fixed `Clock`** | those are backend conventions; the frontend's equivalent of an ArchUnit rule is a source scan |

## 5 · The blast radius, measured rather than guessed

Adding `className` attributes and wrapping elements in primitives changes the DOM, and this suite asserts the
DOM heavily:

- **13 suites** contain an `innerHTML` or `outerHTML` assertion.
- **11 suites** use `querySelector`, `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` 22 times and
  `callbackWithMismatchedStateIsDiscarded.test.tsx` 14.
- **8 assertions** read `tagName` or `nodeName`.
- `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:807` is the sharpest: *"is one landmark holding a level-one
  heading, a paragraph and the way back, **in that order**"*.
- T41's own AC10 case compares a redirected arrival's `innerHTML` to a direct arrival's **byte for byte** — that
  one survives any styling, because both sides change together.

**Two things I expected to be at risk and verified are not:**

- `shellBuildsAndMounts.test.tsx:68-72` asserts every `<script src>` matches `/^\/app\/assets\/[^/]+\.js$/` and
  only reads assets `.endsWith('.js')`. A stylesheet `<link>` is not a `<script>`, so **an emitted CSS asset does
  not fire it.**
- `bundleContainsNoSecretEnvKeys.test.ts` walks the whole output tree, but its only exact-list assertion is for
  files **outside** `assets/` (`['index.html', 'serviceWorker.js']`, line 276) and its JS-count assertion filters
  on `assets/.+\.js` (line 254). A CSS file lands inside `assets/` and is not `.js`, so **it does not fire
  either.**

Stating that precisely matters because the opposite assumption would have driven a worse design — for instance
inlining styles to avoid emitting an asset.

## 6 · Known gaps and unknowns

1. **The headless library is not locked.** O2 (`design.md:74`) and Q7 (`package.md:371`) both say *"Tailwind CSS
   + headless/unstyled components (**e.g.** Radix UI)"*. `e.g.` is an example, not a decision. The five existing
   screens contain **no dialog, dropdown, popover, tooltip or menu** — the surfaces such a library exists for —
   so whether this task should add one at all is a genuine design question for phase 3.
2. **Tailwind's major version and shape.** The registry is reachable from this host and currently serves
   `tailwindcss@4.3.5` and `@tailwindcss/vite@4.3.5` (and `radix-ui@1.8.1`, the unified package). Tailwind 4 is
   CSS-first: a Vite plugin and an `@import` in a CSS file, **no `tailwind.config.js` and no PostCSS step** —
   which changes what the task statement's word *"configured"* refers to. Which version to pin is phase 5's.
3. **What "render through its primitives rather than bare elements" requires** is not defined anywhere in the
   package. It could mean a local primitive layer (`<Button>`, `<Heading>`, `<Text>`), utility classes on the
   existing bare elements, or both. The named test's own name — `screensRenderThroughDesignSystemPrimitivesOnly`
   — leans towards the first, and `only` suggests an exhaustive scan rather than a sample. **I do not know** the
   intended reading; phase 1 must extract it and phase 3 should challenge it.
4. **Neither named test exists.** `screensRenderThroughDesignSystemPrimitivesOnly` and
   `phase1ScreensPassAccessibilityChecks` are both in `package.md` §8 and neither has a file. The second is
   listed in this task's header but is **not** named in the task statement, so whether T173 owes it is unclear —
   and **no accessibility tooling is installed** (no `axe-core`, no `jest-axe`, nothing equivalent), so owing it
   would mean adding a dependency this task's statement does not mention.
5. **No design tokens, no colour palette, no typography scale exist** anywhere in the repository or the spec. A
   "fully custom look, no fighting a pre-themed library" (Q7) with no stated visual direction means the look
   itself is undefined. **I do not know** what the product is supposed to look like; phase 2 must either find it
   or record it as an open question for the owner.
6. **`e2e/` (Playwright, L13) does not exist**, so there is no visual or browser-level check that styling
   actually renders — only jsdom, which applies no CSS.
7. Carried from the closed tasks and relevant here: **task 174** (the home screen) is the sibling of this one and
   also *"execute before 42"*; `/app/home` is still element-free, which is why a completed sign-in lands on a
   blank document with focus stranded on `<body>` (T24-D11, T27-D1).

**Phase 0 complete.** No code written, no spec file touched. Five render sites, three verified absences, two
instruments that turned out **not** to be at risk, and six places where I do not know what the task means yet.
