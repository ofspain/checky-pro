<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T173 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-freeze-task.md` (FROZEN), `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## Gates

| Command | Result |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1780 / 1781** — the one failure is the known host `oxlint@1.87.0` case in `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, unchanged by this task |
| `vitest run src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | 58 / 58 |
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | 49 / 49 |
| `vitest run src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` | 50 / 50 |

No test file was loosened. Two were **grown**, both named in phase 5's prediction table or forced by a
measurement recorded below as a deviation.

## 1 · What changed

### Created — `src/app/styles.css` (the only stylesheet in the repository)

`@import "tailwindcss";` then one `@theme` block: six colours and three type steps. Tailwind 4 is CSS-first,
so there is **no `tailwind.config.js` and no PostCSS step** — `--color-accent` is what makes `bg-accent` exist,
and `--text-step-0` is what makes `text-step-0` exist. Every token's comment names the screen that needs it,
so an unused token reads as a comment with no subject.

The values are mine, not the owner's: **OQ-T173-3** stands — nothing in `spec/frontend/` states a visual
direction. The block is deliberately the whole diff if one arrives. What the spec *does* require is contrast
(L11, "WCAG AA"), so each colour's comment carries its measured ratio: text **17.5:1**, muted **7.9:1**,
inverse on accent **6.7:1**, focus ring **5.9:1** against the 3:1 that WCAG 2.1 §1.4.11 asks of non-text.

> **Corrected after phase 7's F1, at the owner's request.** This paragraph first read *"text 15.9:1, muted
> 7.2:1, inverse on accent 7.0:1, focus ring 3.4:1"*, and all four were wrong — the focus pair by 73%. Every
> threshold cleared then and clears now, so no colour was re-picked; what was wrong was the arithmetic
> reported for colours that were right. See `07-self-review.md` F1.

### Created — `src/app/ui/`, eight primitives and an `index.ts`

| primitive | element | notes |
|---|---|---|
| `Surface` | `div` | page background + text colour; takes `data-testid` |
| `Screen` | `main` | the landmark; one per screen |
| `Heading` | `h1` | **no `level` prop** — nothing in the package needs an `h2` yet |
| `Text` | `p` | takes `id`, for `aria-describedby` |
| `ActionButton` | `button type="button"` | **no `disabled` prop** — `aria-disabled` only, so the control stays focusable |
| `TextLink` | router `Link` | `to` only, **no `href`** |
| `StatusRegion` | `div role="status" aria-live="polite"` | renders **unconditionally**; the ternary goes inside it |
| `FocusHolder` | `div tabIndex={-1}` | children required |

Every one uses `forwardRef` and takes `children`. **`className` and `style` are absent from all eight types**,
which is AC4's real instrument: the violation is a compile error, not a scan finding. The `className` strings
live inside the primitives and nowhere else.

Each primitive's utilities were verified to emit real CSS against a probe consumer and a **real Vite build**
(15/15 classes found in the emitted asset, every token present), because a typo'd Tailwind utility emits
nothing at all and fails silently — jsdom would never notice.

### Modified

| file | change |
|---|---|
| `package.json` · `pnpm-lock.yaml` | `tailwindcss` and `@tailwindcss/vite`, exact versions, `devDependencies` (AC15) |
| `vite.config.ts` | `tailwindcss()` after `react()`, with a comment in the file's existing style |
| `app/mountShell.tsx` | `import './styles.css';` first — **not `main.tsx`**, whose two imports and two statements are pinned by `shellBuildsAndMounts.test.tsx:135` |
| `app/AppShell.tsx` | `<Surface data-testid="app-shell">`. The `data-testid` stays in `AppShell`, not in the primitive: it is this component's identity |
| `app/RateLimitNotice.tsx` | `FocusHolder` → `StatusRegion` + `ActionButton`, both refs preserved |
| `auth/SignInScreen.tsx` | `Screen` / `Heading` / `Text id` / `ActionButton` / `StatusRegion` |
| `auth/CallbackScreen.tsx` | `Screen` / `Heading` / `StatusRegion` |
| `auth/SignedOutScreen.tsx` | `Screen` / `Heading` / `Text` / `TextLink` |
| `app/bundleContainsNoSecretEnvKeys.test.ts` | one vendor URL admitted — **D2** |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | T37's anchor case grown one layer down — **D3** |
| `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | R30's pin grown — **§3** |

## 2 · The prediction against the measurement

Phase 5 predicted **1 certain, 5 to verify**. Measured: **three fired, three did not**, and one of the three
that fired was not the case the table named.

| | instrument | predicted | measured |
|---|---|---|---|
| **1** | R30's pin, `identityErrors…:1433` | **certain** | **fired**, exactly as described. Grown — §3 |
| **2** | `shellBuildsAndMounts:102` *"mounts the empty shell"* | verify | **did not fire**, and the source says why rather than the green suite: it asserts `shell?.innerHTML`, i.e. the element's **children**. A class on the element itself is not a child. The `AppShell` header now records this |
| **3** | `shellBuildsAndMounts:93`/`:127` | verify | **did not fire**. `:127` scans production source for `https?://` and `styles.css` carries no URL; the plugin line changed no counted list |
| **4** | the rate-limit suite's four `querySelector`s | verify | **did not fire**. 50/50, including the two `document.activeElement` assertions at `:399-400` — the task's one real behavioural risk, and the existing suite is what cleared it |
| **5** | `publicRouteSet…:807`, the landmark-order case | verify | **did not fire**, as predicted, because it queries tags and the primitives render the same tags. **But a different case in the same file did** — D3 |
| **6** | `bundleContainsNoSecretEnvKeys` | verify | **fired**, in a form the plan did not anticipate — D2 |

Instrument 5's prediction carried a rule written in advance: *if it fails, the primitive changed the DOM and
the primitive is wrong, not the pin.* It did not fail, so the rule was never needed — but D3 is the reason it
was worth fixing the direction of reasoning beforehand, because the case that **did** fire in that file is one
where the opposite conclusion was correct.

## 3 · R30's pin, grown

`identityErrorsIndistinguishableAcrossAccountStates.test.tsx` read
`expect(seen.map((row) => row.attributes)).toEqual([[], [], [], [], []])`, under a comment that named this
task: *"A PIN FOR TASK 173, not a claim that styling will never exist… When the design system lands, this
case is what fails if one failure is styled differently from another — which is the whole reason to write it
before there is any styling to check."* The handoff was written 27 tasks early and it worked.

It now asserts three things, and the second is the one that matters:

1. **sameness** — the five states' attribute lists collapse to a `Set` of size 1 (R30's actual words: the
   visual treatment SHALL be identical across account states);
2. **non-emptiness** — `treatments[0].length > 0`. Five empty lists satisfy sameness perfectly, which is
   exactly how this case passed for 27 tasks. A version checking only sameness would **also pass if task 173
   were reverted**, or if a later refactor stripped every class. This is T41 phase 11's F3 lesson — *sameness
   is not correctness* — arriving one task later on a different instrument;
3. **classes only** — every reported attribute matches `/^[a-z0-9]+\[class=/`. An inline `style` keyed on the
   cause is the same leak by another route, and the helper reports both shapes, so the distinction is asserted
   rather than assumed.

Its two controls (a cause-keyed class is caught; an inline style is caught too) were **not touched**.

## 4 · Deviations

### D1 — Tailwind first landed in `dependencies`

`pnpm add` without `-D`. Removed and re-added with `-D -E`. Both packages are build-time only; AC15's exact
pin is also what the lockfile audit reads.

### D2 — Instrument 6 fired, and not for the reason the plan gave

The plan expected a new emitted CSS **asset** to be the risk, and verified in phase 0 that the exact-list
assertion covers files outside `assets/`. What actually fired is the bundle's URL scan: the emitted CSS begins
`/*! tailwindcss v4.3.5 | MIT License | https://tailwindcss.com */`, so a vendor URL is now genuinely in the
built output. `KNOWN_ABSOLUTE_URLS` grew by one entry, with a comment naming the licence banner as its
provenance — this is an admission of a **vendor** URL, and it does not relax what the scan says about
application source, which `shellBuildsAndMounts:127` holds separately.

**The first attempt failed**: the scan returns its URLs **sorted** and the assertion is `toEqual`, which
compares order, so `tailwindcss.com` had to go **after** `reactrouter.com`, not at the head of the list. A
growth that is correct in content and wrong in position fails identically to one that is wrong in content.

### D3 — An unpredicted case in a predicted file: T37's anchor case

`publicRouteSetIsExactlyTheDeclaredSet.test.tsx:570` reads `SignedOutScreen.tsx`'s **source** and asserts the
way back is a router `<Link>` and not a hand-rolled `<a>`, `useNavigate`, `location.assign`, a `<button>` or a
`role=`. Moving the screen onto `TextLink` made the claim true one layer down and **unmeasurable where it was
written** — the screen's source now contains neither `<Link` nor `<a`.

Grown, not loosened: the case asserts `<TextLink` in the screen, then **reads `app/ui/TextLink.tsx`** and
asserts the router `<Link>` is there and no bare `<a>` is, and applies T37's whole forbidden list to **both**
files. The instrument became false in form while its claim stayed true, which is the condition under which a
closed task's test may be edited; it is not the forbidden case of "incomplete for something the new task owns".

### D4 — Three primitives had multi-line `className` strings

Prettier-style wrapping puts a literal newline into the DOM attribute. All three were collapsed to one line
each, with a comment saying why the line is long on purpose.

> **CORRECTED by phase 7's F6 and confirmed by phase 8's I5.** This entry first said the newline was
> *"invisible in a browser and very visible to the R30 pin, which compares attribute strings across five
> states."* **It is invisible to the pin too.** RD15 wraps `Screen`'s `className` across three lines and
> survives the whole suite: the newline lands *identically in all five states*, so sameness holds,
> non-emptiness holds, and the classes-only regex `/^[a-z0-9]+\[class=/` anchors only the start. The
> inference ran from *"the pin compares attribute strings"* to *"the pin catches any attribute change"*,
> which does not hold for a change that is the same everywhere — **the exact mistake AC13's non-emptiness
> half exists to prevent, made in the same breath as making it.** No criterion in AC1–AC15 covers it either,
> so phase 10 adds one source-scan row: no `className` literal contains a newline. The collapse itself was
> right; only the reason given for it was wrong.

### D5 — A one-off flake, recorded and not attributed

One whole-suite run showed a single failure at
`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:1594`. It passes in isolation and two subsequent
whole-suite runs were clean at 1780/1781. Recorded here because an unexplained failure that is not written
down becomes evidence for whatever the next person is already expecting; **not** attributed to this change,
because nothing in this task touches that flow.

### D6 — Phase 5's step 7 is phase 10's work, not phase 6's

The plan's execution order ends `7. the named test · 8. phase 7's harness`. Phase 6's prompt is explicit:
*"Do NOT write tests here (that is Phase 10)."* Phase 6 therefore ends at step 6 — grow what fired.
`screensRenderThroughDesignSystemPrimitivesOnly` (AC2–AC9, AC14, AC15, with the §4 control set) is phase 10's,
and the plan's §4 table is its brief. Growing an instrument that this change made **false** is implementation;
writing the task's own suite is not.

### D7 — A comment that named a test which does not exist yet

`RateLimitNotice.tsx`'s new header cited `screensRenderThroughDesignSystemPrimitivesOnly`, AC6 as the thing
that catches a dropped `forwardRef`. That file will not exist until phase 10, so the citation was a claim
dressed as a measurement — the shape T41's phase 8 I10 flagged. It now cites
`rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:399-400`, which asserts the active element **is**
the wrapper and is **not** `<body>`, and which passed unchanged across this move. An existing passing
instrument is stronger evidence than a criterion I have yet to write.

## 5 · Acceptance criteria

| AC | state |
|---|---|
| AC1 Tailwind 4 installed and configured | **done** — plugin, `@theme`, no config file |
| AC2/AC3 the `Only` — five render sites, eight primitives, no bare lowercase JSX tag | **done in production code**; the sweep that proves it is phase 10's |
| AC4 no `className`/`style` in any primitive's type | **done** — `tsc` is the instrument |
| AC5 landmarks, one `h1`, live regions, `aria-*` | **done** |
| AC6 the notice's focus holder keeps focus | **done, and measured** by an existing case |
| AC7/AC8 copy still from the catalogue | **done** — no literal was moved into a primitive |
| AC9 three contrast pairs clear AA | **done arithmetically**, and the four stated ratios were **wrong until phase 7 measured them** (F1); the test that recomputes them from the declarations is phase 10's |
| AC10/AC11 no new storage key, no network call in render | **done** — nothing in `ui/` touches either |
| AC12 one stylesheet | **done** |
| AC13 R30 — identical **and** non-empty | **done** — §3 |
| AC14 the build emits a non-empty CSS asset | **verified by a real build** during phase 6; pinned in phase 10 |
| AC15 exact version pins | **done** |

## 6 · Open questions

Unchanged and carried forward: **OQ-T173-3** (no visual direction exists in the spec package; the `@theme`
block is the whole diff if one is supplied), **OQ-T173-4** (`phase1ScreensPassAccessibilityChecks` is a named
test no task commissions), **OQ-T173-5** (L13 names TanStack Query and Zustand; neither is installed).

**Phase 6 complete.** 10 files created, 12 modified, seven deviations recorded.
