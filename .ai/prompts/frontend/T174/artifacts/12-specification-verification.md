<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T174 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` · **Task** T174 — Home screen |
| **Model** | Claude Opus 5 |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md`, `artifacts/11-mutation-run.txt` |

## Verification performed

Every command below was run on the committed tree, after phase 11's edits.

| Command | Result |
|---|---|
| `vitest run src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | **29 / 29**, exit 0 |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` (whole suite) | **1862 passed, 1 failed of 1863** — the known host `oxlint@1.87.0` case, unrelated to this task |
| `python3 -I artifacts/07-mutation-harness.py` | **21/21 regressions killed · canary RE19 killed · RE18 unfalsifiable as predicted · 0 BROKEN · 5 disagreements** |

The suite declares 27 `it(` blocks and executes 29, because the phase-8 I9 guard is a three-iteration loop
over `SignInScreen`, `SignedOutScreen` and `CallbackScreen`.

---

## 1 · Traceability

| Requirement / decision | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **task 174** *"`/app/home` renders a landing screen"* | **yes** | `app/HomeScreen.tsx:48-61` · attached at `app/routes.tsx:92` | AC1 ×4, AC4 ×2 | — | — |
| **task 174** *"moves focus to it on arrival"* | **yes** | `app/HomeScreen.tsx:52-54` (`useEffect` → `headingRef.current?.focus()`) · `app/ui/Heading.tsx:32` (`tabIndex={-1}`) | AC2 ×3, AC10 ×2; RE01, RE02, RE03 | — | — |
| **task 174** *"so a completed sign-in lands somewhere rather than on a blank document"* | **yes** | `auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:1490` — a real callback, a real token exchange, `/app/home`, focus on this heading | the discharged T27-D1 case; RE02 must kill it | — | **the deferral was wrong twice.** OQ-T41-2 deferred this proof to task 42 and T174's own freeze deferred it again as D8/OQ-T174-4. The case already existed and had been failing by design for eleven tasks. Both closed in phase 6 |
| **task 174** *"Verifies `homeRouteRendersLandingScreenAndManagesFocus`"* | **yes** | `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx`, 29 cases | the file itself | — | — |
| **R28** *unauthenticated → only public routes, else redirect* (`requirements.md:99`) | **yes**, unchanged | the screen sits inside `RequireSession`'s children, `app/routes.tsx:88-94` | AC4 ×2 (redirect **and** the with-session control); RE06, RE07 | — | — |
| **L1** *"allowlisted post-login return target, falling back to the home route"* (`design.md:8-10`) | **yes** | `auth/returnTarget.ts` unchanged; the fallback now arrives at a screen | the T27-D1 case; RE02 | — | **D-6** — `returnTarget.ts:35-38` comment corrected (prose only) |
| **L11** *managed focus* (`design.md:47`) | **yes** | `HomeScreen.tsx:52-54` · `Heading.tsx:32` | AC2, AC8 ×3, AC10 ×2; RE01–RE04, RE22 | the behavioural Tab proof | **OQ-T174-5** — structural, not behavioural; no `user-event`, and jsdom has no sequential focus navigation |
| **L11** *keyboard operation* | **yes** | `tabIndex={-1}` is focusable by code and absent from the tab order | AC8 ×3; RE04 (−1 → 0), RE22 (keydown swallowed) | — | — |
| **L11** *catalogue-only strings* | **yes** | `i18n/catalogue.ts:61-70` (`home.heading`, `home.explanation`) · `HomeScreen.tsx:58-59` use `t()` | AC3 ×2; RE09, RE10, RE11 | — | — |
| **L11** *WCAG AA* | **inherited** | the screen adds no colour; it renders T173's `Screen`/`Heading`/`Text` | T173's five contrast cases | — | **no new claim.** T174 introduced no token and no class |
| **L11** *live regions for async results* | **n/a** | the screen has no async result | — | — | nothing to announce; `StatusRegion` is not rendered |
| **R22** *no half-authenticated screen* (`requirements.md:72`) | **yes** | nothing rendered depends on identity | AC6 ×3; RE12, RE13, RE23 | — | **D-1** — AC6's frozen import list omitted `react`; the prohibition it exists for is unchanged and asserted pattern by pattern |
| **R20** *profile data from `/userinfo`, never the token* (`requirements.md:66`) | **yes**, vacuously | the screen reads no profile data at all | AC5 ×2, AC6 ×2; RE13 | — | the authenticated data path is still dormant (**OQ-T33-2**) |
| **R29 / L16** *storage holds only the L16 items* | **yes** | no storage call in the screen | AC5 ×2, AC6; RE13, RE23 | — | — |
| **L12** *phase additivity* | **yes** | no route added, no endpoint, no contract, no config key, no storage key | AC7 ×3 | — | task 42 nests the shell around this; the screen is 12 lines and a landmark, not a placeholder to delete |
| **T173 §6e** *render through primitives, no lowercase JSX tag* | **yes** | `HomeScreen.tsx` has no lowercase tag | AC1 ×2 (scan **and** control), AC7; RE14, RE15, RE16, RE19 | — | the sweep was **structurally blind** to a screen with no lowercase tag; phase 6 closed it with a barrel-importer derivation plus a control |
| **`ui/index.ts` rule 2** *no primitive accepts the attribute that is its own contract* | **yes, literally** | `Heading.tsx:29` props type is still `{ children: ReactNode }` | AC9 ×3; RE05 (via the real build gate) | — | **D1.** The rule's own text says "set inside"; no prop, no type widening, no rule amendment, no ninth primitive |
| **`package.md:168`** `homeRouteRendersLandingScreenAndManagesFocus` → R28, L11 | **yes** | the named file exists and carries both | the file | — | — |
| **`package.md` §8** `phase1ScreensPassAccessibilityChecks` | **no** | — | — | **commissioned by no task in `tasks.md`** | **OQ-T174-3**, carried for the owner since phase 2. Same shape as OQ-T173-4 |

## 2 · The eight deviations, all recorded before this phase

| | | state |
|---|---|---|
| **D-1** | AC6's import list omitted `react`, which `useEffect`/`useRef` come from | recorded phase 6; the AC was literally unachievable as frozen |
| **D-2** | five instruments grew where the freeze named three | recorded phase 6; both extras were *"must admit new state"*, the permitted reason |
| **D-3** | `app/ui/Screen.tsx:6` comment corrected ("THREE CONSUMERS" → four) | recorded phase 6; one comment, no code, in a file the freeze put off limits |
| **D-4** | two case titles in `publicRouteSetIsExactlyTheDeclaredSet` renamed | recorded phase 6; both asserted the number in words and had become false |
| **D-5** | the freeze's F15 prediction ("no T41 test should break") was **false** — six failed | recorded phase 6, because the freeze required it be measured rather than assumed |
| **D-6** | `auth/returnTarget.ts:35-38` comment corrected | recorded phase 10; same category as D-3 |
| **D-7** | phase 9 corrected a **false diagnosis** carried in T41's and T173's files: `import.meta.url` *is* a `file:` URL under this config; `readFile(import.meta.url)` fails with **ENOENT**, not *"The URL must be of scheme file"* | recorded phase 9; the false reason would have told the next reader a working pattern is broken |
| **D-8** | phase 11 widened AC6's forbidden list with `history` and `indexedDB` | recorded phase 11; the freeze's own constraint names `history`, and the list had omitted it — unenforced, not weakly enforced |

**One inconsistency inside the freeze itself, found in this phase.** `04-freeze-task.md:142` titles D7
*"The copy is **three** catalogue entries"* while D7's own body, two lines below, says *"a new `home` group
with **`heading`, `explanation`** and nothing else"*. The implementation has two, matching the body and
matching AC3. The title is the error. No code or test is affected; it is recorded here so a later reader of
the freeze does not go looking for a third key.

## 3 · The four questions

**1 · Is the task fully complete?** Yes. `/app/home` renders a landmark with a heading and explanatory copy
behind the session gate, moves focus to the heading on arrival, and does it through T173's primitives with
catalogue-only strings. The named test exists with 29 cases. All three instruments the freeze named were
grown, and two more it did not predict. The end-to-end landing — a real callback, a real exchange, focus on
this heading — is proved, which is more than the freeze asked for: it had deferred that to task 42.

**2 · Does it satisfy every acceptance criterion?** AC1–AC10 all hold, each with at least one killing
mutant: AC1 → RE14/RE20, AC2 → RE01/RE02/RE03, AC3 → RE10/RE11, AC4 → RE06/RE07, AC5 → RE13, AC6 →
RE12/RE23, AC7 → RE15/RE16/RE17/RE19, AC8 → RE04/RE22, AC9 → RE05/RE08, AC10 → RE18 (unfalsifiable, scored
apart) with RE01 and RE02 as the behavioural guards. Phase 11 closed the one AC whose clause had a case and
no row — AC8's *no key handler* — so "every criterion has a mutant" is now true without a footnote.

**3 · Does it violate any LOCKED decision?** No. The one decision that was at risk was T173's `index.ts`
rule 2, one task after it was frozen: the screen needs a focusable heading, and the obvious route was a
`tabIndex` prop. The rule's own text prescribes the alternative — *"`role`, `aria-live`, `tabIndex`,
`type="button"` and the heading level are set inside"* — so `tabIndex={-1}` is set in `Heading` and the props
type is unchanged. A rule relaxed by its first customer was never a rule. L12 holds: no route, endpoint,
contract, config key or storage key changed.

**4 · Remaining risks.**

- **No behavioural Tab proof (OQ-T174-5).** AC8 asserts the three structural properties that cause trapping —
  `tabIndex` exactly `-1`, no non-negative `tabindex` anywhere in the render, no `keydown` listener, no
  `aria-modal` — and RE04 and RE22 show those assertions awake. What no case here can show is a real Tab
  leaving the screen. Closing it costs a dependency (`@testing-library/user-event`) or a Playwright case, and
  that is the owner's call.
- **No screen-reader announcement measured.** jsdom exposes no accessibility tree. That the `<h1>` is what a
  screen reader meets after the move is an inference from the DOM, not a measurement (phase 11 F9 agrees, and
  so does this file's own header).
- **StrictMode idempotence has no isolating mutant.** RE18 — the effect running on every render — is
  genuinely unfalsifiable: `focus()` on the already-focused element is a no-op. AC10's two cases die whenever
  focus breaks at all, so they corroborate idempotence rather than isolate it.
- **The copy is asserted for consistency, not quality.** AC3 proves the DOM matches the catalogue and that no
  raw key leaks. Whether *"You are signed in. There is nothing here yet."* is the right sentence is the
  owner's, and D7 put it in the catalogue precisely so it can be changed without touching a component.
- **One environmental failure persists**, `lintRejectsHandWrittenBackendFetch`'s oxlint-summary case: this
  host's `oxlint@1.87.0` prints no summary line. One case of 33 in that file; the other 32 include the
  repository's only end-to-end build gate, which runs a real `pnpm run build` and passes.
- **Six other mounting suites** still leave `IS_REACT_ACT_ENVIRONMENT` unset, so their isolated runs and
  their whole-suite runs are not quite the same measurement. Fixed for this task's file in phase 11; the
  other six belong to closed tasks and were left alone.

**VERDICT: PASS** — every acceptance criterion holds with a killing mutant, no LOCKED decision was bent to
make it fit, and the one gap phase 10 had to record as unmeasured is measured.
