<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T33 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | all prior artifacts (00–11) |
| **Produces** | `artifacts/12-specification-verification.md` |

## What the spec asks of this task

`tasks.md:61` — "**Clean re-auth on failed renewal.** A failed renewal routes to re-auth with no
half-session. Verifies `failedRenewalTriggersCleanReauthWithoutHalfSession`. Cites R22."

`requirements.md:74-76` — R22: "IF renewal fails or the session is no longer valid, THEN the SPA SHALL
transition to clean re-auth, SHALL NOT show a half-authenticated screen, and SHALL show neutral copy.
Sign-out SHALL propagate to every open tab. **Refresh-token reuse detection removed (2026-10-10, Q5
resolved)**."

## Traceability matrix

Evidence cites `frontend/src/...`; the test column cites criteria in
`src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` (37 cases).

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R22 cl. 1 — transition to clean re-auth | **yes** | `auth/cleanReauth.ts:88` navigates to `SIGN_IN_PATH` | AC4/AC10 (`:441-495`), AC2 (`:272-326`) | no | **D1** — a screen that offers re-auth, by consent |
| R22 cl. 2a — no token is still held | **yes** | `auth/cleanReauth.ts:74` `clearAccessToken()`, first | AC1 (`:240-270`), AC8 (`:386-440`) | no | no |
| R22 cl. 2b — no authenticated surface renders | **by construction only** | no authenticated route has an element (`app/routes.tsx:60`) | AC11 (`:517-535`), pinned + canary NR17 | **the route guard itself** | **D2** — environmental; T41 owns it |
| R22 cl. 3 — neutral copy | **yes** | `i18n/catalogue.ts:25`; `auth/signInNotice.ts:26-29` map | AC5 (`:537-615`) | no | **D3** — a new sentence, not the reused one |
| R22 cl. 4 — sign-out propagates to every tab | **not this task** | — | — | no | **task 39** (`tasks.md:67`) |
| R22 reuse half | **removed** | Q5 resolved 2026-10-10 (`requirements.md:69-76`) | — | no | no |
| R21 — one renewal per tab, no error loop | **pre-existing**, must not regress | `api/sessionBoundary.ts:69-74`, `:108-116` | AC2, AC3 (`:328-349`), AC7 (`:684-703`) | no | no |
| L4 — enumeration-safe copy | **yes** | the sentence names the event, never a cause | AC5 (`:546-577`), AC9 (`:622-638`) | no | no |
| L3 / L16 — nothing stored but the carve-out | **yes** | the return target is the only write | AC6 (`:752-774`) | no | no |
| L11 — catalogue-only strings | **yes** | `auth/SignInScreen.tsx:73` renders `t(NOTICE_COPY_KEY[notice])` | AC5 (`:578-595`) | no | no |
| L14 — the declared route set, no new route | **yes** | `SIGN_IN_PATH` from `app/routes.tsx:13`, in `PUBLIC_ROUTE_PATHS` | AC4/AC10 (`:442-460`) | no | no |
| L18 — cross-tab coherence | **yes**, each tab transitions itself | `cleanReauth.ts` is per-tab by construction | AC7 (`:685-703`) | no | **D4** — follower proven by reason, not by a second tab |
| L1 — allowlisted post-login return target | **yes**, reusing the built primitive | `auth/cleanReauth.ts:81` via `rememberReturnTarget` | AC6 (`:753-774`), AC4 (`:498-516`) | no | **D5** — location, not state |
| `tasks.md:61` — the behaviour runs | **NO — the handler has no production caller** | AC12's scan over `src/` | AC12 (`:705-751`), scored as a canary | **the wiring** | **D6 — the material one** |

## Deviations

**D6 — the behaviour is correct and unreachable. This is the one that matters.** `createSessionBoundary` has
no production caller, so nothing in the running SPA ever invokes this handler. Phase 9 established that
wiring T33 itself would not change that: `grep -rn "createAuthApi\|readAccount\|readProfile" frontend/src`
outside tests returns **definitions and doc comments only**, so no authenticated request is issued, no 401
arrives, and no renewal is attempted. The dormancy is package-wide and ordered by `tasks.md`: tasks 5, 6,
30, 31 and 33 are all built and nothing calls any of them.

What T33 does about it, having been unable to close it: the cases drive the **real** boundary rather than a
stub, so they measure the integration point the wiring will use; and **AC12 pins the dormancy itself**, so
the day a production module constructs the boundary, a case fails and its message hands that author the
instruction — pass `createRenewalFailureHandler(navigate)` as `onRenewalFailed`. Raised through phases 3,
8 and 11 as a Blocker each time, and escalated as **OQ-T33-2**.

**D1 — landing on `/app/sign-in` is a screen that offers re-auth, reached by consent.** Kimi's phase 3 and
the frozen brief's G1. The two alternatives are worse: `/app/signed-out` is R19's deliberate-sign-out
landing and would state something false, and an automatic `/oauth2/authorize` navigation re-enters the
mechanism that just failed — the error loop R21 forbids — with no `prompt=none` set, so it would drop the
visitor into the hosted login page mid-task. The AC4/AC10 case at `:461-478` ties the destination route to `SignInScreen`, which is
the half nobody owned until phase 10.

**D2 — "no half-authenticated screen" is environmental for the second clause.** No route guard exists; T41
owns it (`app/routes.tsx:49-50`). What makes the claim true today is that no authenticated route has an
element, which is a fact about the repository rather than a property of this code. AC11 pins it and names T41 in its failure message.

**D3 — a new sentence rather than the reused `discarded` one.** Reusing it was the obvious move and is
false: that sentence says sign-in did not complete, and a failed renewal happens to a visitor who signed in
possibly hours ago. L4 does not require the reuse — it makes copy identical across **account states and
token conditions**, not across every event in a session's life.

**D4 — the follower-tab criterion is proven by rejection reason, not by a second tab.** A `SessionTokens`
whose renewal rejects with `SiblingRenewalFailedError` is exactly what a follower's `SharedRenewal` hands
its boundary. Standing up a second BroadcastChannel tab would re-prove task 6's mechanism, which task 6
owns and tests in 633 lines.

**D5 — the return target preserves the location, not the state** of whatever was interrupted. Two
consequences, both pre-decided elsewhere: a parameterised route is not allowlisted (T24-D5), so a visitor
on `/invoices/123` returns to home; and a non-allowlisted target is silently ignored, so a tab already at
sign-in needs no special case.

**D7 — `inAppPath` is exported for its own test.** Its root result is otherwise unobservable, because `/`
is not an allowlisted return target and so is rejected exactly as `null` is. Justified in the module.

## LOCKED decisions

None violated. L1, L3, L4, L11, L13, L14, L16 and L18 are satisfied as traced above. No file under `spec/`
was modified. Three closed tasks' suites were edited — the `signIn` key-set pins in T28's, T31's and the
callback suite's files — each where its own comment asks for that justification, and T28's and T31's
no-cause checks were **strengthened** to hold the new sentence rather than exempt it.

## The approving principal engineer's answers

**(1) Is the task fully complete?** **It is complete as specified and dormant as built.** Everything
`tasks.md:61` names is implemented, instrumented and mutation-tested; the behaviour cannot run, for reasons
outside this task's reach, and that is disclosed in the module, in the test file, in a pinned canary and in
this matrix. I would not sign this off as "done" without the owner seeing D6.

**(2) Does it satisfy every acceptance criterion?** Yes, at the module level. The eleven frozen criteria
plus AC12, added at phase 11, each have at least one instrument; phase 10's clause-by-clause audit found
and closed the one gap (AC10's third clause).

**(3) Does it violate any LOCKED decision?** No.

**(4) Remaining risks?**

- **D6.** A correct component nobody calls decays: the next author to touch the boundary has no running
  behaviour to check against, only this suite. AC12 is the mitigation and it is a weak one.
- **The forbidden-cause word list is now load-bearing in two places** — the catalogue check and the surface
  sweep share it, which is the repair Kimi's phase 11 F3 asked for. Its account-state and token-condition
  groups come from L4's text; its `server|network` group is my judgement and is marked as mine in the file.
- **Helper duplication, now the most-raised item in this package.** The boundary harness is the third copy
  of tasks 5 and 6's shape, the `signIn` key-set enumeration exists in three closed suites, and the scan
  helpers are duplicated per instrument file. **OQ-T32-4**, raised independently by six reviews across two
  tasks.
- **One environmental suite failure persists** on this host only:
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, because this `oxlint@1.87.0` prints no summary line
  for the assertion that parses it. Pre-existing, unrelated, pinned by name in the harness baseline.
- **Spec drift, recorded not fixed:** `package.md:171` maps `reusedRefreshOrFailedRenewalTriggersCleanReauth`
  to R22 though `tasks.md:62` struck out the task that verifies it, and `package.md` §11's Q5 is not struck
  through though `requirements.md:69-76` records it resolved.

## Gates

| Gate | Command | Result |
|---|---|---|
| T33 cases | `vitest run src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | **37 passed, exit 0** |
| whole suite | `vitest run` | **1574 passed / 1 failed of 1575**, exit 1 — the one failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental on this host |
| type check | `tsc --noEmit` | **exit 0** |
| lint | `oxlint src` | **exit 0**, no output |
| mutation | `python3 -I .ai/prompts/frontend/T33/artifacts/07-mutation-harness.py` | **22/22 regressions killed · 2/2 canaries killed · 0 survivors · 0 broken** |

The two canaries are scored apart from the regression count, per Kimi's phase 8 I2: NR17 (an authenticated
route gains an element) and NR24 (a production module constructs the boundary) describe changes **elsewhere**
that must still be caught the day they arrive, not regressions in this task's code. Mixing them into the
score would inflate it.

Every mutant's failure count is recorded in the run above, because a kill count alone does not show that a
mutant killed through the instrument meant for it. The two coarse ones are deliberate: NR18 (38 failures)
changes the shared route table, and NR23 (18) changes the notice module every sign-in case reads.

## Verdict

**PASS** — with D6 recorded as an open blocker for the owner, not for this task: every acceptance criterion
is satisfied and instrumented at the module level, and the behaviour cannot run in the SPA until a wiring
task exists, which no unit of `tasks.md` currently owns (**OQ-T33-2**).

**Phase 12 complete.**
