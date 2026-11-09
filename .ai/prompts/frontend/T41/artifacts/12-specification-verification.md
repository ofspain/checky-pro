<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T41 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md`, `artifacts/11-mutation-run.txt` |

## Final gates

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | **35 / 35** |
| `vitest run` | **1780 / 1781** — the one failure is the known host-environmental `lintRejectsHandWrittenBackendFetch` case (this host's `oxlint@1.87.0` prints no summary line) |
| `python3 -I …/07-mutation-harness.py` | **22/22 regressions killed, every kill `KILLED/own`, 0 survivors, 0 unfalsifiable, 0 broken, 0 disagreements** |

## 1 · Traceability matrix

`tasks.md` task 41: *"**Unauthenticated redirect.** An unauthenticated request for any other route redirects to
sign-in. Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites R28."*

`requirements.md:99` — **R28**: *"WHILE a visitor is unauthenticated, THE SPA SHALL serve only the public routes
listed verbatim in `design.md` §4c and SHALL redirect any other route to the sign-in start."*

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R28**, second clause — redirect any other route to the sign-in start | **yes** | `auth/RedirectToSignIn.tsx:73`; the three arrivals at `app/routes.tsx` (index route, catch-all) and `auth/RequireSession.tsx:47` | AC1/AC2/AC3 cases, 7 arms | no | no |
| **R28**, first clause — serve only the public nine | yes (T40) | `routes.tsx`'s gate | T40's AC1; T41's AC4, 6 arms incl. the signed-out landing by name | no | no |
| **R28**, the **WHILE** — unauthenticated only | **yes** | `RedirectToSignIn.tsx:50` returns `null` for a held session | AC5's three cases; harness RD02 | no | no, and it closes phase 3's F7 |
| **L1** — the visitor's intent survives sign-in | yes | `rememberReturnTarget(arrival)` at `:67`, allowlist-filtered | AC7/AC14, 6 arms; `takeReturnTarget()` round-trip | no | no |
| **L4** — nothing discloses a cause | yes | one component for all three arrivals; no copy of its own | AC9 (`Set` size = 1 on destination, action and markup) | no | no |
| **L11** — no router-supplied English | yes | the catch-all carries an element | task 16's grown case at `:144` | no | no |
| **L12 / L14** — the route set is exactly §4c | yes | the two arrays are untouched | task 16's `pathTree` and the 20-path pin; T41's AC13 | no | no |
| **L16** — storage holds only the listed items | yes | `RETURN_TARGET_KEY` reused; no new key | AC8's `setItem` key-inventory case | no | no |
| `package.md` §8 — `unauthenticatedRoutesRedirectToSignIn` | **yes, created** | `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx`, 35 cases | — | no | no |
| `package.md` §8 — `publicRouteSetIsExactlyTheDeclaredSet` | grown, not bypassed | 6 of T40's arms and T37's AC9 | — | no | no |
| `package.md` §8 — `homeRouteRendersLandingScreenAndManagesFocus` | **not this task** | — | — | task 42's; blocked on 173/174 | no |

## 2 · Acceptance criteria

| | criterion | met by |
|---|---|---|
| **AC1** | (a) eleven blocked paths end at the sign-in start | *"sends all eleven to the sign-in start, replacing the entry"* + StrictMode + the empty-string token |
| **AC2** | (b) a path matching nothing | the unmatched case and the `sign-in-x` look-alike |
| **AC3** | (c) the bare `/app/` | `/app/` and `/app` |
| **AC4** | the nine are still served | matching for all nine (callback render-free), rendering for eight, the signed-out landing by name, the sign-in screen with no loop, query string, trailing slash |
| **AC5** | with a session, no redirect | the eleven; plus F7's two arrivals outside the gate |
| **AC6** | replaces rather than pushes | asserted on every redirect arm, in the source, and now in AC9's shared value (phase 11, F3) |
| **AC7** | (a) remembers, (b) and (c) do not — **by the allowlist, not by a branch** | 6 value cases **and** the no-branch source scan, which is where this rule actually lives |
| **AC8** | no new storage key | the `setItem` key-inventory case |
| **AC9** | the three arrivals indistinguishable | one destination, one action, one markup string, plus byte-identity with a direct arrival |
| **AC10** | no catalogue entry, no copy | the catalogue scan and the byte-identity case |
| **AC11** | one spelling, read inside a function | two source cases, plus `replace` asserted in source |
| **AC12** | instruments grown, not bypassed | eleven grown; harness rows RD15–RD20 fail if any is reverted |
| **AC13** | the route set unchanged | T41's AC13 case and task 16's pins |
| **AC14** | the arrival is read basename-relative; the **stored value** is pinned | the exact-value case, the per-path case, the doubled-prefix case |

**AC14's criterion is met and its stated justification was wrong.** The frozen brief says *"`useLocation().pathname`
includes the `/app` prefix — the router's own state proves it"*. The second half of that sentence is the error
made visible: the router's **state** is what carries the prefix; `useLocation()` does not. The brief is frozen
and was not edited. What AC14 *requires* — a basename-relative arrival and a case pinning the stored value — is
exactly what is built and measured; the implementation that the justification would have produced is the one
phase 9 **removed**.

## 3 · Deviations

| | |
|---|---|
| **D1** | Phase 5 predicted **seven** firing instruments; **twenty-six** fired. Nine were T40 arms that were never wrong — `withProbes` identified the gate as *the pathless child*, and the index route made that two. The gate is now the pathless child **with children** |
| **D2** | Two files beyond §3's list were touched: tasks 5/6/23's callback **seams**, which resolved no access token and used *home vs sign-in* as a discriminator that only works while home renders nothing. Verified independently (Kimi I1) that no assertion in either suite is weakened |
| **D3** | I ran the mutation harness on uncommitted phase-9 edits; `revert()` destroyed them and the run then reported a clean result for a tree I had already changed. Nothing lost permanently; the sequence was redone as fix → verify → **commit** → mutate |
| **D4** | The basename strip was removed from production at phase 9 — a line **deleted**, not added, on a measurement that contradicted the frozen brief's reasoning (above). Two harness rows were rewritten because they had been mutating code that could not run |
| **D5** | Phase 3's F2 remedies were both **refused** at phase 4 and the arithmetic corrected (three → **four**, not five); the instrument the challenge missed was T40's own **AC13** |
| **D6** | Phase 7 labelled RD09 `unfalsifiable`. Kimi's I3 showed that behaviourally unfalsifiable is not unkillable when **source** is an instrument; the category is now empty and the rule has the scan it needed |
| **D7** | Kimi's phase-11 **F2** was accepted as a cheap cross-check with its **rationale rejected**, and the rejection was then confirmed by measurement — see §4 |

## 4 · Phase 11 findings — verdicts

| | finding | verdict |
|---|---|---|
| **F1** | the sweep misses `listenForSignOut`, `markSignedIn`, `setSignInNotice`, `useNavigate`, `useHref`, `switch`, the two other return-target entry points | **ACCEPTED** — 25 → **37** rows, each with a paired probe. `hasSignedOut` was added beyond the list: a redirect that consulted the sign-out latch would be deciding *why*, and L4 forbids the three arrivals differing even to the component itself |
| **F7** | add `window.name` and `history.pushState` | **ACCEPTED, broadened** — one `\bwindow\b` row and one `\bhistory\.` row. The module reaches neither, so the broad form is strictly stronger |
| **F14** | add an explicit `document.cookie` row | **REJECTED with the same reasoning, applied the other way** — `\bdocument\b` is strictly broader, and a row that can only fire when a broader one already has adds a number, not a surface |
| **F3** | AC9 proves sameness, not that the shared action is `REPLACE` | **ACCEPTED** — three arrivals that all pushed would have collapsed to one action and passed. The shared value is now named |
| **F4** | the no-branch scan is brittle and incomplete | **ACCEPTED** — both anchors must exist (a rename now **fails** instead of reducing the scan to `''`), the read must precede the call, and the keyword set covers `switch`, `while`, `for`, `try`, `do` |
| **F6** | no scan enforces that the session check comes first | **ACCEPTED** — a new case pins the order of the three reads in the function body |
| **F5** | the bare-`/app/`-with-session case does not assert the target is untouched | **ACCEPTED**, and it is the **load-bearing** one: the index route is outside the gate, so that arrival really does mount the redirect for a signed-in visitor |
| **F2** | the same assertion inside the AC5 authenticated-path loop | **ACCEPTED as a cross-check · RATIONALE REJECTED · then settled by measurement.** On an authenticated path **with** a session the gate renders `<Outlet />`, so `RedirectToSignIn` never mounts and the order of its reads cannot be measured there. Rather than argue it, phase 12 added **RD21** — the remember call moved **above** the session check — and the run says it is killed by **F6's order scan and the no-branch scan**, with **no AC5 behavioural case failing at all**. F2's assertion would not have caught the hazard F2 described; F5's arrival and F6's scan do |
| **F8**–**F13** | the byte-identity case is correct; `REMEMBERABLE` is right; excluding `callback` is right; the `takeReturnTarget` round-trip is strong; phase 9's removal was correct; RD09 is now killed | **ACCEPTED as confirmations**, no action |

Two harness rows were added for the two findings that describe real regressions rather than missing patterns
(the paired probes in the control already cover a pattern's existence):

- **RD21** the arrival remembered **before** the session is checked → killed by F6's scan and the no-branch scan.
- **RD22** a `switch` between the arrival and the allowlist → killed by the no-branch scan, the sweep, **and the
  control**, which fires because the real source now trips the new `switch` row. That is the control doing its
  second job: proving the sweep is not already satisfied by the source it is scanning.

## 5 · The principal engineer's four questions

**(1) Is the task fully complete?** Yes. All three arrivals R28's *"any other route"* denominates are built and
measured; the named test exists with 35 cases; eleven instruments of four other tasks were grown rather than
bypassed; 22 of 22 mutants die by cases in this task's own files.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1–AC14, with AC14 met and its frozen justification
corrected rather than honoured. AC12's claim is itself measured: RD15–RD20 revert each grown instrument and
each dies.

**(3) Does it violate any LOCKED decision?** No. L1 is served by the return target; L4 by one component for
three arrivals plus the indistinguishability case; L11 by the catch-all's element; L12/L14 by two untouched
arrays; L16 by reusing `RETURN_TARGET_KEY` with a key-inventory assertion. No spec file was modified.

**(4) Remaining risks?**

- **OQ-T41-1** — the basename-spelled destination cannot be a harness row: it exhausts the V8 heap rather than
  failing. Narrowed at phase 9, since `APP_BASENAME` is no longer imported by the module at all.
- **OQ-T41-2** — the end-to-end deep link (link → sign-in → back to the link) needs a screen behind the gate, so
  it is task 42's. Both ends are pinned separately here.
- **OQ-T41-3** — `useLocation()` being basename-relative is now load-bearing in the opposite direction; the
  stored-value cases are what would notice a router change.
- **OQ-T40-1** (carried) — the session is read at render, not reactively. A token arriving with no navigation
  would leave a decided redirect in place. Nothing does that today.
- **FU-T37-2** (carried) — `routes.tsx:5`'s TDZ claim is false as written.
- Task **42** is blocked on 173/174, so every authenticated route behind the gate is still element-free.

## 6 · Verdict

**PASS** — R28's second clause is built for all three arrivals, measured by 35 cases and 22/22 mutants with no
kill outside this task's files, and the one reviewer finding that contradicted the frozen brief turned out to be
right, which removed a line of production code rather than adding one.
