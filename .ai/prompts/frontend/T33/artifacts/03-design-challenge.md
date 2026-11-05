<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T33 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### Issue: Landing on `/app/sign-in` is a screen that *offers* re-auth, not a transition *to* re-auth
· **Severity:** **Major**
· **Evidence:** `SignInScreen.tsx:50-77` renders a heading, an explanation, and a button labelled "Continue to sign in". The button navigates to SAS's `/oauth2/authorize` only when clicked. `SignInScreen.tsx:73` renders the `discarded` notice only while `status === 'idle'`.
· **Assessment:** The brief interprets R22's "transition to clean re-auth" as landing the visitor on `/app/sign-in`, where one further click starts the flow. This is defensible — it matches R23's "resume only on user action" posture — but it is still an interpretation. A stricter reading of R22 could require the visitor to be handed directly to SAS (the actual re-auth mechanism), with the SPA screen acting only as the carrier of neutral copy. The brief rejects automatic `/oauth2/authorize` navigation on good grounds, but the chosen destination is therefore a *prelude* to re-auth, not re-auth itself. The owner should confirm that this satisfies R22.
· **Recommended brief amendment:** Record G1 as an owner decision with the rationale, or add an AC that proves the destination screen is the *start* of the auth flow and not an authenticated surface. If the owner wants a more direct transition, the brief should instead land on `/app/signed-out` with a new "session ended" notice and a single action that navigates to `/oauth2/authorize`.

### Issue: T33 delivers routing that has no production caller
· **Severity:** **Major/Blocker**
· **Evidence:** The brief states `createSessionBoundary` has no production caller (Phase 0, §2) and T33 will implement `onRenewalFailed` as a module with injected dependencies, but will **not** construct the boundary in `mountShell.tsx`. OQ-T33-2 is left open.
· **Assessment:** A routing module with no caller is correct code that never runs. The brief compares this to T32's forward guard, but T32's instruments are static scans that fire on future code; T33's behavior requires a runtime hook. Until a wiring task connects `createSessionBoundary(..., onRenewalFailed)` into the SPA, the acceptance criteria cannot be exercised against a running application. The brief needs either (a) a commitment that T33 also does the wiring, (b) an identified owner task that will consume this module, or (c) an explicit acknowledgment that AC8 is unprovable in production until that task exists.
· **Recommended brief amendment:** Freeze OQ-T33-2 with one of the three resolutions above. If the wiring owner is unknown, add it to the Open Questions and do not claim AC8 is satisfied beyond source-side inspection.

### Issue: The new `SignInNotice` copy is not specified
· **Severity:** **Major**
· **Evidence:** G3 decides to add a second `SignInNotice` member because `'discarded'` copy is false for a failed renewal. AC5 requires neutral copy naming no cause, status or step. The brief does not propose the actual string.
· **Assessment:** Without the copy, the design cannot be reviewed for L4/L11 compliance. The copy must be indistinguishable from other identity-failure copy, must not name the failure cause, and must work on the sign-in screen alongside the existing explanation and action button.
· **Recommended brief amendment:** Add the proposed catalogue key and English text to the brief. For example: `sessionEnded: 'Your session has ended. Continue to sign in.'` — but the exact wording must be reviewed against L4 and the existing `signIn.explanation`.

### Issue: AC8 is a source-side claim because no route guard exists
· **Severity:** **Major**
· **Evidence:** AC9 (in the brief's table) says "No half-authenticated render. Instrumented as a **source-side** claim plus the measured fact that no authenticated route has an element today (`routes.tsx:46`); a rendered proof is impossible until the wiring and the guard exist."
· **Assessment:** The source-side claim is that the module clears the token and navigates to a public route. But without a route guard, a tab that fails renewal while on an authenticated route will briefly continue to render that route's element (if one existed). Today no authenticated route has an element, so the measured fact holds. The moment a task adds an element to an authenticated route, this instrument becomes a forward guard rather than a present proof.
· **Recommended brief amendment:** Make AC8/AC9 explicit that the "no half-authenticated render" proof is environmental today and becomes a T41 (route guard) obligation. Add a source scan that asserts authenticated routes have no elements, so the environmental assumption is pinned and will fail when it stops being true.

### Issue: The production module's location and name are unspecified
· **Severity:** **Minor/Major**
· **Evidence:** The brief says "A production module that routes a failed renewal to clean re-auth" but does not name it or place it. The test file is planned for `src/api/`, but routing logic that imports `signInNotice`, `routes`, `returnTarget`, and `accessToken` may fit better in `src/auth/`.
· **Assessment:** Placement affects import cycles. `routes.tsx` already documents a cycle involving `authorizeRequest.ts`. If the routing module imports `routes.tsx` for `SIGN_IN_PATH`, it must avoid closing a new cycle. The brief says navigation is injected to avoid importing the router, but it is silent on importing `routes.tsx`.
· **Recommended brief amendment:** Specify the module path and name, and verify it does not create a new import cycle. A safe choice is `src/auth/failedRenewalRoutesToCleanReauth.ts` (or similar), which imports `routes.tsx` for the path constant and accepts `navigate` as a parameter.

### Issue: The return-target preservation may return a visitor to a stale authenticated surface
· **Severity:** **Minor**
· **Evidence:** G4 decides to call `rememberReturnTarget` before leaving for re-auth, reusing the allowlisted mechanism.
· **Assessment:** Remembering the target is good UX and permitted by L16(a). However, if the visitor was in the middle of an action that depends on session state, returning them to the same screen after re-auth may show stale data or re-trigger the same renewal failure. This is not a T33 defect — it is the responsibility of the screen that renders after return — but the brief should not imply that remembering the target guarantees a seamless resume.
· **Recommended brief amendment:** Add a note that `rememberReturnTarget` preserves the *location*, not the *state* of the interrupted task, and that post-return behavior is owned by the rendering task.

### Issue: The transition must be idempotent when the visitor is already at the destination
· **Severity:** **Minor**
· **Evidence:** G6 says the transition must be idempotent and run once per renewal. AC2 says it runs exactly once per renewal.
· **Assessment:** If a tab is already at `/app/sign-in` when renewal fails (e.g., a second sign-in attempt in a tab that still holds a token), calling `navigate(SIGN_IN_PATH)` may be a no-op. The module must still clear the token and set the notice. The brief mentions "a second sign-in attempt in a tab that already holds a token" in the test plan but does not say how idempotence is expressed when navigation is a no-op.
· **Recommended brief amendment:** Clarify that idempotence means the side effects (clear token, set notice, remember return target) are safe to repeat, regardless of whether `navigate` changes the URL. Add a test for the already-at-destination case.

### Issue: AC6's "no failure info in surfaces" conflicts with writing the return target
· **Severity:** **Minor**
· **Evidence:** AC6 says "Nothing about the failure — no token, no reason, no flag — is written to `sessionStorage`..." The brief then says the return target is the one permitted write (L16(a)).
· **Assessment:** The return target is written to `sessionStorage`. The brief correctly notes it carries no failure information, so AC6 is satisfied in spirit. But the wording of AC6 could be read as an absolute prohibition on any `sessionStorage` write. The brief should make the exception explicit in the AC text.
· **Recommended brief amendment:** Rewrite AC6 to say "No failure information reaches ...; the only permitted write is the allowlisted return target, which carries no failure information."

### Issue: The boundary's `onRenewalFailed` hook receives a `reason`, but the routing must not use it
· **Severity:** **Minor**
· **Evidence:** `sessionBoundary.ts:50` types `onRenewalFailed` as `(reason: unknown) => void`. The brief's module will receive this reason but must not render or log it.
· **Assessment:** This is consistent with L4 and AC6. But the brief should be explicit that the module ignores `reason` and does not branch on it, otherwise a future maintainer might add cause-specific copy.
· **Recommended brief amendment:** Add a constraint that the routing module ignores the `reason` parameter and does not branch on it. Add a source scan or a test proving no cause-specific copy is selected.

### Issue: The follower-tab case needs clarity on which tab performs the navigation
· **Severity:** **Minor**
· **Evidence:** AC7 says a follower tab that adopts a sibling's `renewal:failed` performs the same transition once. The test plan includes "a follower adopting `renewal:failed`".
· **Assessment:** The `crossTabRenewal` mechanism presumably broadcasts the failure to followers. Each follower tab has its own `onRenewalFailed` hook. The brief should clarify whether each follower navigates independently, or whether the mechanism centralizes the transition. Independent navigation in every tab is the simplest model and matches R21/R22.
· **Recommended brief amendment:** Document that every tab (leader or follower) runs its own `onRenewalFailed` hook once per failure event, and that the hook is responsible for its own navigation and token clearing.

### Issue: The test harness should reuse but not duplicate the existing boundary harness
· **Severity:** **Minor**
· **Evidence:** The test plan says T33 reuses the shape of `parallel401sTriggerExactlyOneRenewal.test.ts` and `siblingTabsShareOneRenewal.test.ts`.
· **Assessment:** Reusing is good, but duplicating the fake transport / fake session harness in a third file is more helper duplication. The existing two files already share similar code inline.
· **Recommended brief amendment:** Extract the boundary-test harness into a shared helper module, or at least document that T33's test will copy the pattern. This is less urgent than T32's scan-helper duplication but worth noting.

---

## Summary

The brief makes reasonable decisions on the three phase 1 blockers, but the two largest risks are **G1** (whether landing on `/app/sign-in` satisfies "transition to clean re-auth") and **G2** (whether delivering unwired routing satisfies the task). Both need owner confirmation. The design should also specify the new catalogue copy, the production module's name and location, and the exact scope of AC8 before freezing in phase 4.

**Phase 3 complete.** Ten findings returned; no code or contract changed.
