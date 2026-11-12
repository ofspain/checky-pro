<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T173 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Claude Opus 5 |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md`, `artifacts/11-mutation-run.txt` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | **52 / 52** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1832 / 1833** — the known host `oxlint@1.87.0` case, unrelated to this task |
| `python3 -I artifacts/07-mutation-harness.py` | **19/19 regressions killed, 0 survivors, 0 BROKEN, 1 canary killed, 0 disagreements** |

Eighteen of the twenty rows die in files this task owns or grew. The two that do not — RD12 and RD16 — are
correct to: the shell's `data-testid` belongs to task 01's and task 16's suites, and AC4's real gate is `tsc`
through the end-to-end build case.

---

## 1 · Traceability

| Requirement / decision | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **task 173** *"Tailwind CSS … installed, configured, and applied to every existing screen"* | **yes** | `package.json` two exact pins · `vite.config.ts:22` · `app/styles.css` · `app/ui/` ×8 · five render sites | AC1, AC2/AC3, AC14, AC15 | — | — |
| **task 173** *"render through its primitives rather than bare elements"* | **yes** | no lowercase JSX tag in any render site | AC2/AC3, 8 cases + 4 controls; RD13, RD14 | — | the sweep is text-based, not AST-based — pinned as a control (F11) |
| **task 173** *"Verifies `screensRenderThroughDesignSystemPrimitivesOnly`"* | **yes** | `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`, 52 cases | the file itself | — | — |
| **O2** *"Tailwind CSS + headless/unstyled components (e.g. Radix UI)"* | **yes** | `app/ui/` is the unstyled primitive layer | AC2/AC3 | no vendor library | **deliberate (§6a).** `e.g.` is an example; no render site has a dialog, menu, popover, tooltip, tab set, form field, table or toast. Revisit trigger named: the first composite control with managed focus (MFA, R11–R13) |
| **L11** WCAG AA | **yes** | `@theme`'s six tokens | AC9 ×5, recomputed from the declarations; RD07 | — | — |
| **L11** keyboard operation | **yes** | `aria-disabled` never `disabled`; `type="button"` | AC5 | — | — |
| **L11** managed focus | **yes** | `FocusHolder`; all eight primitives forward refs; focus ring on all three focusable primitives | AC6 ×2, AC1 ×3; RD08, RD09, RD19 | the focus *move* itself is measured by an existing case | **deliberate.** F7/F8 declined; both owning cases named by exact assertion text so a rename fails a test |
| **L11** live regions for async results | **yes** | `StatusRegion` renders unconditionally | AC5 ×4; RD10, RD11 | — | — |
| **L11** catalogue-only strings | **yes** | no primitive holds copy or calls `t()` | AC7/AC8 ×5 | — | — |
| **L13** confirmed stack | **partly** | Vite, React, TypeScript, React Router, Vitest all in use and unchanged | AC15 | **TanStack Query and Zustand are not installed** | **OQ-T173-5** — pre-existing, named in §5 as out of scope. This task installed no state or data library because no screen has data to fetch or state to share |
| **L12** phase additivity | **yes** | no route, endpoint, storage key or contract changed | AC10 ×3 | — | — |
| **R30** identical visual treatment across the five account states | **yes** | identical, non-empty, class-only attributes | the grown pin + its 2 controls (AC13); RD04, RD17 | — | — |
| **O3** *"a missing key falls back to English, never a raw key"* | **yes**, unchanged | catalogue untouched; 14 key paths pinned | AC7/AC8 — no key may leak into the DOM | — | — |
| **AC12** one stylesheet | **yes** | `app/styles.css` is the only one in `src/` | AC14/AC15 | — | — |
| **`phase1ScreensPassAccessibilityChecks`** (`package.md` §8) | **no** | — | — | **no task in the package commissions it** | **OQ-T173-4**, carried since phase 2 and still the owner's |

## 2 · The four acceptance criteria that changed at the freeze

| | |
|---|---|
| **AC4** *absent from every primitive's type* | satisfied, and **enforced end-to-end**: RD16 breaks `tsc` in two places and the build case's control arm with it |
| **AC9** the three pairs, computed | satisfied — 17.49, 6.71, 5.89 against 4.5, 4.5, 3. A further case asserts the stylesheet's own comments state the computed figures, which is what four wrong numbers got past |
| **AC13** R30: identical **and** non-empty | satisfied. Only a five-file mutant can falsify it; RD04 is that mutant |
| **AC14/AC15** the built asset, and exact pins | satisfied — one non-empty CSS asset, linked with `rel="stylesheet"`, carrying every required utility and token |

## 3 · The principal engineer's four questions

**(1) Is the task fully complete?** Yes. All five render sites go through the primitive layer, Tailwind 4 is
installed and configured CSS-first, the named test exists and is the file `package.md` §8 names, and the one
closed instrument that fired was grown rather than loosened.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1–AC15, with AC2 and AC3 satisfied by the
three-part construction §6e froze rather than by a single scan.

**(3) Does it violate any LOCKED decision?** No. **L13 is the one to look at twice**: it names TanStack Query
and Zustand and neither is installed. That is not a violation introduced here — it was already true before
this task, it is recorded as OQ-T173-5, and §5 placed it out of scope. Installing a data-fetching library
with no data to fetch would be the worse reading of a LOCKED decision: L13 lists the confirmed stack, not an
installation schedule. **L11 is the decision this task is really measured against**, and all five of its
obligations have instruments.

**(4) Remaining risks.**

- **The headline risk is what 19/19 cannot tell you.** Phase 10 ended 16/16 with no survivors and the visual
  contract entirely unmeasured — a primitive could have lost `bg-accent` and every case passed. Kimi's F1
  found it by reading, not by running. The number is now 19/19 against a strictly larger set of rows, but the
  lesson stands and should be read as a limit on this very verdict: **a kill count measures the rows someone
  thought to write.**
- **`RateLimitNotice` is mounted by nothing** (F8/I9), so `FocusHolder`'s layout classes have no production
  consumer and AC6's structural evidence runs only in test trees.
- **No mutant attacks `SignInScreen`'s status region.** RD11's guard fires on `children === null`, true for
  the notice's single expression and false for the sign-in screen's three ternaries. AC5 holds the claim
  there; nothing attacks it.
- **The `Only` sweep reads source text.** A screen written as `React.createElement('div')` would pass. No
  file uses that form, and a row aimed at nothing is its own hazard (T37), so it is recorded, not added.
- **The harness's `in:` file list still over-reports** (phase 9's residual): baseline cases are excluded by
  name, the file list is not. For the eleventh copy.
- **OQ-T173-3 is unchanged and is the owner's**: no visual direction exists anywhere in `spec/frontend/`, so
  the token values are mine. They are confined to one `@theme` block, and contrast is the only part of them
  any requirement constrains.

## 4 · What this task changed outside its own files

Three closed-task instruments were **grown**, never loosened, and one more was corrected:

| file | why it had to change |
|---|---|
| `identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | the pin written for this task by name, 27 tasks early |
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | T37's anchor case followed the router `<Link>` one layer down into `TextLink` |
| `bundleContainsNoSecretEnvKeys.test.ts` | Tailwind's licence banner is a real vendor URL in the build — and the admission is a **pin**, since RD05 and RD06 die on it |
| `unauthenticatedRoutesRedirectToSignIn.test.tsx` | a comment this task made false (I8) |

---

**VERDICT: PASS** — every acceptance criterion is met with instruments behind it, 19/19 mutants die with 18
in this task's own files, and the only unmet spec item (L13's TanStack Query and Zustand) predates the task
and is recorded as the owner's open question.
