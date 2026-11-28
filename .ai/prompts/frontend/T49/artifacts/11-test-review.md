<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T49 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T49 — Verify failure is uniform |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | **38 / 38 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2086 / 2086 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T49/artifacts/07-mutation-harness.py` | **20/20 regressions killed**, 1 canary killed, 2 declared unfalsifiable (TR17, TR20), **0 broken**, **0 disagreements** |

## Phase 10 changes consumed

Phase 10 added three cases under a new `phase 10` describe block:

1. **HANDOFF TO TASK 50** — asserts that `/app/resend-verification` resolves but renders empty HTML today. Written as a positive claim that inverts when task 50 attaches the element.
2. **Token not in resend link's href** — asserts the href contains neither the token nor any query string, documenting why a clickable token is worse than one in the arrival URL.
3. **PINS A LIMIT — in-flight token replaced by tokenless URL** — documents and pins a corner case where the one-request guard leaves the screen showing only the heading.

Phase 10 also added three harness rows (TR21, TR22, TR23) isolating these cases.

## Findings

### F1 — The task-50 handoff gap from Phase 8 is now closed
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:505-543` navigates to `RESEND_VERIFICATION_PATH` through the real route table, asserts the path resolves, and asserts `container.innerHTML === ''`.
· **Assessment:** This is exactly the positive, inverting claim Phase 7/8 asked for. When task 50 attaches an element, this case will fail with a message that points maintainers back to `VerifyScreen`'s link. The self-review correctly notes that two route-count pins would also fire, but this case supplies the instruction while the pins only supply the count.
· **Recommendation:** None.

### F2 — The token-in-href case adds reasoning but closes no new behavioural hole
· **Severity:** **Positive / Observation**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:545-559` asserts `href` does not contain `TOKEN` or `?`. The existing case at lines 242-254 already asserts `href` equals `${APP_BASENAME}/${RESEND_VERIFICATION_PATH}` exactly.
· **Assessment:** The Phase 10 self-review correctly observes that the exact equality already forbids a query string. The new case is valuable for its comment and its narrower failure message, not for closing an unmeasured hole. Keeping it is right.
· **Recommendation:** None.

### F3 — The in-flight→tokenless limit is documented and pinned, not fixed
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:561-586` arrives with a token, navigates to a tokenless URL while the request is in flight, and asserts the screen stays at the heading only.
· **Assessment:** The comment explains why the fix was rejected: moving the tokenless branch above the guard would make Back navigation after success display the failure message. The case pins that cost and will fail if anyone later decides to close the corner.
· **Recommendation:** None.

### F4 — The new cases are isolated by their own harness rows
· **Severity:** **Positive**
· **Evidence:** Phase 10 reports `TR21`, `TR22`, and `TR23` as new rows. The canary `TR19` still dies, confirming the instruments are not inert.
· **Assessment:** Each new concern has a dedicated mutant. The harness now covers 20 regressions.
· **Recommendation:** None.

### F5 — The suite count and baseline are clean
· **Severity:** **Positive**
· **Evidence:** Named suite: 38 cases. Whole suite: 2086/2086. `tsc` and `oxlint` clean.
· **Assessment:** Phase 10 added three cases without regressions.
· **Recommendation:** None.

### F6 — D-T49-3 and D-T49-4 remain for Phase 9 ratification
· **Severity:** **High/Medium (process)**
· **Evidence:** Same as Phase 8 I6. The design-system pin key-set was updated and the catalogue keys were kept flat.
· **Assessment:** Test generation does not ratify process deviations; these remain open.
· **Recommendation:** Phase 12 or the owner should resolve these.

### F7 — No case asserts the task-50 handoff case will actually fail for the right reason
· **Severity:** **Minor**
· **Evidence:** The handoff case asserts `container.innerHTML === ''`. When task 50 lands, any non-empty render will fail it.
· **Assessment:** This is sufficient. The case's failure message explicitly says "task 50 has landed, so re-check this screen's link". A more specific assertion (e.g., contains a form) would require knowing task 50's structure, which is out of scope.
· **Recommendation:** None.

### F8 — Harness is 20/20 with TR17 and TR20 the only survivors
· **Severity:** **Positive**
· **Evidence:** The harness run produced `regressions: 20/20 killed`, `canaries: ['TR19'] - none survived`, survivors `['TR17', 'TR20']`, 0 broken, 0 disagreements.
· **Assessment:** Every measured gap is closed and every declared unfalsifiable row remains so.
· **Recommendation:** None.

---

## Summary

Phase 10 closed the one real gap identified in Phase 8 (the missing task-50 handoff instrument) and added two more cases that document security reasoning and a deliberate corner-case limit. The handoff case is written as a positive, inverting claim — the right shape based on Phase 5's lesson. The token-in-href case adds valuable commentary even though the exact-equality case already provided protection.

The suite is now 38 cases, the whole suite is 2086/2086, and type/lint checks are clean. The two process deviations from earlier phases (D-T49-3, D-T49-4) remain for the owner.

Final verification is clean: **38/38** in the named suite, **2086/2086** in the whole suite, and **20/20 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 11 complete.** No code or contract changed.
