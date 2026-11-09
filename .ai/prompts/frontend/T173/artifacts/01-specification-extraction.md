<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T173 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## 1 · The finding that shapes this task: there is no requirement

The phase header says *"Scoped requirement IDs: **none cited inline** — derive them in Phase 1 from
`requirements.md`."* Derived, and the honest answer is that **there are none to derive.**

`requirements.md` contains **no** occurrence of *WCAG*, *accessible*, *accessibility*, *focus*, *keyboard*,
*live region*, *screen reader* or *catalogue* — the search returns nothing. The presentation layer has no
EARS-form requirement anywhere in the package. Its entire authority is:

| | source | text |
|---|---|---|
| **L11** | `design.md:47` | *"**[ALL] Accessibility and i18n on every screen.** WCAG AA, keyboard operation, managed focus across redirect and MFA steps, live regions for async results, catalogue-only strings."* |
| **L13** | `design.md:51` | *"**[ALL] Confirmed stack.** Vite; React and TypeScript; React Router; TanStack Query; Zustand; `vite-plugin-pwa` (Workbox); Vitest; Playwright. **Confirmed by the author.**"* |
| **O2** | `design.md:74-75` | *"**Design system.** ~~Not chosen (Q7). Must meet L11.~~ **Resolved (2026-10-10): Tailwind CSS + headless/unstyled components (e.g. Radix UI) for accessible primitives.**"* |
| **Q7** | `package.md:371-372` | the same resolution, plus *"**Fully custom look, no fighting a pre-themed library.** Closed."* |
| **agents.md:93-97** | standing rule, LOCKED, `[ALL]` | *"Every screen, in every phase, is keyboard-operable, manages focus across redirect and MFA steps, announces async results through a live region, and meets WCAG AA contrast."* · *"Every user-facing string is in the catalogue. No hardcoded copy in components."* |

That matters for two reasons. First, **L11 and agents.md are the acceptance surface**, and both are written
per-screen ("every screen, in every phase"), so a criterion that samples one screen does not satisfy them.
Second, **nothing anywhere states a visual requirement** — no colour, no contrast ratio beyond "WCAG AA", no
type scale, no spacing unit, no breakpoint. The task says "installed, configured, and applied"; the spec says
what the *result must satisfy* and nothing about what it should *look like*.

## 2 · L13 and the stack: two of its names are still absent

L13 is **confirmed by the author** and lists eight things. Six are present in `package.json`. **TanStack Query
and Zustand are not installed** — `design.md:189-195` describes server state as *"query cache, invalidated on
mutation and on session change"*, which is TanStack Query's job, and no task has needed it yet because
`profile/readAccount.ts` and `readProfile.ts` have no callers (OQ-T33-2).

**Tailwind is not in L13's list at all.** L13 is the *stack*; O2 is the *design system*. Adding Tailwind is
authorized by O2 and Q7, not by L13 — and the task statement cites L13 because the installation touches the
build (`vite.config.ts`), which L13 owns. Reading L13 as "these eight and nothing else" would forbid the task
its own subject, so the reading taken forward is: **L13 fixes the stack's spine and O2 adds the design system
inside it.** Recorded rather than assumed, because it is the one place the two cited decisions could be read as
contradicting each other.

## 3 · The named tests

`package.md` §8 (§"Phase 1a — Presentation layer (added 2026-10-29)" and §"Phase 1a — Unit-level tests"):

| test | maps to | status |
|---|---|---|
| **`screensRenderThroughDesignSystemPrimitivesOnly`** | **L11** | **this task's. Does not exist.** |
| `homeRouteRendersLandingScreenAndManagesFocus` | R28, L11 | **task 174's**, not this one |
| `shellBuildsAndMounts` | L13 | exists, 8 cases — the suite most likely to be grown here |
| `phase1ScreensPassAccessibilityChecks` | **L11** | **does not exist, and no task in `tasks.md` names it** |

Two extractions worth stating plainly:

- **The name carries the criterion.** `screensRenderThroughDesignSystemPrimitivesOnly` —
  *screens*, plural; *primitives*; and **`Only`**. "Only" is an exhaustiveness word: the claim is not *a screen
  uses a primitive* but *no screen renders anything else*. That is a scan over every screen, with a pinned
  count, in the style of T40's AC13 and T37's AC9 — and `tasks.md:37`'s own wording agrees: *"applied to **every
  existing screen**, which render through its primitives **rather than bare elements**"*.
- **`phase1ScreensPassAccessibilityChecks` is orphaned.** It is in §8, it maps to L11, and no task in
  `tasks.md` claims it — this task's header lists it only because the generator injects every §8 test that maps
  to a cited decision. The task statement does not mention it, and honouring it would mean installing
  accessibility tooling (nothing of the kind is in the tree) that the statement does not authorize. **Carried to
  phase 2 as an open question, not silently adopted.**

## 4 · Contracts

None. `contracts/api/*.yaml` and `token-claims.md` are injected into every header by the generator; this task
sends no request, reads no claim and touches no endpoint. The only contract-like constraint that applies is
**L10/R58** — nothing new may be baked into the bundle — which is relevant only because a CSS build step is a
new bundle input.

## 5 · What the spec does and does not authorize

**Authorized, explicitly:**

| | |
|---|---|
| Tailwind CSS, installed and configured | O2, Q7 |
| Headless/unstyled accessible primitives | O2, Q7 — *"(e.g. Radix UI)"* |
| A fully custom look | Q7 |
| Applying it to **every existing screen** | `tasks.md:37` |
| Touching the build to do it | L13 (see §2) |

**Not authorized, or not answered:**

| | |
|---|---|
| **Which** headless library — or whether to add one at all | O2 says *"e.g."*. An example is not a decision, and none of the five existing render sites has a dialog, dropdown, popover or menu, which is what such a library exists for |
| **Where** primitives live | `design.md` §6's planned tree has no `ui/` or `components/` directory. Adding a top-level directory the spec's own file map does not list is a deviation whichever way it goes |
| **What** the product looks like | nothing in the package states a palette, a type scale or a spacing unit |
| Accessibility **tooling** | `phase1ScreensPassAccessibilityChecks` is orphaned (§3) |
| Changing any screen's **copy, structure or behaviour** | L12 and agents.md's phase discipline: *"Later phases consume earlier ones. They never rewrite them."* This task restyles; it does not redesign. A primitive that dropped `role="status"`, `aria-live="polite"` or `aria-describedby` would be a regression against L11 dressed as progress |
| Changing the **route set** | L14 — untouched here |

## 6 · Acceptance surface, derived

From L11, agents.md:93-97, `tasks.md:37` and the named test's own name:

1. Tailwind is installed, configured, and its stylesheet actually reaches the built output.
2. **Every** existing screen renders through the primitive layer; **no** screen renders a bare styled element
   that the layer should own. Exhaustive, with a pinned count (the `Only`).
3. The primitives are **unstyled by origin and custom by us** — no pre-themed library's look (Q7).
4. Every existing accessibility property survives: keyboard operation, the managed focus in
   `RateLimitNotice.tsx:16-19`, `role="status"`/`aria-live="polite"`, and `aria-describedby`.
5. Every string still comes from the catalogue — a primitive must not introduce copy of its own (L11,
   agents.md:97).
6. No new storage key, no network call, no new environment key (L10, L16, R58) — the standing constraints every
   task in this package carries.
7. The route set and every screen's observable copy are unchanged (L12, L14).

## 7 · Open questions for phase 2 and the phase 3 challenge

- **OQ-T173-1** — Does this task add a headless component library, given that no existing screen has a surface
  that needs one? O2's *"e.g. Radix UI"* permits one without requiring it.
- **OQ-T173-2** — Where do the primitives live, when `design.md` §6's file map has no directory for them?
- **OQ-T173-3** — What is the visual language? Nothing in the package states one. A "fully custom look" with no
  stated direction means this task either invents one (and should say so loudly) or installs the machinery and
  applies a deliberately minimal, documented token set.
- **OQ-T173-4** — Is `phase1ScreensPassAccessibilityChecks` this task's? It maps to L11 and no task claims it.
- **OQ-T173-5** — L13 names **TanStack Query** and **Zustand**, neither installed. Out of scope here, but the
  gap is now recorded against the decision that is "confirmed by the author".
- **OQ-T173-6** — Tailwind 4 (currently `4.3.5` on the registry) is CSS-first: a Vite plugin plus one
  `@import`, with **no `tailwind.config.js` and no PostCSS step**. `tasks.md:31` describes the gap as *"no
  Tailwind or PostCSS configuration"*, which was written expecting the version-3 shape. The word *"configured"*
  in the task statement needs the version-4 reading, and phase 5 pins the version.

**Phase 1 complete.** No requirement exists for this task — L11, L13, O2/Q7 and agents.md are its whole
authority; one potential contradiction between the two cited decisions resolved and recorded; the named test's
own name read as the criterion; six open questions, none of them answered by guessing.
