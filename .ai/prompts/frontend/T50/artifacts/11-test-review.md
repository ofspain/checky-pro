<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T50 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T50 — Resend is public |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | **30 / 30 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2116 / 2116 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T50/artifacts/07-mutation-harness.py` | **21/21 regressions killed**, 1 canary killed, 1 declared unfalsifiable (TR20), **0 broken**, **0 disagreements** |

## Phase 10 changes consumed

Phase 10 added three cases under a new `phase 10` describe block:

1. **Email in no URL** — asserts the email does not appear in the router's pathname/search/hash or in the real `window.location` search/hash/href. Also checks the request path.
2. **Email in no console** — derives every `console` method at runtime, asserts the derivation is non-empty, spies on all of them, and asserts no log contains the email local part. Ends with a positive control.
3. **Failure → retry → success** — drives a dropped connection, then a successful retry, and asserts the form keeps the email and the acknowledgement renders.

Phase 10 also added three harness rows (TR21, TR22, TR23) isolating these cases.

## Findings

### F1 — The email-in-URL gap from Phase 8 is now closed
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:417-455` asserts `router.state.location.search`, `hash`, and `pathname`, plus `window.location.search`, `hash`, and `href`, all contain no email or local part.
· **Assessment:** This is the right shape. The Phase 10 self-review notes the first version only read the memory router and would have missed a `window.history.replaceState` leak; the rewritten case checks both router state and the real browser URL. It also guards the request path, so the email is asserted to be only in the POST body.
· **Recommendation:** None.

### F2 — The email-in-console gap from Phase 8 is now closed
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:457-486` derives `console` methods at runtime, asserts the list contains the expected eight names, spies on every method, asserts no call contains the local part, and uses a positive control with `console.info('control', EMAIL)`.
· **Assessment:** Confirms the non-vacuity trap is avoided. A reflection returning nothing would fail the derivation assertion before the negative assertions become meaningless.
· **Recommendation:** None.

### F3 — The failure→retry→success journey is now driven end to end
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:488-515` fails once with a rejecting transport, then clicks the same button with a succeeding transport and asserts the email is still in the field, one request is sent, and the acknowledgement renders without the `unsent` line.
· **Assessment:** This converts D-T50-1 from a DOM claim into an outcome claim. It also verifies the email survives in the field, which is the other half of keeping the form mounted.
· **Recommendation:** None.

### F4 — The URL case found a real harness disagreement and was rewritten, not the row
· **Severity:** **Positive / Observation**
· **Evidence:** Phase 10 reports TR21 disagreed on run 1 because the first case only read the memory router. The case was rewritten to check `window.location`, and TR21 then died as expected.
· **Assessment:** This is the right response. The mutant was valid; the instrument was incomplete. Fixing the case preserved the row's intent.
· **Recommendation:** None.

### F5 — The suite count and baseline are clean
· **Severity:** **Positive**
· **Evidence:** Named suite: 30 cases. Whole suite: 2116/2116. `tsc` and `oxlint` clean.
· **Assessment:** Phase 10 added three cases without regressions.
· **Recommendation:** None.

### F6 — No new process deviations introduced
· **Severity:** **Positive**
· **Evidence:** No new files were created; only the existing named suite was extended. The seven measured pins from T50 implementation remain unchanged.
· **Assessment:** Phase 10 is test-only, so it cannot introduce new D-T50-* deviations.
· **Recommendation:** None.

### F7 — The URL case's local-part check is a good privacy refinement
· **Severity:** **Positive**
· **Evidence:** The case asserts neither `EMAIL` nor `'visitor'` (the local part) reaches the URL.
· **Assessment:** This catches an encoder that might split the address and still leak identifying information.
· **Recommendation:** None.

### F8 — The console case uses `Object.fromEntries` to build the spy map
· **Severity:** **Minor**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:470-471` maps method names to spies via `Object.fromEntries`.
· **Assessment:** This is clean and type-safe enough. The case is well-structured.
· **Recommendation:** None.

### F9 — Harness is 21/21 with TR20 the only survivor
· **Severity:** **Positive**
· **Evidence:** The harness run produced `regressions: 21/21 killed`, `canaries: ['TR19'] - none survived`, survivors `['TR20']`, 0 broken, 0 disagreements.
· **Assessment:** Every measured gap from Phase 8 is closed and the declared unfalsifiable row remains so.
· **Recommendation:** None.

---

## Summary

Phase 10 closed all three gaps identified in Phase 8: email must not reach URL, email must not reach console, and a failed send must be retryable to success. The URL case is particularly well-hardened after a harness disagreement revealed the first version measured only the memory router. The console case correctly guards against a vacuous reflection, and the retry case drives the full journey end to end.

The suite is now 30 cases, the whole suite is 2116/2116, and type/lint checks are clean. No new process deviations were introduced.

Final verification is clean: **30/30** in the named suite, **2116/2116** in the whole suite, and **21/21 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 11 complete.** No code or contract changed.
