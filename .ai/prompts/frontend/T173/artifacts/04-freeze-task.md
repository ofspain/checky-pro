<!-- MODEL: Claude Opus 5 — Phase 4 (Freeze Task) · FROZEN BRIEF -->

# frontend · T173 · Phase 4 — Frozen Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` (20 findings) |
| **Produces** | `artifacts/04-freeze-task.md` — **FROZEN**; downstream phases may not renegotiate §6 |

## 1 · The measurement that was taken before freezing

F8 says the closed-task instruments *"will break and must be grown in advance"*, and my own phase 0 sized the
blast radius as *"13 suites assert `innerHTML`, 11 use `querySelector`, 8 read `tagName`"*. T41's **D1** was
exactly this mistake — phase 5 predicted seven firing instruments and twenty-six fired — and the correction
recorded then was *run the suite rather than reason about it*. So it was run, **before** the freeze:

> `className` added to all five elements of `SignInScreen`, plus `import './probe.css'` in `mountShell.tsx`,
> then `vitest run` over the whole suite.

**Exactly one instrument fired.**

| | |
|---|---|
| **1 failure** | `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1433` — *"renders no class and no style attribute, for any of the five states"* |
| **0 failures** | from all 13 `innerHTML` suites, all 11 `querySelector` suites and all 8 `tagName` assertions |
| **0 failures** | from the CSS import — **vitest needed no configuration at all**, which answers F19 by measurement |

The reason the 13 + 11 + 8 did not fire is that **no test in the repository asserts a non-empty exact
`innerHTML` string**: every one is either `toBe('')`, a comparison of two renders that change together, or a
`querySelector` for a tag or role that a primitive still renders. Phase 0's number was a count of *suites that
touch the DOM*, not of *suites that would break* — and the difference is 24 to 1.

**F8 is therefore accepted in principle and corrected in fact**, and phase 5's prediction starts from a
measurement rather than from a grep.

## 2 · The instrument that fired was written for this task, and it carries a requirement phase 1 said did not exist

`identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1430-1434` says so itself:

> *"**A PIN FOR TASK 173**, not a claim that styling will never exist. Measured: no production file contains
> `className`, `style=`, `styled` or `.css`, so **R30**'s 'identical visual treatment' is trivially true today
> (T29-D3). When the design system lands, **this case is what fails if one failure is styled differently from
> another** — which is the whole reason to write it before there is any styling to check."*

**This corrects phase 1, §1.** That phase concluded *"there is no requirement to derive"* on the strength of
`requirements.md` containing no occurrence of *WCAG*, *accessible*, *focus*, *keyboard* or *catalogue*. It
searched for words that would **require** styling and found none, which is true. It did not search for a
requirement that **constrains** styling, and there is one:

> **R30** (`requirements.md:104-107`) — *"WHEN an identity-related screen the SPA renders shows an error, THEN
> the copy, redirect target, and visual treatment SHALL be identical across unknown or missing, locked,
> suspended, and deleted accounts, and across bad, expired, and already-used tokens. The requirement covers
> SPA-observable copy, redirects, and **styling** only."*

R30 is the only requirement in the package that mentions styling, and it is live for this task. **It joins the
frozen acceptance surface as AC13.** A task that styled the five identity-failure states differently — a
`sign-in-error--locked` class, which that suite's own control plants — would be an enumeration leak dressed as
visual feedback.

**The grown assertion must claim sameness AND non-emptiness.** Five empty lists satisfy sameness, which is how
the case passes today; five *identical* lists satisfy R30 only if styling is actually present. This is T41's
phase-11 F3 lesson — *sameness is not correctness* — arriving one task later on a different instrument.

## 3 · Verdicts on all twenty findings

| | finding | verdict |
|---|---|---|
| **F1** | the no-headless-library call needs a crisp decision | **ACCEPTED — ratified.** §6a states it as a decision with a named revisit trigger |
| **F2** | no primitive matches `AppShell`'s container; `Screen` is `<main>` and cannot nest | **ACCEPTED — the one gap in phase 2's inventory.** Phase 2 enumerated eight shapes and then listed seven primitives. An eighth, `Surface`, is frozen in §6c |
| **F3** | the no-`className` rule may be too rigid | **ACCEPTED as a question · ANSWERED by measurement.** All three screens are the same shape — `main > h1 > (p) > control` — so no layout variant has a consumer today. `className` **and** `style` are forbidden on every primitive; a variant prop arrives with the first screen that needs one, which is the same rule applied to the vendor library in F1 |
| **F4** | how the `Only` claim is enforced is unspecified | **ACCEPTED, and strengthened past the recommendation.** A pinned list alone cannot see a new screen; a glob alone cannot say which files were deliberate. §6e freezes **both**, cross-checked — the hole T40's AC13 existed to close |
| **F5** | attribute-forwarding policy | **ACCEPTED** — props tables in §6c |
| **F6** | the contrast test needs concrete arithmetic | **ACCEPTED** — pairs, token names and thresholds frozen in §6d |
| **F7** | build-output verification must be a gate | **ACCEPTED, as a test rather than a gate.** A command a human runs is not an instrument; the named test builds with Vite's JS API, as `shellBuildsAndMounts.test.tsx:54` already does, and asserts an emitted CSS asset |
| **F8** | closed-task instruments will break | **ACCEPTED in principle, CORRECTED in fact** — see §1. One instrument, not twenty-four |
| **F9** | focus preservation must be asserted | **ACCEPTED** — `document.activeElement` after dismissal, AC6 |
| **F10** | primitive props interfaces unspecified | **ACCEPTED** — §6c |
| **F11** | `FocusHolder`'s children should be optional | **REJECTED — the premise is false.** `RateLimitNotice.tsx:29-37` always renders the status region inside the holder; the *button* is conditional, the children are not. Children stay required, because a focus holder with nothing in it is a bug this type can refuse |
| **F12** | ratify Tailwind 4 or switch to 3 | **ACCEPTED — Tailwind 4 ratified**, OQ-T173-6 closed in §6b with the reason |
| **F13** | exact pinning must be enforced | **ACCEPTED, narrowed** — one assertion over the two new dependencies, not a new package-wide rule this task was not asked for |
| **F14** | `TextLink` takes `to`, not `href` | **ACCEPTED** — §6c |
| **F15** | `StatusRegion` must render unconditionally | **ACCEPTED** — frozen in the props table, with the reason (a region that appears with text already in it is not announced) |
| **F16** | one landmark and one `<h1>` per screen, source **and** runtime | **ACCEPTED** — AC5 |
| **F17** | the catalogue-unchanged constraint needs a plan | **ACCEPTED** — each screen's rendered text is asserted against its catalogue entries **and** as literal English, because `t()` returns the key when an entry is missing |
| **F18** | `ActionButton` must not map `aria-disabled` to `disabled` | **ACCEPTED, with the trap named.** `aria-disabled` *contains* the substring `disabled`, so the naive scan trips on the correct code. The frozen pattern is `/(?<!aria-)\bdisabled\b/` — T36's lesson that a substring is not a call shape |
| **F19** | CSS imports may need vitest configuration | **ACCEPTED as a risk · ANSWERED: none needed** (§1) |
| **F20** | the scan must not flag fragments or conditionals | **ACCEPTED** — §6e defines the scan as *lowercase JSX tag names*, which fragments, expressions and spreads are not |

**Ten of twenty findings changed the brief; one was rejected on its facts; two were answered by running
something rather than deciding anything.**

## 4 · What the challenge did not raise

Recorded because a review's silence is not evidence:

- **`AppShell` is where the stylesheet's effect begins, and it is `<div>`-only.** With `Surface` added, the
  shell has a primitive and AC2 has no exemption — which F2 was right to insist on.
- **No finding questioned AC8** (rendered text unchanged), which is the criterion that makes this task safe to
  run across five closed screens. It stays.
- **No finding mentioned R30**, the one requirement that constrains styling, nor the pin written for this task
  in a closed suite. Both were found by running the suite, not by reading the challenge — which is the same
  shape as T41, where the instrument that mattered (T40's AC13) was the one the challenge missed.

## 5 · Scope

**In:** install and configure Tailwind 4 · one CSS entry with a `@theme` token block · eight primitives in
`src/app/ui/` · all five render sites moved onto them · the named test · growing the one instrument that fires.

**Out:** no vendor headless library (§6a) · no visual redesign · no copy change · no new route · no new storage
key · no accessibility tooling (OQ-T173-4 stays open) · no TanStack Query, no Zustand (OQ-T173-5) · task 174's
home screen.

## 6 · The frozen shape

### 6a · No vendor headless library — ratified

`src/app/ui/` **is** the unstyled accessible primitive layer this task's statement calls for. O2 says
*"headless/unstyled components (**e.g.** Radix UI)"*, and `e.g.` is an example; Q7 asks for *"fully custom look,
no fighting a pre-themed library"*. None of the five render sites has a dialog, dropdown, popover, menu,
tooltip, tab set, form field, table or toast — the composite controls such a library exists to make accessible.

**Revisit trigger, named so a later task does not have to infer it:** the first screen that needs a composite
control with managed focus — MFA enrollment (R11–R13) is the likely first — installs the library *with a real
consumer*, and this decision is where it should start reading.

### 6b · Tailwind 4 — ratified, OQ-T173-6 closed

`tailwindcss` and `@tailwindcss/vite`, both pinned exactly at the same version. CSS-first: the Vite plugin plus
`@import "tailwindcss";`, tokens in `@theme`, **no `tailwind.config.js` and no PostCSS step**. `tasks.md:31`'s
phrase *"no Tailwind or PostCSS configuration"* describes the gap in version-3 terms; version 4 closes it with a
plugin and a stylesheet, and that is what *"configured"* means here. Chosen over version 3 because a config file
and a PostCSS chain are two more build surfaces than this task needs, and `vite.config.ts` is pinned by a test
that reads it line by line.

The stylesheet is imported by **`src/app/mountShell.tsx`**, not `src/main.tsx`:
`shellBuildsAndMounts.test.tsx:135` pins the entry to a single `mountShell` call.

### 6c · Eight primitives, with frozen interfaces

Every one: forwards `ref`, takes `children`, and **accepts neither `className` nor `style`** — not optional,
not ignored, **absent from the type**, so a screen that tries fails `tsc` rather than a scan.

| primitive | renders | props beyond `children` + `ref` |
|---|---|---|
| `Surface` | `<div>` | `data-testid?: string` — the shell's identity |
| `Screen` | `<main>` | — |
| `Heading` | `<h1>` | — |
| `Text` | `<p>` | `id?: string`, for `aria-describedby` |
| `ActionButton` | `<button type="button">` | `onClick?`, `aria-describedby?`, `aria-disabled?`, `aria-busy?` · **never `disabled`** |
| `TextLink` | react-router `<Link>` | `to: string` · **never `href`** |
| `StatusRegion` | `<div role="status" aria-live="polite">` | — · **renders unconditionally**; the consumer passes children only when there is text, so the region is in the tree before the text arrives and is announced |
| `FocusHolder` | `<div tabIndex={-1}>` | — · `children` **required** (F11) |

`role`, `aria-live` and `tabIndex` are set **inside** the primitive and are not props: an ARIA contract a
consumer can override is not a contract.

### 6d · The token set and the contrast claim

One `@theme` block in `src/app/styles.css`. Six colour tokens, three type steps, Tailwind's default spacing.
Every token carries a comment saying which screen needs it. Frozen pairs and thresholds:

| pair | threshold | why |
|---|---|---|
| `--color-text` on `--color-surface` | **≥ 4.5:1** | body text, WCAG AA 1.4.3 |
| `--color-text-inverse` on `--color-accent` | **≥ 4.5:1** | the one control's label |
| `--color-focus` on `--color-surface` | **≥ 3:1** | focus indicator, WCAG 2.1 1.4.11 non-text contrast |

Computed in the test from the CSS file's own declarations — parse the hex, sRGB → relative luminance,
`(L1 + 0.05) / (L2 + 0.05)`. **The tokens are the single point of visual change** (OQ-T173-3): if the owner
supplies a direction, that block is the whole diff.

### 6e · How `Only` is enforced — both directions

Three claims, because each alone has a hole:

1. **TWO pinned lists, because a primitive renders a lowercase tag by definition — that is its whole job —
   and a render site must not.** The render sites are **five** (`app/AppShell.tsx`, `app/RateLimitNotice.tsx`,
   `auth/SignInScreen.tsx`, `auth/CallbackScreen.tsx`, `auth/SignedOutScreen.tsx`); the primitives are
   **eight** (§6c). A sixth screen or a ninth primitive must be added to its list deliberately, as
   `SCREENS_ATTACHED_SO_FAR` is.
2. **A derived sweep that cross-checks both pins.** Measured at phase 5: exactly five non-test files under
   `src/app/` and `src/auth/` contain a lowercase JSX tag today, and they are precisely the five render sites.
   After this task there must be thirteen, and **every one must appear in exactly one of the two lists** — so
   neither a new screen nor an unlisted primitive can be invisible to the claim. This is the hole T40's AC13
   was written for, closed before it bites rather than after.
3. **Per file, every lowercase JSX tag name is forbidden.** Fragments (`<>`), component tags (uppercase),
   expressions and spreads are not lowercase tag names and are not flagged (F20). Each forbidden pattern is
   paired with a probe that proves it fires, and the real source must not already trip it.

`RequireSession.tsx` and `RedirectToSignIn.tsx` render `<Outlet />`, `<Navigate />` or `null` — no lowercase
tag, so they satisfy (3) without being primitives' consumers, and the sweep in (2) will not list them.

### 6f · Acceptance criteria — FROZEN

AC1–AC12 from phase 2 §4 stand, with these changes:

| | |
|---|---|
| **AC2** | extended: all **five** render sites, including `AppShell` via `Surface` — no exemption (F2). (*Corrected after freezing: this row read "eight render sites". Eight is the PRIMITIVE count; the render sites are five, and conflating them is what §6e's two lists exist to keep apart.*) |
| **AC3** | extended: the `Only` claim is the three-part construction in §6e, not a single scan |
| **AC4** | hardened: `className` and `style` are **absent from every primitive's type**, so the violation is a compile error, not a test failure |
| **AC9** | concrete: the three pairs and thresholds in §6d, computed from the stylesheet |
| **AC12** | corrected by measurement: **one** closed instrument fires, named in §2 |
| **AC13** | **NEW — R30.** The five identity-failure states carry **identical** style attributes, and the lists are **non-empty**, because five empty lists satisfy sameness while proving nothing |
| **AC14** | **NEW** — the emitted build contains a CSS asset (F7), asserted in the named test via Vite's JS API |
| **AC15** | **NEW** — `package.json` pins `tailwindcss` and `@tailwindcss/vite` exactly: no `^`, no `~` (F13) |

## 7 · What downstream phases may not renegotiate

§6a (no vendor library), §6b (Tailwind 4, and the import site), §6c (the eight primitives and their
interfaces — in particular that `className` and `style` are not in the types), §6d (the three contrast pairs),
§6e (all three parts of the `Only` construction), and AC13's **sameness-and-non-emptiness** pair.

Phase 5 may choose file layout within `src/app/ui/`, the token values, and the order of work. It must
enumerate the firing instruments **in advance and then measure**, starting from §1's result rather than from a
grep.

## 8 · Open questions carried

**OQ-T173-3** (the owner's: no visual direction exists; confined to one `@theme` block) ·
**OQ-T173-4** (`phase1ScreensPassAccessibilityChecks` is orphaned in `package.md` §8 and claimed by no task) ·
**OQ-T173-5** (L13 names TanStack Query and Zustand; neither installed) · **OQ-T173-1/2/6 are now closed** —
no vendor library, `src/app/ui/`, Tailwind 4.

**Phase 4 complete.** Twenty findings judged: 17 accepted (2 of them answered by running something), 1
rejected on its facts, 2 corrected. One eighth primitive added, one requirement recovered that phase 1 had
ruled out, one instrument found that the challenge missed — and the blast radius corrected from 24 suites to
one, by measurement taken before the freeze rather than after it.
