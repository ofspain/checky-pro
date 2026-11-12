<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T173 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `artifacts/10-mutation-run.txt`, `artifacts/10-mutation-run-final.txt` |

## Gates

| Command | Result |
|---|---|
| `vitest run src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | **34 / 34** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1814 / 1815** — the known host `oxlint` case |
| `python3 -I artifacts/07-mutation-harness.py` | **16/16 killed, 0 survivors, 0 BROKEN, 1 canary killed, 0 disagreements** |

Fourteen of the seventeen rows now die in this task's own files. The four survivors phase 9 handed over as
this phase's brief — RD07, RD13, RD14, RD15 — are all killed by the named suite, and so are the two that only
`inherited` kills reached before (RD08, RD09).

---

## 1 · The manifest

`src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` — 34 cases in nine sections.

| § | cases | AC / requirement | what it holds |
|---|---|---|---|
| **AC2/AC3** | 8 | AC2, AC3, §6e | the `Only`, in all three parts: both pinned lists (sizes, sorted, disjoint); the derived sweep over every non-test file under `src/app/` + `src/auth/`; no lowercase JSX tag in any render site; barrel-only imports; **four controls** |
| **AC4** | 4 | AC4, F5, F6/I5 | no `className`/`style` in any of the eight prop types · **no `className` literal contains a newline** · a planted prop is caught · the build case that actually enforces it is named |
| **AC5** | 4 | AC5, L11 | per screen: one `<main>`, one `<h1>`; the sign-in control's `type`/`aria-describedby`/`aria-disabled`/`aria-busy` and **no `disabled`**; one `role="status"` with `aria-live="polite"`, empty before anything happens; the notice's `tabIndex="-1"` holder |
| **AC6** | 2 | AC6, F9 | **all eight primitives forward their ref to the real element**, with the tag asserted per primitive · the existing focus case is named |
| **AC7/AC8** | 4 | AC7, AC8, AC11, F17 | each screen's text against the catalogue **and** as literal English; no key leaked into the DOM; the catalogue's 14 key paths pinned; no primitive holds copy or reads `t()` |
| **AC10** | 2 | AC10 | no storage write and no `fetch` while a screen renders; no storage key, endpoint or URL in any primitive |
| **AC9** | 5 | AC9, §6d | the six colour tokens pinned; the three frozen pairs recomputed from the declarations, each **printing the computed ratio**; the comments must state the computed figure; a control at the 4.5:1 boundary |
| **AC14/AC15** | 3 | AC14, AC15, AC12, I13 | the build emits exactly one non-empty CSS asset, `index.html` links it, the tokens **and** the utilities are in it; both dependencies pinned exactly as devDependencies at the same version; one stylesheet in all of `src/` |

**Seven controls**, which is the half T40's AC7 shipped without: the sweep catches a planted bare element;
flags no fragment, component tag, spread or expression (F20); reads code and not prose; treats a TypeScript
generic as neither a tag nor a reason to go blind; a planted `className?: string` is caught in the *type*; a
pair just under 4.5:1 is reported failing; and black-on-white computes to 21:1.

## 2 · Writing the case that disarms I7's trap, I fell through its second floor — twice

§6e(2) says *"after this task there must be thirteen."* Phase 7's F7 and Kimi's I7 established that thirteen
is the **union of the two pinned lists**, and that a test asserting it over the derived set would fail. Both
reviews stopped there. Measurement found two more floors:

**The sweep finds SEVEN, not eight.** `TextLink` renders react-router's `<Link>` — a component tag — so it
bears no lowercase tag of its own. That is not a defect: §6c and challenge F14 *required* it (`to`, never
`href`), and RD03 kills a bare anchor. But the brief, both reviews and my own phase-7 F7 all assumed the
derived set would be the eight primitives. The case now pins **seven**, names the single exception, and
asserts its reason — that the file still renders `<Link>` — so one excused file cannot silently become two.
This is T35's AC7 lesson arriving on a different instrument: *a pinned count is only as honest as the reason
attached to each exception.*

**Then I built the union from the derived set and got 12.** Thirteen is a statement about what the two lists
**declare**, never about what the sweep **finds**. The case asserts both separately, plus the cross-check
§6e(2) actually asks for: every file the sweep found is in one of the two lists.

So the frozen brief's one number was wrong in three different ways, and the third was only reachable by
running the thing.

## 3 · The scan's first version put two JSX-free files in the derived set

`auth/pkce.ts` and `auth/returnTarget.ts` were reported as rendering a `<string>` element:
`Promise<string>` and `Set<string>`. A JSX tag's `<` is never preceded by an identifier character and a
generic's always is, so the fix is a lookbehind — **and the fix is the dangerous half**. Narrowing a scan
until it passes is how a sweep stops seeing the thing it was written for, so the control asserts the
narrowed scan still finds `<span>`, `<div>` and a self-closing `<p />`. A scan is only as good as the
measurement that it still fires.

Comment-stripping is in the same family and was anticipated: four of the five render sites discuss `<div>`,
`<body>` or `<main>` in prose — `AppShell`'s own header names the `<div>` its primitive renders — so a
tag-counting scan over raw text would report the layer broken while it was intact. That direction is the worse
one: it trains the next person to loosen the scan rather than to fix the code.

## 4 · AC6 does not re-render the 429 flow, deliberately

`RateLimitNoticeState` exposes only `isShown`, `subscribe` and `dismiss`. Showing the notice means driving a
real rate-limited response through `createAuthApi`, which
`rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` already does — and that case already asserts the
active element **is** the holder and is **not** `<body>`. A second copy of that harness would measure the same
claim with more machinery, and phase 7's F5 recorded the rule: *an existing passing instrument is stronger
evidence than a criterion I write to replace it.*

So AC6 measures the **mechanism** that case depends on and nothing else does: every primitive forwards its ref
to the element it renders, tag by tag. That is exactly what RD08 and RD09 break, and both moved from
`inherited` to `own` — predicted in phase 10's commit message *before* the run, not after it.

Two cases name an instrument by case name — AC4's build gate and AC6's focus case — so a rename breaks a test
instead of quietly orphaning a citation. Phase 7's F5 and phase 6's D7 are both about a claim that pointed at
something which was not there; this is the cheapest way to stop writing them.

## 5 · The expectation field after phase 10

Eight rows carried `phase 10`, meaning *nothing can kill this until the named suite exists*. The suite exists
and killed all eight, so the run printed nine disagreements. **Each row's expectation is now updated, and each
keeps its original prediction in a trailing comment.** The distinction matters more than the edit: a `phase 10`
label is a prediction about *time*, and the predicted time arrived. An expectation quietly rewritten to match
an outcome is how a harness stops measuring anything — which is why the old value stays readable beside the new
one, and why both runs are kept: `10-mutation-run.txt` is the honest first run with nine disagreements, and
`10-mutation-run-final.txt` is the confirmation at `disagreements: none`.

RD12 and RD16 remain `inherited` and are correct to be: the shell's `data-testid` belongs to task 16's and
task 01's suites, and AC4's real gate is `tsc` through the build case. Phase 10 did not try to drag either
into this task's files just to make a column uniform.

## 6 · What is still not covered, stated rather than implied

- **No row covers `SignInScreen`'s status region being conditional.** RD11's guard fires on
  `children === null`, which is true for `RateLimitNotice`'s single expression and false for the sign-in
  screen's three ternaries (an array of nulls). AC5 asserts the region is present and empty there, so the
  claim is held; what is missing is a *mutant* that attacks it.
- **AC9 cannot catch a wrong colour, only a wrong ratio.** Nothing in the package states a visual direction
  (**OQ-T173-3**), so the thresholds are the whole claim.
- **`FocusHolder`'s layout classes have no production consumer** (F8/I9): `RateLimitNotice` is mounted by
  nothing, so AC6's structural evidence runs only in test trees.
- **The `Only` sweep reads source text**, so a screen written as `React.createElement('div')` would pass. No
  file in the package uses that form; the row is cheap and is *not* added, because a pattern aimed at nothing
  is its own hazard (T37's lesson).
- **The harness's `in:` file list still over-reports** (phase 9's residual): cases are excluded by name at
  baseline, the file list is not. Fix belongs to the eleventh copy.

## 7 · Open questions

Unchanged: **OQ-T173-3** · **OQ-T173-4** (`phase1ScreensPassAccessibilityChecks` is a named test no task
commissions) · **OQ-T173-5** (L13 names TanStack Query and Zustand; neither installed).

**Phase 10 complete.** 34 cases, 7 controls, 16/16 mutants killed by this task's own cases where predicted,
0 survivors, 0 broken, 0 disagreements.
