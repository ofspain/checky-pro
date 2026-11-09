<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T173 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Consumes** | `artifacts/04-freeze-task.md` (FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

## 1 · Files to create

### `src/app/styles.css`

```css
@import "tailwindcss";

@theme {
  --color-surface: …;        /* every screen's page background */
  --color-text: …;           /* Heading + Text */
  --color-muted: …;          /* StatusRegion, which is secondary to the control */
  --color-accent: …;         /* ActionButton — the one control in the app */
  --color-text-inverse: …;   /* its label */
  --color-focus: …;          /* the focus ring; FocusHolder and every interactive primitive */
  --text-step-0 / -1 / -2: …;/* Text, Heading, and the status line */
}
```

Six colours, three type steps, Tailwind's default spacing. Each token carries a comment naming the screen that
needs it, so an unused token is visible as a comment with no subject. **The three frozen pairs (§6d) are
satisfied arithmetically, not by eye**, and the test reads this file rather than a duplicate table.

### `src/app/ui/` — eight primitives, one file each

`Surface.tsx` · `Screen.tsx` · `Heading.tsx` · `Text.tsx` · `ActionButton.tsx` · `TextLink.tsx` ·
`StatusRegion.tsx` · `FocusHolder.tsx`, plus `index.ts` re-exporting the eight.

Every one: `forwardRef`, `children`, **no `className`, no `style` in the type** (§6c — the violation is a
compile error, which is a stronger instrument than any scan this task could write). `role`, `aria-live` and
`tabIndex` are set inside and are not props.

One shared file header explains the layer; each primitive's own header says which screens consume it and which
attribute it is responsible for keeping.

### `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`

The named test from `package.md` §8. Sections in §4.

## 2 · Files to modify

| | |
|---|---|
| `package.json` | `tailwindcss` and `@tailwindcss/vite`, pinned exactly (AC15) |
| `pnpm-lock.yaml` | follows |
| `vite.config.ts` | the Tailwind plugin, added to the existing `plugins` array **after** `react()` and before `VitePWA` — the file's existing comments explain each entry and this one gets the same treatment |
| `app/mountShell.tsx` | `import './styles.css';` — **not `main.tsx`** (`shellBuildsAndMounts.test.tsx:135`) |
| `app/AppShell.tsx` | `<Surface data-testid="app-shell">` |
| `app/RateLimitNotice.tsx` | `FocusHolder` → `StatusRegion` + `ActionButton`; **the two refs must keep working**, which is the only place in this task where behaviour could break |
| `auth/SignInScreen.tsx` | `Screen` / `Heading` / `Text id` / `ActionButton` / `StatusRegion` |
| `auth/CallbackScreen.tsx` | `Screen` / `Heading` / `StatusRegion` |
| `auth/SignedOutScreen.tsx` | `Screen` / `Heading` / `Text` / `TextLink` |

## 3 · The instruments that will fire — predicted, then measured

Phase 4 §1 measured the probe (one screen styled, CSS imported) and **one** instrument fired. This change is
larger: five screens, a primitive layer, and a build plugin. The prediction below therefore starts from that
measurement and names what the probe did **not** cover.

| | instrument | why it fires | grows to |
|---|---|---|---|
| **1** | `identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1433` *"renders no class and no style attribute, for any of the five states"* | the probe proved it | the five lists are **equal to each other and non-empty** (AC13, R30). Its two controls are untouched |
| **2** | `shellBuildsAndMounts.test.tsx:102` *"mounts the empty shell into the container"* | `AppShell` becomes `<Surface>`, and if that case asserts exact emptiness or an exact child shape, the class attribute changes it | whatever it asserts, expressed through the primitive. **Not covered by the probe**, which never touched `AppShell` |
| **3** | `shellBuildsAndMounts.test.tsx:93` *"serves the dev server on 5173 … with no proxy"* and `:127` | both read `vite.config.ts` or the app source as **text**; a new plugin line and a new import could trip a count or an exact-list assertion | **Not covered by the probe.** Read before editing |
| **4** | `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` (4 `querySelector` calls) | the notice's two refs and three elements all move | tag/role queries survive a primitive; **the focus assertion is the one to watch** |
| **5** | `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:807` *"one landmark holding a level-one heading, a paragraph and the way back, in that order"* | `SignedOutScreen`'s four elements all become primitives | should pass unchanged — it queries tags, and the primitives render the same tags. **If it fails, the primitive changed the DOM and the primitive is wrong** |
| **6** | `bundleContainsNoSecretEnvKeys.test.ts` | a new emitted CSS asset, and a new dependency in the bundle graph | phase 0 verified its exact-list assertion is for files **outside** `assets/` and its JS count filters `.js`, so it should pass. **Verify, do not assume** |

**Predicted: 1 certain, 5 to verify.** The rule from T41's D1 applies to the number itself: *this list is a
prediction, and the suite is the measurement.* Any instrument that fires and is **loosened** rather than grown
is the failure mode, not the firing.

## 4 · The named test — `screensRenderThroughDesignSystemPrimitivesOnly`

| | section | holds |
|---|---|---|
| **AC2/AC3 · the `Only`** | two pinned lists (five render sites, eight primitives), a derived sweep asserting every non-test file under `src/app/`+`src/auth/` with a lowercase JSX tag is in **exactly one** list, and per-render-site scans that no lowercase JSX tag appears | §6e, all three parts |
| | **CONTROL:** a planted `<div>` in a fake render site is caught; a fragment `<>`, an uppercase component tag, a spread and an expression are **not** (F20) | the scan's precision, which is the half T40's AC7 shipped without |
| **AC4** | each primitive's source contains neither `className` nor `style` as a prop; **and** the types reject them — asserted as a source scan, since `tsc` is the real instrument and a test cannot run it | §6c |
| **AC5** | rendered: exactly one `<main>` and one `<h1>` per screen; `role="status"`+`aria-live="polite"` present on every status region; `aria-describedby`/`aria-disabled`/`aria-busy` on the sign-in control; `tabIndex="-1"` on the notice's holder | L11 |
| **AC6** | `RateLimitNotice`: click dismiss, then `document.activeElement` **is the focus holder** | F9 |
| **AC7/AC8** | each screen's rendered text, asserted against its catalogue entries **and** as literal English | F17 |
| **AC9** | parse `styles.css`'s `@theme` declarations, sRGB → relative luminance, `(L1+0.05)/(L2+0.05)`, three frozen pairs and thresholds; **CONTROL:** a known-failing pair (e.g. `#777` on `#fff` ≈ 4.48:1) is reported as failing | §6d |
| **AC14** | build with Vite's JS API into a temp dir; an emitted `assets/*.css` exists and is non-empty; the built `index.html` links it | F7 |
| **AC15** | `package.json`'s two new entries match `/^\d+\.\d+\.\d+$/` | F13 |
| **AC10/AC11** | no new storage key, no network call during any screen's render; the catalogue's key set is unchanged | the package's standing constraints |

## 5 · Execution order

1. **Install and configure** — `package.json`, `vite.config.ts`, `styles.css` with the `@theme` block, the
   import in `mountShell.tsx`. Gate: `tsc`, `oxlint`, **whole suite**. This is where instruments 3 and 6 fire
   if they are going to, with no primitive in play to confuse the cause.
2. **The eight primitives**, compiled but unused. Gate: `tsc`. Nothing can fire yet.
3. **`AppShell` → `Surface`** alone, then the whole suite: instrument 2 in isolation.
4. **`RateLimitNotice`** alone, then the whole suite: instrument 4, and the focus behaviour, in isolation.
   This is the riskiest edit in the task and it gets its own measurement.
5. **The three screens**, then the whole suite: instruments 1 and 5.
6. **Grow what fired**, each to name the new state rather than admit any state.
7. **The named test**, then `tsc` · `oxlint` · whole suite.
8. **Phase 7's harness**, tenth copy.

Steps 3–5 are separated **because the probe's one-instrument result was measured on one screen**, and the
honest way to attribute five screens' worth of failures is to change one thing at a time. T41's D1 cost a
false conclusion — *T41 has overreached* — for precisely the lack of this.

## 6 · Public / private · services

| | |
|---|---|
| **Public** | eight primitives from `src/app/ui/index.ts`; nothing else |
| **Private** | the token values; every `className` string, which lives in the primitive |
| **Services** | none. No endpoint, no contract, no config key, no catalogue entry, no storage key |

## 7 · Risks carried into phase 6

| | |
|---|---|
| **`RateLimitNotice`'s two refs** | the only behavioural risk. `wrapper` is focused when the dismiss button unmounts; if `FocusHolder` or `ActionButton` drops `forwardRef`, focus falls to `<body>` and a keyboard user is stranded — the exact defect L11 names. Step 4 measures it alone |
| **`aria-disabled` vs `disabled`** | the scan for the second trips on the first as a substring. Frozen pattern: `/(?<!aria-)\bdisabled\b/` |
| **A primitive that changes the DOM** | if instrument 5 fires, the primitive is wrong, not the pin. That direction of reasoning is fixed here, in advance, so it is not decided under pressure |
| **Contrast arithmetic** | an off-by-one in a luminance formula passes a bad palette silently. Hence the AC9 control: a pair known to be **just below** 4.5:1 must be reported as failing |
| **Tokens with no stated direction** | OQ-T173-3. The values are the owner's to replace, confined to one block |
| **The `Only` sweep's own hole** | it reads source text, so a screen that rendered `React.createElement('div')` would pass. Noted rather than closed: nothing in the package uses that form, and a row forbidding it is cheap — it goes in with a probe |

**Phase 5 complete.** 11 files created, 9 modified, six instruments predicted with one certain, five of the six
explicitly flagged as *not covered by the probe*, and an execution order that changes one screen at a time so
the next deviation can be attributed rather than guessed at.
