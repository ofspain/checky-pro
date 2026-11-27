<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# frontend · T47 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

Commands run independently on the tree as delivered by Phase 7. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | **17 / 17 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2010 / 2010 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T47/artifacts/07-mutation-harness.py` | **13/14 regressions killed**, 1 canary killed, 2 expected survivors (TR10, TR15), 1 canary survived (TR16), **0 broken**, **1 disagreement** (TR14 — stronger than claimed) |

The whole-suite result matches the Phase 7 claim of a fully green 2010/2010 baseline. The harness matches Phase 7's reported results row-for-row: TR10 and TR16 survive as predicted, confirming the two Medium gaps; TR14 is caught by this task's own files as well as the inherited pins.

---

## Findings

### I1 — `replace: true` is genuinely unmeasured
· **Severity:** **Medium**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:96` navigates with `{ replace: true }`. A grep for `historyAction`, `'REPLACE'`, or `replace:` across `frontend/src/features/account` and the route-test files returns nothing. The self-review reports TR10 was written to predict this survival.
· **Assessment:** Confirmed independently. The design rationale in `RegisterScreen.tsx`'s header is explicit and correct — Back must not return to a filled form — but the suite does not hold it. A regression that dropped `replace: true` would not be caught.
· **Recommendation:** Same as self-review F1: add a case that asserts `router.state.historyAction === 'REPLACE'` after the submit, or stronger, that Back from the acknowledgement does not land on the form.

### I2 — The identical-output case never asserts its two probes differ
· **Severity:** **Medium**
· **Evidence:** `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:46-50` defines `NEW_ADDRESS` and `ALREADY_REGISTERED` with different `message` bodies, but the AC2 case at lines 157-176 never checks `NEW_ADDRESS.body !== ALREADY_REGISTERED.body`. The self-review reports TR16 survived as the canary for this.
· **Assessment:** Confirmed. The trap the suite tries to avoid — byte-identical mocks making the claim vacuous — is currently guarded only by a comment convention. Editing one mock to match the other would leave every AC2 assertion passing while the test measured nothing.
· **Recommendation:** Same as self-review F2: assert the two bodies differ inside the AC2 case, so the probe and the claim cannot drift apart.

### I3 — The T45 suite was modified despite the freeze
· **Severity:** **High (process)**
· **Evidence:** `git diff --stat` shows `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` changed by 35 lines. The Phase 2 brief listed this file under *Files NOT to Modify* ("the T44/T45 suites — R1 is closed"). The self-review reports 13 cases crashed once navigation landed and were fixed with one line per case.
· **Assessment:** Confirmed by reading the diff summary. The changes may be minimal and may not move any R1 claim, but they are outside the approved scope and must be ratified in Phase 9 or reverted with the navigation logic tested another way.
· **Recommendation:** Phase 9 should review the exact diff and either ratify it or move the necessary fixes into a different file (e.g., a shared helper or the T47 suite itself).

### I4 — Blast radius spans five files, not the one the freeze named
· **Severity:** **High (process / observation)**
· **Evidence:** `git diff --stat` across the expected files shows changes in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, `routesResolveUnderAppPrefixOnly.test.tsx`, `unauthenticatedRoutesRedirectToSignIn.test.tsx`, `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`, `homeRouteRendersLandingScreenAndManagesFocus.test.tsx`, plus `routes.tsx`, `catalogue.ts`, `RegisterScreen.tsx`, and the T45 suite.
· **Assessment:** Confirmed. Most of these changes are count/membership pins that necessarily move when a screen is attached, but the freeze only named one of them. The discrepancy between expected and actual blast radius is a planning gap.
· **Recommendation:** Update the task brief's *Files to Modify* list to reflect the real blast radius, or split the work so that closed-task files are changed by their own maintenance tasks rather than incidentally.

### I5 — The I9 guard was updated with a reasoned exclusion, not a fifth body-focus entry
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:432-479` keeps the four-screen "leave focus on body" loop and adds a separate case asserting `RegisterSentScreen` takes focus.
· **Assessment:** This matches the Phase 3 design-challenge recommendation. The comment explains why `RegisterSentScreen` is an exception (it is reached by client-side navigation), so the loop's reasoning stays coherent.
· **Recommendation:** None.

### I6 — The acknowledgement correctly ignores the server's `message` and uses catalogue keys
· **Severity:** **Positive**
· **Evidence:** `RegisterSentScreen.tsx` renders only `t('registerSent.heading')` and `t('register.success')`. The named suite at lines 204-213 asserts the rendered text equals the catalogue literal and does NOT contain the mocked server message.
· **Assessment:** This preserves L11 and makes R2's identical-copy claim provable from the client. The screen's header comment records D2 explicitly.
· **Recommendation:** None.

### I7 — The storage assertion is no longer vacuous
· **Severity:** **Positive**
· **Evidence:** The named suite spies on `Storage.prototype.setItem/getItem/removeItem` and filters out only the react-router internal key `remix-router-transitions`. It also includes a control proving the spy intercepts.
· **Assessment:** This addresses the T42 bug where an instance spy on jsdom storage did not intercept. The negative assertion now has a meaningful instrument behind it.
· **Recommendation:** None, beyond the self-review's F6 note that the react-router key name is a private dependency.

### I8 — The AC2 `innerHTML` comparison is a deliberate, if brittle, "copy AND layout" claim
· **Severity:** **Minor**
· **Evidence:** Lines 168-171 compare `innerHTML` between the two journeys, not `textContent`. The self-review F8 notes the separate `textContent` equality is whitespace-brittle.
· **Assessment:** The `innerHTML` comparison is the right choice for R2's "copy and layout" requirement, but it couples the test to React's rendered attribute order and any future primitive that injects wrapper markup. The trade-off is documented in the case comment.
· **Recommendation:** None; keep the equality and accept the brittleness as the cost of the stronger claim.

### I9 — The T47 `type` helper differs from the registration suites' pattern
· **Severity:** **Minor**
· **Evidence:** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:118-123` assigns `input.value = value` directly, while `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:129-138` uses `Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set` to bypass React's tracker.
· **Assessment:** For the initial value from empty, direct assignment works. The inconsistency is a maintenance hazard: a future copy of this helper into another suite could fail for non-empty fields. The self-review did not flag this.
· **Recommendation:** Optional: align the T47 helper with the registration suites' prototype-setter pattern and add a comment explaining why, or extract a shared helper (FU-T43-2 notwithstanding).

### I10 — The AC11 "no props" regex is literal and brittle
· **Severity:** **Minor**
· **Evidence:** Line 367 asserts `/export function RegisterSentScreen\(\): ReactElement \{/.matches(code)`.
· **Assessment:** The regex is precise for the current export signature but will break if the signature is reformatted (e.g., arrow function, multiline, or added whitespace). It is the same shape of assertion the project uses elsewhere for export signatures, so the brittleness is not new.
· **Recommendation:** None required; note it as the same pattern used in other suites.

---

## Summary

T47's implementation correctly realises the Phase 3 design decisions: `RegisterScreen` navigates to `register/sent` with `replace: true`, `RegisterSentScreen` renders only catalogue copy and moves focus to its heading, and the I9 guard is updated with a reasoned exclusion. The whole suite is fully green (2010/2010), confirming FU-T47-1 was resolved before this phase.

The two Medium gaps (F1/F2) are confirmed independently and are each backed by a harness survivor: TR10 (`replace: true` dropped) and TR16 (identical probe replies). The one disagreement, TR14, is in the safe direction — the task's own suite catches a path-spelling mutant, not only the inherited route-set pins. The two High findings (F3/F4) are process issues about scope and blast radius that Phase 9 must ratify or reverse. The remaining findings are positive observations or minor maintainability notes.

No production or test code was changed during this review. The only file modified is this artifact.

**Phase 8 complete.**
