<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T173 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
feat(frontend): install the design system and move every screen onto its primitives (T173, L11, L13)
```

## Commit message

```
feat(frontend): install the design system and move every screen onto its primitives

O2 resolved the design system as "Tailwind CSS + headless/unstyled
components (e.g. Radix UI)". Tailwind 4 is installed CSS-first - the Vite
plugin and one @theme block, no config file and no PostCSS step - and the
primitives are eight files in src/app/ui/ rather than a vendor library.
"e.g." is an example, and none of the five render sites has a dialog,
menu, popover, tooltip, tab set, form field, table or toast, which is what
such a library exists to make accessible. The revisit trigger is named in
the frozen brief: the first composite control with managed focus, likely
MFA enrollment.

className and style are absent from all eight primitive types, not
optional and not ignored. A screen that styles itself is a compile error,
and that is measured rather than trusted: an existing end-to-end case runs
a real build and asserts today's sources compile, so the type-level
guarantee already has a test behind it.

A pin written 27 tasks early fired exactly as its own comment promised.
identityErrorsIndistinguishableAcrossAccountStates read
toEqual([[], [], [], [], []]) under "A PIN FOR TASK 173, not a claim that
styling will never exist... this case is what fails if one failure is
styled differently from another". It now claims sameness AND non-emptiness
AND classes-only, because five empty lists satisfy sameness perfectly -
which is how it passed for 27 tasks, and why a sameness-only version would
also pass if this whole change were reverted.

R30 is the requirement behind that pin, and phase 1 had concluded there
was no requirement to derive. It searched for a requirement that REQUIRES
styling and found none; it did not search for one that CONSTRAINS styling,
and R30 is the only such requirement in the package.

Three closed-task instruments were grown and none loosened: the R30 pin;
T37's anchor case, which followed the router Link one layer down into the
TextLink primitive; and the bundle's URL allowlist, which now admits
Tailwind's MIT licence banner - an admission that is a PIN, since removing
the stylesheet import or the build plugin now fails it.

Tokens: six colours and three type steps, values chosen here because
nothing in spec/frontend/ states a visual direction (OQ-T173-3). They are
confined to one @theme block, so a supplied direction is the whole diff.
Contrast is the one part any requirement constrains, and all three frozen
pairs clear WCAG AA computed from the declarations: 17.49:1, 6.71:1 and
5.89:1 against 4.5, 4.5 and 3.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

**23 files, +1258 / −38** in `frontend/`, plus 13 pipeline artifacts under `.ai/prompts/frontend/T173/`.

| | |
|---|---|
| **Created — the layer** | `app/styles.css` · `app/ui/{Surface,Screen,Heading,Text,ActionButton,TextLink,StatusRegion,FocusHolder}.tsx` · `app/ui/index.ts` |
| **Created — the test** | `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (855 lines, 52 cases) |
| **Build** | `package.json` (two exact pins, devDependencies) · `vite.config.ts` (the plugin, after `react()`) · `app/mountShell.tsx` (the one stylesheet import — not `main.tsx`, whose imports are pinned) |
| **The five render sites** | `app/AppShell.tsx` · `app/RateLimitNotice.tsx` · `auth/SignInScreen.tsx` · `auth/CallbackScreen.tsx` · `auth/SignedOutScreen.tsx` |
| **Instruments grown** | `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` · `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` · `app/bundleContainsNoSecretEnvKeys.test.ts` |
| **Comment corrected** | `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` (it called `AppShell` "a bare div") |

**No** route, endpoint, contract, storage key, catalogue entry or piece of copy changed.

## Summary

The SPA had three unstyled screens, a shell and a notice, all rendering bare elements. It now has a token
block, eight primitives, and five render sites that contain no lowercase JSX tag at all — the "Only" in the
task statement, enforced by two pinned lists cross-checked against a sweep over every non-test file in `src/`,
because a pinned list cannot see a sixth screen and a glob cannot say which files were deliberate.

Three things worth a reviewer's attention:

1. **The blast radius was 24 suites by grep and one by measurement.** Phase 0 counted suites that touch the
   DOM; the number that break is the number that assert a non-empty exact `innerHTML`, which is zero. The one
   instrument that fired was the pin written for this task.
2. **AC4 is a compile error, not a scan finding**, and the scans say so rather than implying they carry it.
3. **A frozen number was wrong in three ways.** §6e(2)'s "thirteen" is the union of the two pinned lists; the
   sweep finds seven, because `TextLink` renders react-router's `<Link>` and bears no lowercase tag of its
   own; and building the union from the derived set gives twelve. Two reviews caught the first. The others
   needed running the thing.

## Testing performed

| | |
|---|---|
| **Named suite** | 52 / 52 — `screensRenderThroughDesignSystemPrimitivesOnly` |
| **Whole suite** | 1832 / 1833; the one failure is the host's `oxlint@1.87.0` printing no summary line, present before this task |
| **Types · lint** | `tsc --noEmit` exit 0 · `oxlint src` exit 0 |
| **Mutation** | **19/19 regressions killed, 0 survivors, 0 BROKEN, 1 canary killed, 0 disagreements**; 18 of 20 rows die in this task's own files |
| **Build** | a real Vite build asserts one non-empty CSS asset, linked with `rel="stylesheet"`, carrying every required utility and token — the only place a typo'd Tailwind class is detectable, since it emits nothing and jsdom cannot see it |
| **Reviews** | three Kimi 2.7 gates, 51 findings: 20 at phase 3, 16 at phase 8, 15 at phase 11 |

**The number to distrust is the mutation score.** Phase 10 ended 16/16 with no survivors while the visual
contract was entirely unmeasured — a primitive could have lost `bg-accent` and every case passed. Phase 11's
F1 found it by reading. Three rows were added and the score is now 19/19 against strictly more, but it
remains a count of the rows someone thought to write.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` 173 — *"Design system installed … Verifies `screensRenderThroughDesignSystemPrimitivesOnly`. Cites L11, L13. Execute before 42."* |
| **Requirements** | **R30** (`requirements.md:104`) — recovered at phase 4; phase 1 had found no requirement, having searched for one that *requires* styling rather than one that *constrains* it |
| **LOCKED decisions** | **L11** (accessibility and i18n: WCAG AA, keyboard operation, managed focus, live regions, catalogue-only strings — all five have instruments) · **L13** (confirmed stack) · **L12** (phase additivity: nothing was replaced) |
| **Open decisions** | **O2** resolved and ratified at §6a; **O3** unchanged |
| **Named test** | `package.md` §8 — `screensRenderThroughDesignSystemPrimitivesOnly` |
| **Unblocks** | tasks 42, 43 and everything blocked on 173 |

## Open questions handed to the owner

- **OQ-T173-3** — no visual direction exists anywhere in `spec/frontend/`. The six colours and three type
  steps are mine, confined to one `@theme` block; a supplied direction is the whole diff. Contrast is the only
  part any requirement constrains, and it is computed rather than asserted.
- **OQ-T173-4** — `phase1ScreensPassAccessibilityChecks` is named in `package.md` §8 and commissioned by no
  task in the package.
- **OQ-T173-5** — **L13 names TanStack Query and Zustand; neither is installed.** Pre-existing and scoped out
  (§5): no screen has data to fetch or state to share. Flagged because L13 is a LOCKED decision and the gap
  should be the owner's to close, not quietly inherited by the next task.
- **Pipeline drift** — the phase-13 template still asks for a `Claude Opus 4.8 (1M context)` trailer. The
  trailer above follows the session's current attribution instruction instead. Carried from T41.

**Phase 13 complete.** T173 is ready to merge.
