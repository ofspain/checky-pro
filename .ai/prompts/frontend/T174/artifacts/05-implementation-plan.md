<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). No code. -->

# frontend · T174 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Consumes** | `artifacts/04-freeze-task.md` (FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

Every file below traces to the frozen brief's Files sections. Nothing else is planned.

---

## Files to create

| file | brief |
|---|---|
| `src/app/HomeScreen.tsx` | Files-to-create, **D2** |
| `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | Files-to-create, `package.md` §8 |

## Files to modify

| file | the edit | brief |
|---|---|---|
| `src/app/ui/Heading.tsx` | add `tabIndex={-1}` to the `<h1>`; **props type untouched** | **D1**, AC9 |
| `src/app/routes.tsx` | give `HOME_PATH` an element inside the gate's children (`:84`) | AC1 |
| `src/i18n/catalogue.ts` | a `home` group: `heading`, `explanation` | **D7**, AC3 |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` (`:18`) 3 → 4, adding `'home'` | **D4**, AC7 |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` (`:63-69`) 5 → 6; the union pin (`:221-222`) 13 → 14; the header comment (`:13-15`) | **F14**, AC7 |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **only if D5's measurement requires it** | **D5** |

## Files NOT to modify
Every other file under `src/app/ui/` · `src/app/styles.css` · `auth/RequireSession.tsx` ·
`auth/RedirectToSignIn.tsx` · `auth/returnTarget.ts` · anything under `spec/`.

---

## Public surface

**`HomeScreen.tsx`** exports exactly one symbol:

```
export function HomeScreen(): ReactElement
```

No props — the brief's Inputs section is `None`, and a prop would be a seam AC6 has to police.

Internals (not exported, no helper file):
- `const headingRef = useRef<HTMLHeadingElement>(null)` — the ref `Heading` has forwarded since T173.
- one `useEffect(() => { headingRef.current?.focus(); }, [])` — empty dependency array. The effect is the
  whole of the focus management, and `?.` is the null handling the brief's Constraints require.

**`Heading.tsx`** — no signature change. `forwardRef<HTMLHeadingElement, { children: ReactNode }>` stays
exactly as it is; only the rendered `<h1>` gains `tabIndex={-1}`. AC9 is written so a props-type change
breaks a test.

**`routes.tsx`** — no exported signature changes. `HOME_PATH` and `AUTHENTICATED_ROUTE_PATHS` keep their
current values and order. The single edit is at `:84`, where
`AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path }))` becomes a map that attaches an element when
`path === HOME_PATH`, in the same shape the public branch already uses at `:75-77`. The other ten
authenticated paths keep no element — attaching one to any of them is out of scope and would pre-empt task 42.

**No entities, repositories or services.** This is a browser screen with no state, no storage and no network
call; the brief's `State Changes` section is `None`.

---

## Execution order

The front-loading rule for this stack is: **the thing other edits depend on first, the instruments that
measure them last, and the harness after the tests are committed.**

1. **`Heading.tsx`** — `tabIndex={-1}`. Everything else depends on the heading being focusable, and doing it
   first means every later run is against the real primitive. Immediately after: run
   `screensRenderThroughDesignSystemPrimitivesOnly` and the two rate-limit suites, and record the result. D1
   predicts nothing moves (`RateLimitNotice` has no `Heading`, so `:515`'s `[tabindex]` query is unaffected) —
   **that prediction is checked here, not assumed.**
2. **`catalogue.ts`** — the `home` group. Before the component, so the component is never written against a
   key that does not exist.
3. **`HomeScreen.tsx`** — the component.
4. **`routes.tsx`** — attach the element. The screen now exists, so the route table is never broken between
   steps.
5. **Instrument growth**, in the order their failures would surface: `RENDER_SITES` and the union
   (`screensRender…`), then `SCREENS_ATTACHED_SO_FAR` (`routesResolve…`). Each edit carries a comment naming
   **task 174** as the reason, and the `SCREENS_ATTACHED_SO_FAR` comment attributes the constant to **task
   16** (**D4** — phase 3 got this wrong and the comment is where the correction has to live).
6. **D5's measurement** — run `publicRouteSetIsExactlyTheDeclaredSet` and record whether it moved. Edit it
   only if it did, and only under the standing rule (grow an instrument that must admit new state; never one
   that is merely inconvenient).
7. **`homeRouteRendersLandingScreenAndManagesFocus.test.tsx`** — the named suite, AC1–AC10.
8. **Full suite green, then COMMIT.** The harness reverts with `git checkout --` over `REVERT_PATHS`;
   uncommitted test edits are destroyed silently. This has cost 24 cases across T14 and T15 and is not
   repeated.
9. **The eleventh harness copy** — phase 7, after the commit, never before.

---

## Tests required

### The named suite — `homeRouteRendersLandingScreenAndManagesFocus.test.tsx`

Grouped by criterion, every case naming its AC in the title, per the convention T173 settled.

| AC | cases |
|---|---|
| **AC1** | one `<main>`, exactly one `<h1>`, both from primitives; a source scan of `HomeScreen.tsx` finding **zero** lowercase JSX tags (T173's `lowercaseJsxTags` shape, including the `(?<![A-Za-z0-9_$])` lookbehind that generics forced — and a **control** proving the scan still finds a real tag) |
| **AC2** | after mount, `document.activeElement` is the `<h1>` — asserted as `===` the heading node, not merely "not body"; plus a case asserting it is **not** `<main>` and **not** the body, so the three candidates D1 weighed are distinguished |
| **AC3** | every rendered string equals its catalogue entry **and** its literal English; no `home.` key text in the DOM |
| **AC4** | the real route table mounted at **`/app/home`** with no session → redirected to the sign-in start, and **no** part of the screen in the DOM (the heading text is absent) |
| **AC5** | `sessionStorage`/`localStorage` untouched (snapshot before and after) and `fetch` never called |
| **AC6** | the DOM holds no identity-shaped content, **and** `HomeScreen.tsx`'s import list is exactly `app/ui` and `react-i18next` — a source assertion, since D6 made the import list the contract |
| **AC7** | the three instruments at their new values, each asserted by **reading the instrument file**, so a silent revert fails |
| **AC8** | the focus target's `tabIndex` is exactly `-1`; **no** rendered element has a positive `tabIndex`; no `aria-modal`; no `keydown` listener. **The suite says in a comment what it does NOT claim** (a real Tab keypress — OQ-T174-5) so a later reader does not mistake AC8 for behavioural proof |
| **AC9** | `Heading` renders `tabindex="-1"`, **and** its props type still rejects a `tabIndex` prop — enforced the way T173 enforces `className`/`style`, through the build gate, since a type error is not a test failure |
| **AC10** | mounted inside `<StrictMode>`, the final `activeElement` is the same heading (precedent: `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:68`) |

### Growth of three closed-task instruments
As listed in Files-to-modify. Each is an instrument that must **admit new state** — a sixth render site, a
fourteenth file, a fourth attached screen — which is the permitted reason to edit a closed task's tests.

### The mutation harness (eleventh copy, phase 7)
At least one row per criterion **AC1–AC10**, each predicting `own` / `inherited` / `unfalsifiable`, with
controls. Two machinery items carry forward:
- the `in:` file list over-reports because `FAIL` lines and `×` lines are parsed separately — **parse them in
  document order** (the tenth copy deferred this);
- the `total < base_total → BROKEN` guard stays, and a mutant that cannot compile is **not** a kill.

---

## Risks this plan accepts, and where each is checked

1. **`Heading` gaining `tabindex` could blunt an existing query.** `screensRender…:515` takes the *first*
   `[tabindex]`. Verified safe today (no `Heading` in `RateLimitNotice`), and step 1 re-measures rather than
   trusting the reading.
2. **F15's "no T41 test should break" is a prediction.** Step 1 and the full run at step 8 are where it
   becomes a measurement; the notes record the run, not the expectation.
3. **AC8 is structural, not behavioural.** Named in the suite itself and carried as OQ-T174-5, so the gap is
   visible at the instrument rather than only in an artifact.

## Open questions
**No blockers.** OQ-T174-3, -4 and -5 are carried from the freeze; -1 and -2 are closed there.
