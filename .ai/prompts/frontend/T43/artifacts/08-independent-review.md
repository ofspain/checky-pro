<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T43 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T43 — Registration accepts 12 characters |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

Commands run independently on the tree as delivered by Phase 7. No code or contracts were changed.

| Command | Result |
|---|---|
| `vitest run src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **34 / 34 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1930 / 1931 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `python3 -I .ai/prompts/frontend/T43/artifacts/07-mutation-harness.py` | **22/24 regressions killed**, 1 canary killed, 3 survivors (TR09, TR21 expected phase 10; TR24 unfalsifiable), **0 broken**, 1 disagreement (TR26) |

The whole-suite count and harness outcome match the self-review exactly (1931 cases, 1 environmental failure; 22/24 killed, TR09/TR21/TR24 survivors, TR26 disagreement).

---

## Findings

### I1 — `RegisterScreen` is not added to T174's "no OTHER screen takes focus" guard
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:422-454` mounts `SignInScreen`, `SignedOutScreen`, and `CallbackScreen` and asserts each leaves `document.activeElement` on `<body>`. `RegisterScreen` is absent from this loop, despite the brief listing this file under *Files to Modify* for T43.
· **Assessment:** T174 created this guard because it made every `<h1>` focusable, so any screen that later calls `.focus()` on mount would silently move a screen reader user's focus. RegisterScreen is a new screen with interactive controls; a future edit that autofocuses the email field or the heading would not be caught by the existing three-screen guard.
· **Recommendation:** Add `RegisterScreen` to the I9 guard loop. The screen currently passes because it does not move focus on mount, which is the desired behavior for all non-home screens.

### I2 — No case arrives at `/app/register` through the real route table
· **Severity:** **Minor/Major**
· **Evidence:** The named test mounts `RegisterScreen` directly under a probe route (`frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:96`). AC8 checks the declared path sets structurally but does not render the real `appRoutes`.
· **Assessment:** T41 and T42 both mount the real route table to prove the gate/layout works end-to-end. T43's route attachment is verified by walking `appRoutes` in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`, but no case actually arrives at `/app/register` through the real table. A regression that moved the screen to the wrong route branch would be caught by the structural pins, but the user-facing composition (screen + AppShell + no session requirement) is not exercised.
· **Recommendation:** Add one T43 case that mounts `appRoutes` via `createMemoryRouter` and arrives at `/app/register` without a session, asserting the form renders.

### I3 — The registration screen does not focus its heading on arrival
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:55-72` renders a `<Heading>` but never calls `.focus()` on it. `HomeScreen.tsx` does focus its heading on arrival (T174).
· **Assessment:** The brief does not explicitly require focus-on-arrival for T43, and L11's "managed focus" is satisfied by focus rings and the status-region focus pattern. However, consistency with T174 would suggest focusing the heading on arrival. The self-review TR21 flags "focus moved on mount" as unmeasured; the deeper question is whether the screen should move focus at all. This is a design decision the owner should make.
· **Recommendation:** Optional: decide whether public screens should focus their heading on arrival. If yes, add the focus effect and a test; if no, document that only HomeScreen moves focus on arrival.

### I4 — `createPublicAuthApi()` is instantiated on every submit
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:51` calls `createPublicAuthApi()` inside `submit()`.
· **Assessment:** `createPublicAuthApi` creates a new generated client each time. For a one-shot public mutation this is functionally harmless, but it is inconsistent with `createAuthApi` being created once per tab. It also means any middleware (e.g., rate-limit notice if passed) is re-registered per submit.
· **Recommendation:** Move the client creation to module scope: `const publicApi = createPublicAuthApi();` and use it in `submit()`. This is a small cleanup, not a correctness fix.

### I5 — `SubmitButton` lacks a busy/disabled state during submission
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/ui/SubmitButton.tsx:21-31` accepts only `children` and renders a plain submit button. `RegisterScreen.tsx:66` uses it with no `aria-disabled` or `aria-busy`.
· **Assessment:** This is closely related to self-review F6 (nothing prevents a second submit). It also means a screen reader user gets no indication that submission is in progress. `ActionButton` supports `aria-disabled` and `aria-busy` for exactly this reason. If the owner decides to guard double submission (F6), `SubmitButton` will need to accept those props.
· **Recommendation:** If F6 is accepted, extend `SubmitButton` to accept optional `aria-disabled` and `aria-busy` props, mirroring `ActionButton`. Otherwise, document that the submit button is intentionally passive.

### I6 — The `app/ui/index.ts` header and rule 2 are falsified (self-review F1)
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/ui/index.ts:7-10` says no screen renders a form field; rule 2's list omits `type="email"`, `type="password"`, `type="submit"`, the `htmlFor`/`id` pair, and `noValidate`.
· **Assessment:** Confirmed independently. The self-review correctly identifies this. It is the highest-priority Phase 9 fix because it is the file every future task reads before adding a primitive.
· **Recommendation:** Same as self-review F1: update the header and rule 2 in Phase 9.

### I7 — The submit promise has no rejection handler (self-review F2)
· **Severity:** **Major**
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:51-52` fires `createPublicAuthApi().POST(...).then(...)` without `.catch()` or try/catch.
· **Assessment:** Confirmed. A transport-level failure (offline, DNS error, blocked request) leaves the live region on "Creating your account…" forever and produces an unhandled promise rejection. The self-review notes the suite can see the symptom (TR26) but not isolate the handler gap.
· **Recommendation:** Same as self-review F2: Phase 9 should add a rejection handler. The decision is whether to leave the in-flight copy in place or add a failure state (task 45's ground).

### I8 — `SubmitButton` claims a pair pin that does not exist (self-review F3)
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/ui/SubmitButton.tsx:18-19` claims both files are pinned to the same token list. `REQUIRED_UTILITIES` has independent rows for `ActionButton` and `SubmitButton`.
· **Assessment:** Confirmed. The two rows happen to match today, but nothing asserts it. This is the same coincidence hazard as T42's Banner/Screen column.
· **Recommendation:** Same as self-review F3: add a pair assertion or remove the claim.

### I9 — AC10's storage spy has no control (self-review F4)
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:511-523` spies on `setItem` and asserts it was not called.
· **Assessment:** Confirmed. A spy that fails to install reports the same result as a journey that writes nothing. The `Object.keys` assertions add some evidence but miss write-then-remove.
· **Recommendation:** Same as self-review F4: after the journey, write one key through each storage and assert the spy saw it.

### I10 — The empty-string password clause is unmeasured (self-review F5)
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/passwordLength.ts:15-16` claims empty string returns 0, but no test asserts it.
· **Assessment:** Confirmed. The freeze explicitly required the empty string not to crash the count. The most ordinary accidental input is both fields empty.
· **Recommendation:** Same as self-review F5: add a case that submits with empty password and asserts the count is 0, `aria-invalid` is true, and the request still goes.

### I11 — The typing helper's optional setter is a latent test bug (self-review F7)
· **Severity:** **Minor**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:128` uses `setter?.call(input, value)`.
· **Assessment:** Confirmed. If `HTMLInputElement.prototype.value` ever lacked a setter, the helper would silently no-op and failures would surface far from the cause.
· **Recommendation:** Same as self-review F7: assert the descriptor exists before using it.

### I12 — The form primitive's jsdom claim is unverified (self-review F7)
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/Form.tsx:8-9` claims jsdom logs "Not implemented" for form submission.
· **Assessment:** Confirmed. The harness's `noValidate` removal row is the experiment that could confirm or refute this. The comment should be corrected if the harness output contradicts it.
· **Recommendation:** Verify against the harness output and update or remove the sentence.

### I13 — The design-system suite correctly models the two-element field exception
· **Severity:** **Positive**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:143-160` and `:408-412` pin the exception table and assert each field renders exactly `<input>` and `<label>`.
· **Assessment:** This is a strong response to the challenge of field primitives rendering two elements. The exception is narrow and justified, and the suite verifies the exact tags.
· **Recommendation:** None.

### I14 — The advisory range table is well structured for task 44 extension
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:228-237` defines `PASSWORDS` as a table with `inRange` column, and `:287-294` adds controls proving the column is load-bearing.
· **Assessment:** This is exactly the shape needed for task 44 to add the 128 boundary without restructuring.
· **Recommendation:** None.

### I15 — The 500 and 409 rows in AC11 are honest about the response-agnostic design
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:574-591` tests 202, 409, and 500 responses and asserts the same success state.
· **Assessment:** This is a strong enforcement of R2/L4. The 500 row in particular documents that the screen reports success it cannot know, with OQ-T43-9 carrying the open question.
· **Recommendation:** None.

### I16 — The password-leakage case was corrected to a stronger claim
· **Severity:** **Positive**
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:526-557` checks every other element's attributes and text for the password, after discovering React 19 mirrors the controlled value into the DOM.
· **Assessment:** This is good adversarial testing: the original claim was falsified by measurement, and the case was rewritten to the stronger, correct claim.
· **Recommendation:** None.

### I17 — The whole-suite count matches the self-review
· **Severity:** **Positive / verification**
· **Evidence:** My run: 1930 passed / 1931 total. Self-review: 1931 cases, 1 failing.
· **Assessment:** The numbers match. The single failure is the known environmental `oxlint` case.
· **Recommendation:** None.

---

## Summary

T43's implementation is solid and the named suite is strong. The self-review found the most important issues: the falsified `app/ui/index.ts` header/rule 2 (**I6/F1**), the missing rejection handler (**I7/F2**), the false pair-pin claim in `SubmitButton` (**I8/F3**), and the uncontrolled storage spy (**I9/F4**). Independent findings add **I1** (`RegisterScreen` missing from T174's focus guard), **I2** (no real-route-table arrival case), **I3** (heading not focused on arrival), **I4** (new client per submit), and **I5** (submit button has no busy state). The design-system instrumentation for the four new primitives is well done (**I13**), and the AC11 response-agnostic tests are honest (**I15**). The main action items are the Phase 9 documentation fixes and the small guard/composition additions.

**Phase 8 complete.** Seventeen findings returned; no code or contract changed.
