<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T42 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Model** | Claude Opus 5 |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md` · `artifacts/11-mutation-run.txt` |

## Verification performed

Run on the committed tree after phase 11's edits.

| Command | Result |
|---|---|
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **58 / 58**, exit 0 |
| `tsc --noEmit` | **exit 0** |
| `oxlint --no-ignore -c .oxlintrc.json src` | **exit 0** |
| `vitest run` (whole suite) | **1886 passed, 1 failed of 1887** — the known host `oxlint@1.87.0` case |
| `python3 -I artifacts/07-mutation-harness.py` (31 rows) | **28/29 regressions killed** · canary TE20 killed · TE21 unfalsifiable as predicted · **TE28 the one survivor** · **0 BROKEN** · 5 disagreements |

The named suite declares 23 `it(` blocks under task 42's `describe` and 58 cases run in the file, because
task 41's 35 cases live in the same file and task 42's AC1 and AC5 cases loop over the eleven paths.

---

## 1 · Traceability

| Requirement / decision | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **task 42** *"A session renders the shell and the authenticated routes"* | **yes** | `app/AuthenticatedLayout.tsx:58-94`, nested inside the gate at `app/routes.tsx:99` | AC1 (eleven paths), AC2, AC4/AC15; TE01, TE03, TE07, TE08 | — | **D-2** — a shell already wrapped every route (`AppShell`, task 1), so the statement was *literally* true before this task. What did not exist was anything **between** the gate and the eleven paths. Recorded in the freeze, not discovered late |
| **task 42** *"Verifies `unauthenticatedRoutesRedirectToSignIn`"* | **yes** | the named suite, 58 cases, 23 of them task 42's | the file itself | — | — |
| **task 42** *"Cites R28"* | **yes** | R28's positive half; the negative half is unchanged from T40/T41 | AC1, AC4, AC15 | — | — |
| **task 42** *"Blocked on 173, 174"* | **satisfied** | both closed PASS (2026-11-12, 2026-11-14); `Banner` is T173's ninth primitive, `HomeScreen` nests in this layout | AC2, AC7 | — | — |
| **R28** *"while unauthenticated, only the public routes are served"* | **yes**, both halves | the layout is a child of `RequireSession` (`app/routes.tsx:96-105`) — a gate read at render, so no session renders no chrome | AC4 **and** AC15 (empty-string token); TE07 (the layout wrapping the gate), TE24 (the layout on public routes) | — | — |
| **R19** *"clears in-memory token state and ends the SAS session"* (owner, OQ-T42-2) | **yes** | `AuthenticatedLayout.tsx:90` → `endSession()` — the **first caller the sign-out path has ever had** | AC13 behavioural (real navigation to `/connect/logout`, token cleared) + AC13 by source; TE16, TE17, TE22 | — | **OQ-T36-5 closes for its sign-out half.** Its data half stays open as OQ-T33-2 |
| **R22** *"never show a half-authenticated screen"* | **yes** | the gate is the parent; a receiving tab renders `RedirectToSignIn` in place of `<Outlet />` (`:85`) | AC14 ×4 (sibling, unmount, StrictMode, return target); TE18, TE19, TE23 | — | **D11** — the redirect component owns destination, return target and replace-vs-push |
| **R22's last clause** *"sign-out shall propagate to every open tab"* | **yes, for every tab behind the gate** | `AuthenticatedLayout.tsx:78-82` — the mechanism's first installer (T39 built it with none) | AC14 ×4; TE18, TE23 | **a tab on a PUBLIC path does not react** | **OQ-T42-5** — pinned by a named case, not left silent. Closing it means an app-level listener on `AppShell`, which L12 keeps this task off |
| **R57** *"the end-session navigation is served by SAS"* | **yes** | the layout builds no URL and never touches `globalThis.location`; `endSession()` does both | AC13 by source (nineteen forbidden reaches, each with a probe in the paired control); TE16 | — | — |
| **R20** *"profile data from `/userinfo` and `/accounts/me`, never from the token"* | **yes, vacuously** | the layout reads no identity at all | AC6/AC10; TE14 (the layout reads the access token) | — | — |
| **R29 / L16** *"nothing stored client-side beyond the carve-out"* | **yes** | no storage write, no network call | AC11; TE15 | — | — |
| **L11** *keyboard-operable, managed focus, WCAG AA* | **yes** | `ActionButton` is a real `<button type="button">` with the focus ring T173 pins; the layout takes no focus and renders no non-negative `tabIndex` | AC3, AC8, AC9, AC12, the no-clip case, the DOM-order case; TE05, TE06, TE13, TE25, TE29, TE30 | **a real Tab keypress** (OQ-T174-5, inherited) | — |
| **L12** *phase additivity — tasks 43 onward nest here* | **yes** | one pathless route holding the eleven as children; the element slot a later task needs is untouched | AC5, AC2; TE08 | — | — |
| **L14** *the declared route sets are the single source* | **yes** | `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` unchanged in content, spelling and order | AC5; TE08, TE24 | — | — |
| **L3** *no PII, no token, no role in the DOM* | **yes** | the chrome's whole text is one catalogue word | AC6/AC10; the two grown identity suites pin the signed-in document's exact text | — | — |
| **`agents.md`** *no hardcoded copy in a component* | **yes** | `t('shell.signOut')`, one group and one key (`i18n/catalogue.ts:79-82`) | AC6; TE09, TE10 | — | — |
| **`app/ui/index.ts` rule 1** *no primitive accepts `className`/`style`* | **yes** | `Banner`'s props are `{ children: ReactNode }` and nothing else (`app/ui/Banner.tsx:36`) | T173's prop-type case, grown to nine | — | — |
| **`app/ui/index.ts` rule 2** *the attribute that is its own contract is set inside* | **yes** | `<header>` carries the `banner` role **natively**; no `role` prop, no `role` attribute | AC9 (tag **and** ancestry), the no-accessible-name case; TE02, TE04, TE31 | — | **D2** — the ninth primitive. The alternative was not a cheaper landmark, it was no landmark |
| **T173 §6e** *no lowercase JSX tag in a render site* | **yes** | the layout renders `Banner`, `ActionButton`, `Outlet` and a fragment; the one lowercase tag is inside the primitive | AC7 (nine primitives, eight bearers, seven sites, union sixteen); TE11, TE12, TE20 | — | — |

## 2 · The two comments this task was told to make true, and one it missed

The brief listed three comments that task 42 falsifies (`02-task-implementation-brief.md:120,126,129`).
**Phase 6 corrected one; phase 8 and phase 11 both passed over the other two.** Phase 12 found them by
re-reading the brief's own list rather than the code:

| comment | state |
|---|---|
| `signOutClearsMemoryAndEndsSasSession.test.tsx:1745-1761` — `PERMITTED_IMPORTERS` written empty *"for whichever task first called the sign-out path"* | **corrected in phase 6.** The list names the layout and the comment says the day arrived |
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:889` — *"nothing in the SPA calls `endSession()` (OQ-T36-5), so every real arrival is someone typing the URL"* | **FALSE as shipped by phase 6.** The layout calls it; a real arrival at the signed-out screen is now a real sign-out |
| `api/signOutPropagatesToAllTabs.test.ts:9-12` — *"NOTHING INSTALLS THE LISTENER IN PRODUCTION … verified as a mechanism rather than as a journey. Task 39 is the ninth consecutive task in this position"* | **FALSE as shipped by phase 6.** This task is the installer and the journey is measured four times |

Both are corrected in this phase, comment-only, with no assertion touched — and the miss is recorded as
**D-3** rather than quietly fixed, because the brief had already done the hard part by naming them.

## 3 · What the last three rows said about the findings that asked for them

**TE29** — the chrome moved below the screen — is killed by **three** files: the new AC2 order assertion
**and both grown identity suites**, whose text pins are ordered concatenations. So phase 11's F1 was wrong
that nothing would catch it, and right that the claim had no direct instrument. Both halves are now true at
once: the direct case exists, and the run names who else was holding it.

**TE30** — a clipping wrapper in the layout — is killed by the extended scan **and by T173's §6e(3)**,
*"renders no lowercase JSX tag in any of the seven render sites"*. A `<div className="overflow-hidden">` in
a render site was already forbidden, for a different reason, by a rule written 27 tasks earlier. F4's hole
was narrower than it looked; the scan is kept because §6e forbids the **tag**, not the utility, and a
clipping utility can arrive on a primitive without a lowercase tag anywhere.

That same kill exposed a stale title: the case said *"six render sites"* while `RENDER_SITES` has held seven
since phase 6 of this task. Corrected here — **a count in a test's name is part of the test's claim**.

**TE31** — a named landmark — is killed by the F5 case and nothing else, with exactly one new failure. A
decision is now pinned as a decision.

## 4 · Deviations

| | |
|---|---|
| **D-1** | The freeze predicted **six** instrument failures; **nineteen** deterministic ones occurred. Four instruments nobody had named fired. Recorded in phase 6 — the freeze had made it a prediction *to be measured* |
| **D-2** | A shell already existed, so the task statement was literally true before the task. What was missing was the layer between the gate and the eleven paths |
| **D-3** | Two of the three comments the brief named as falsified were still false at the end of phase 11 (above) |
| **D-4** | The brief's constraint says *"exactly two auth modules"* and *"must not call `signOut()` directly"*; **D9** and **D11** raised that to three auth modules and one `api` module, because a receiving tab must sign out **without** broadcasting. The brief is left exactly as frozen — a freeze supersedes a brief by design — and phase 8's I2/I3 read the superseded sentence as current |
| **D-5** | **AC10**'s literal last clause, *"no `auth/` import"*, cannot be satisfied by any layout that installs a cross-tab listener. Case 8 verifies AC10's substance: the exact import allowlist plus the absence of every identity read |
| **D-6** | **TE21** flipped from survivor to kill when phase 10 pinned `px-6` on the pair. The pin is deliberate (a chrome and a screen with different horizontal edges do not line up); the row was re-aimed at the **vertical** padding, which is what T173's decision was about, and re-run |
| **D-7** | **TE28 survived.** The receiving tab's two statements are order-independent because React batches, so the layout's comment claiming the order was a criterion was corrected in phase 11. The row stays predicted `own` and reported as a disagreement |
| **D-8** | A test title said *"six render sites"* while the list it names has held seven since phase 6 of this task — introduced by this task's own edit, found by TE30's kill list, corrected in phase 12 |
| **D-9** | **FU-T42-1** — a latent order-dependent flake in T16's `signInStarts…` suite, sighted twice in phase 6 and not reproduced in any of the four harness runs. A sighting, not a diagnosis |

## 5 · The principal engineer's four questions

**(1) Is the task fully complete?** Yes. R28's positive half is built and nested, the eleven paths resolve
through one pathless layer, and the sign-out path has its first caller after seven tasks dormant. The ten
element-less paths are each a later unit's (**OQ-T42-3**), which the freeze carried deliberately.

**(2) Does it satisfy every acceptance criterion?** AC1–AC15 all have at least one case and at least one
mutant, with the two documented readings: **AC10** is verified in its amended form (D-5), and **AC14**'s
reach stops at the gate (**OQ-T42-5**, pinned by a case that must fail when a later task closes it).

**(3) Does it violate any LOCKED decision?** No. L11, L12, L14, L3, L16, R29, the catalogue rule and both
`app/ui` rules hold, each with an instrument and a mutant. The ninth primitive is `index.ts`'s own documented
escape hatch — *"a ninth must be added deliberately"* — paid for in a file whose comment argues the case.

**(4) Remaining risks.** Three, all named and none silent: a signed-in tab on a **public** path does not
react to a sibling's sign-out (**OQ-T42-5**); **no real second tab and no real Tab keypress** are measurable
in jsdom; and **a leak confined to StrictMode's first pass** cannot be distinguished from one live listener,
which the strict case says in its own comment.

**VERDICT: PASS** — every AC has a case and a mutant, 28 of 29 regressions die, the one survivor is a
comment corrected rather than a behaviour unmeasured, and the three risks that remain are each pinned by a
case that must fail when someone closes them.
