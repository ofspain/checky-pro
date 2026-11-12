<!-- MODEL: Human Approval gate — Phase 4 (Freeze Task). The model assembles the decision packet; the decisions below are recorded as taken. -->

# frontend · T174 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Consumes** | `artifacts/02-task-implementation-brief.md` · `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` |

**STATUS: FROZEN.** Phases 5–13 may not renegotiate what is below. A phase that finds this brief *wrong*
records a deviation with evidence; it does not quietly do something else.

---

## Part 1 — Disposition of the seventeen phase-3 findings

Every citation in the challenge was checked against source before disposition, per standing rule. Two were
wrong; both are recorded here rather than silently dropped.

| | finding | disposition |
|---|---|---|
| **F1** | focus target | **ACCEPTED in substance, REJECTED in mechanism.** See D1. |
| **F2** | modifying `Heading` touches T173's rule #2 | **CITATION VERIFIED ACCURATE**, and **made moot** by D1. |
| **F3** | file location | **ACCEPTED** → `src/app/HomeScreen.tsx`. See D2. |
| **F4** | grow the three instruments | **ACCEPTED.** Citation verified: `SCREENS_ATTACHED_SO_FAR` is at `routesResolveUnderAppPrefixOnly.test.tsx:18`. |
| **F5** | may conflict with task 42's shell | **ACCEPTED as a constraint**, already carried as L12 in the brief. No edit. |
| **F6** | must not import from `auth/` but needs the route constant | **ACCEPTED.** `HOME_PATH` lives in `app/routes.tsx`, not `auth/`, so there is no tension: the screen imports nothing at all from either. See D6. |
| **F7** | catalogue copy unspecified and should be minimal | **ACCEPTED.** See D7; this closes OQ-T174-1. |
| **F8** | focus must not create a trap | **ACCEPTED as a hazard, REJECTED as written.** See D3. |
| **F9** | re-assert the unauthenticated redirect for `/app/home` | **ACCEPTED** (AC4 already had it; F9 fixes its *scope* — the case must mount the real table at `/app/home`, not a representative path). |
| **F10** | no identity-dependent content | **ACCEPTED.** AC6 gains a source scan, not only a DOM scan. |
| **F11** | one harness row per criterion | **ACCEPTED.** Binding on phase 7. |
| **F12** | screen-count pin must distinguish screens from redirects | **PREMISE FALSE, EDIT CORRECT.** See D4. |
| **F13** | the structural pin may need refinement | **ACCEPTED CONDITIONALLY.** See D5. |
| **F14** | the `Only` sweep must include the home screen | **ACCEPTED.** `RENDER_SITES` 5 → 6 (verified: the array at `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:63-69` holds exactly the five named). |
| **F15** | should not break T41's tests | **ACCEPTED as a prediction, which phase 7 must MEASURE.** "No test should break" is a claim, and the only honest form of it is a green run named in the notes. |
| **F16** | cross-suite callback → home → focus | **DEFERRED.** See D8 → OQ-T174-4. |
| **F17** | StrictMode double invocation | **ACCEPTED.** Verified: `mountShell.tsx:25` wraps the tree in `<StrictMode>`, so the effect genuinely runs twice in development. See D9. |

---

## Part 2 — The nine decisions this gate settles

### D1 — The focus target is the `<h1>`, and `tabIndex={-1}` is set INSIDE `Heading`

F1 is right that the heading is the correct target: focusing it makes a screen reader announce the screen's
name and its level, which is the thing an arriving visitor needs. Focusing `<main>` announces a landmark and
leaves the name to chance; a labelled holder announces a label and inserts a non-semantic wrapper.

**F1's mechanism is rejected.** It proposes `tabIndex?: -1` as a *prop* on `Heading`, plus an amendment to
T173's rule #2 to permit it. That amendment is wrong on both counts:

- It weakens an invariant **one task after it was frozen**, in order to let the very next task do the thing
  the invariant forbids. A rule relaxed by its first customer was never a rule.
- It is **unnecessary**, because rule #2's own text already prescribes the remedy. Verified verbatim at
  `src/app/ui/index.ts:16`: *"**No primitive accepts the attribute that is its own contract.** `role`,
  `aria-live`, `tabIndex`, `type="button"` and the heading level are **set inside**."* The rule does not ban
  `tabIndex` on a primitive. It bans a **consumer** supplying it.

So: `Heading` renders `<h1 ref={ref} tabIndex={-1} …>` unconditionally. Its props type stays
`{ children: ReactNode }` — no new prop, no type change, no rule amendment, and no ninth primitive. The screen
focuses it through the ref `Heading` has forwarded since T173 (verified: `Heading.tsx:11-18` is already
`forwardRef<HTMLHeadingElement, { children: ReactNode }>`).

Every heading in the SPA becomes programmatically focusable. That is the intended consequence, not a side
effect: `tabIndex={-1}` adds nothing to the tab order, and task 42 and every later screen will want the same
move on arrival. Making it uniform now is cheaper than a prop that each screen must remember to pass.

**One instrument was checked for silent blunting before taking this decision.**
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:515` reads `container.querySelector('[tabindex]')`
and asserts it is `-1`. If a `Heading` were in that tree, the query could return the heading instead of the
`FocusHolder` and still pass — the case would stop measuring its own subject. It mounts `RateLimitNotice`,
whose render tree is `FocusHolder › StatusRegion › ActionButton` with **no `Heading`** (verified at
`RateLimitNotice.tsx:40-52`). The case is unaffected. Phase 7 re-checks this rather than trusting it.

**This moves `src/app/ui/Heading.tsx` from Files-NOT-to-modify to Files-to-modify.** The phase-2 brief put all
of `app/ui/` off limits; that was right as a default and wrong for this task, and the change is recorded here
rather than discovered in a diff. Every alternative considered — a prop on `Heading`, `aria-labelledby` on
`FocusHolder`, `tabIndex` inside `Screen` — modifies `app/ui/` too, because no existing primitive is both
focusable and named. The rest of `app/ui/` stays off limits.

### D2 — The file is `src/app/HomeScreen.tsx`, the suite beside it

Verified by listing: `app/` holds `AppShell.tsx` and `RateLimitNotice.tsx`; `auth/` holds the three screens
that are *about* authentication. Home is not about authentication — it is what authentication leads to. The
suite is `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx`, named exactly as `package.md` §8
names it.

### D3 — F8's Tab-key assertion is REJECTED as unmeasurable here, and replaced

F8 asks for a case proving focus can leave the screen by keyboard. **As described it cannot be written on this
host, and a case that appears to assert it would assert nothing.** Measured:

- `@testing-library/user-event` is **not a dependency** (verified against both dependency blocks in
  `package.json`) — so `userEvent.tab()` does not exist here.
- jsdom does not implement sequential focus navigation. `fireEvent.keyDown(el, { key: 'Tab' })` dispatches an
  event and moves focus **nowhere**. A case that pressed Tab and then asserted `activeElement` had moved would
  fail against correct code; one that asserted it had *not* moved would pass against a real trap.
- There is **no precedent** anywhere in `src/` — no test presses Tab (verified by grep).

This is T37's lesson in a new costume: *a pattern can be aimed at nothing.* Rather than install a dependency
to satisfy one assertion, or write a case whose green means nothing, AC8 asserts the **structural property
that causes trapping**, which is falsifiable here:

- the focus target's `tabIndex` is exactly `-1` — not `0`, not positive;
- **no element rendered by the screen carries a positive `tabIndex`** (a positive value is what actually
  disorders and captures keyboard navigation);
- no element carries `aria-modal`, and the screen installs no `keydown` listener.

What is *not* claimed is that a Tab keypress leaves the screen. That is honest and it is a real gap:
**OQ-T174-5** records it, with the remedy named (a dependency the owner has not agreed to, or a Playwright
case) so a later task can close it deliberately.

### D4 — F12's edit is adopted; its attribution is corrected

F12 asserts *"T173 introduced `SCREENS_ATTACHED_SO_FAR` and `REDIRECTS_ATTACHED_SO_FAR`."* **That is false.**
Both live in `routesResolveUnderAppPrefixOnly.test.tsx` — at `:18` and `:34` — and belong to **task 16**;
`publicRouteSetIsExactlyTheDeclaredSet.test.tsx:691` only *mentions* them, in a comment reading *"task 16's
`SCREENS_ATTACHED_SO_FAR`"*. T173 did not touch either.

The recommended edit is nonetheless right: `SCREENS_ATTACHED_SO_FAR` becomes
`['sign-in', 'callback', 'signed-out', 'home']` and `REDIRECTS_ATTACHED_SO_FAR` stays `['*']`. The
attribution matters because the comment the edit lands beside must name the right task, or the next reader
inherits the error.

### D5 — F13 is conditional, and the condition is a measurement

`publicRouteSetIsExactlyTheDeclaredSet` pins the **public** set. `/app/home` is authenticated, so attaching an
element to it should move nothing there. Phase 6 does not assume that: it **runs that suite before and after**
and records the result. If it does not move, the notes say so and say why. If it does, the pin is examined
under the standing rule — grow an instrument that must *admit new state*, never one that is merely
*inconvenient*.

### D6 — The screen imports nothing but `app/ui` and `react-i18next`

F6 worries the screen needs a route constant. It does not: it renders copy and a heading, and it is mounted
*by* the route table rather than linking into it. The screen's import list is frozen at exactly two modules.
AC6's source scan enforces it, which also discharges F10 without a second instrument.

### D7 — The copy is three catalogue entries, and this closes OQ-T174-1

Verified convention from `SignedOutScreen.tsx`: `useTranslation()` + `t('<group>.<key>')`, catalogue at
`src/i18n/catalogue.ts`. A new `home` group with **`heading`, `explanation`** and nothing else. No name, no
greeting, no account state, no counts — AC6 forbids all of it, and R22 is why.

The copy is deliberately the minimum that is not blank. It is the owner's to rewrite, and because it lives in
the catalogue they can do that without touching a component or a test that asserts behaviour. OQ-T174-1 is
**closed by this decision**, not carried.

### D8 — F16's cross-suite case is OUT of scope → OQ-T174-4

A callback → `/app/home` → focus case would be the first end-to-end proof of the post-sign-in landing, and
OQ-T41-2 has been waiting for it (verified: `T41/09-review-resolution.md:109` — *"AC14's end-to-end value is
provable only by composing two suites until task 42 exists"*). It stays out of T174 because it spans two
suites and two tasks' ownership, and because task 42 builds the shell both ends will sit inside. Recorded as
**OQ-T174-4**, pointing at OQ-T41-2, so task 42 inherits a named obligation rather than a vague intention.

### D9 — The focus effect must be idempotent under StrictMode, and must be *proved* so

`mountShell.tsx:25` really does wrap the tree in `<StrictMode>` (verified), so the effect runs twice in
development. `ref.current?.focus()` is idempotent, but "is idempotent" is a claim. The suite mounts the screen
**inside `<StrictMode>`** in at least one case and asserts the same final `activeElement`, following the
precedent already in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:68`.

---

## Part 3 — The frozen brief

### Task
T174 — Home screen. `/app/home` renders a landing screen and moves focus to it on arrival.

### Purpose
A completed sign-in currently lands on a blank document: no authenticated route has an element. This is the
first authenticated screen, and the destination `CallbackScreen` already navigates to.

### Scope

**In:** one screen component; its route element; two catalogue entries; `tabIndex={-1}` inside `Heading`; the
named suite; the growth of three closed-task instruments; a mutation harness.

**Out:** the authenticated shell (task 42) · navigation, menus or links out · any profile or account data ·
any network call · `phase1ScreensPassAccessibilityChecks` (OQ-T174-3) · the cross-suite end-to-end case
(OQ-T174-4) · a real Tab keypress (OQ-T174-5).

### Business rules
Unchanged from phase 2: **R28** · **R7/L1** · **R22** · **R20** · **R29/L16/L10** · **L4**.

### Locked decisions
**L1** · **L4** · **L10** · **L11** · **L12** · **L13** · **L14** · **L16**, plus `agents.md`'s catalogue rule
and accessibility rule.

### Files to create
- `src/app/HomeScreen.tsx`
- `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx`

### Files to modify
- `src/app/ui/Heading.tsx` — `tabIndex={-1}`, set inside; props type unchanged **(D1)**
- `src/app/routes.tsx` — an element on `HOME_PATH`, inside the gate's children
- `src/i18n/catalogue.ts` — a `home` group of two entries
- `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — `SCREENS_ATTACHED_SO_FAR` 3 → 4 **(D4)**
- `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` — `RENDER_SITES` 5 → 6, union 13 → 14
- `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — **only if D5's measurement requires it**

### Files NOT to modify
Every other file under `src/app/ui/` · `src/app/styles.css` · `auth/RequireSession.tsx` ·
`auth/RedirectToSignIn.tsx` · `auth/returnTarget.ts` · anything under `spec/`.

### Acceptance criteria

| | criterion | traces to |
|---|---|---|
| **AC1** | `/app/home` renders a `<main>` landmark holding exactly one `<h1>`, through T173's primitives, with no lowercase JSX tag in `HomeScreen.tsx` | task 174, T173 §6e |
| **AC2** | On arrival `document.activeElement` **is the `<h1>`** — not `<body>`, not `<main>`, not a wrapper | task 174, **L11**, **D1** |
| **AC3** | Every rendered string comes from the catalogue, asserted against the entries **and** as literal English, with no raw key in the DOM | **L11**, **O3**, **D7** |
| **AC4** | An unauthenticated arrival **at `/app/home` specifically** is redirected to the sign-in start and renders no part of the screen | **R28**, **F9** |
| **AC5** | Rendering writes no storage and makes no network call | **R29**, **L16**, **R20** |
| **AC6** | Nothing rendered depends on identity, enforced in the **DOM and in the source**: `HomeScreen.tsx` imports only `app/ui` and `react-i18next` | **R22**, **R20**, **D6** |
| **AC7** | Three instruments grown: six render sites, a union of fourteen, four attached screens — each edit naming **task 174**, and `SCREENS_ATTACHED_SO_FAR` attributed to **task 16** | the pins' own comments, **D4** |
| **AC8** | No focus trap, asserted structurally: the target's `tabIndex` is exactly `-1`, no rendered element carries a positive `tabIndex`, no `aria-modal`, no `keydown` listener | **L11**, **D3** |
| **AC9** | `Heading` carries `tabindex="-1"` and still accepts **no** `tabIndex` prop — its props type remains `{ children }`, so T173's rule #2 holds literally | **D1**, `ui/index.ts:16` |
| **AC10** | The focus move is idempotent: mounted inside `<StrictMode>`, the final `activeElement` is the same | **D9**, **F17** |

### Required tests
`homeRouteRendersLandingScreenAndManagesFocus`, plus the instrument growth above. A mutation harness
(**eleventh copy**) with **at least one row per criterion AC1–AC10**, every row predicting
`own` / `inherited` / `unfalsifiable`, and **controls as well as mutants**. Standing harness rules carry:
commit tests before running it; a mutant that cannot compile is **BROKEN, not killed**; a mutant must measure
its own description; score canaries apart from regressions.

The eleventh copy also carries the fix the tenth deferred: the `in:` file list over-reports, because `FAIL`
lines and `×` lines are parsed separately. Parse them in document order.

### Constraints
- **Module boundaries:** `HomeScreen.tsx` imports exactly `app/ui` and `react-i18next`. No `auth/`, no
  `accessToken`, no `globalThis.location`, no `history`, no `document.cookie`, no storage.
- **Security:** no PII, no token, no account state in the DOM or the source.
- **Null handling:** the heading ref is the one nullable thing; `?.focus()` must tolerate it.
- **Phase additivity (L12):** task 42 nests the shell around this. It must be something to build on.

### Open questions

**No blockers.** One closed, four carried, each with an owner:

- **OQ-T174-1** — **CLOSED by D7.** Copy is two catalogue entries, the owner's to rewrite.
- **OQ-T174-2** — **CLOSED by D1.** L11's *"manages focus"* resolves to the `<h1>`, with the reasoning and the
  rejected alternatives recorded above.
- **OQ-T174-3** — `phase1ScreensPassAccessibilityChecks` appears in this task's header and is commissioned by
  no task in `tasks.md`. **Not claimed here.** Carried for the owner. (OQ-T173-5's shape.)
- **OQ-T174-4** — the callback → `/app/home` → focus end-to-end case, blocked on task 42. Points at
  **OQ-T41-2**. Owner: task 42.
- **OQ-T174-5** — AC8 proves no trap structurally, not behaviourally, because no Tab keypress is measurable on
  this host. Closing it needs `@testing-library/user-event` or a Playwright case — an owner decision, since it
  adds a dependency.
