<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T48 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | **32 / 32 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2048 / 2048 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T48/artifacts/07-mutation-harness.py` | **21/21 regressions killed**, 2 canaries killed, 1 unfalsifiable (TR20), **0 broken**, **0 disagreements** |

## Phase 10 changes consumed

Phase 10 added five cases and four harness rows:

1. `never renders the token, in ANY state` — closes the "not rendered" gap from the brief's Constraints section.
2. `logs nothing at all` — behavioural counterpart to the source scan that forbids `console.`.
3. `sends a POST with a JSON content type` — closes the verb/content-type gap.
4. `verifies from a link with a trailing slash` — end-to-end claim that `/app/verify/` resolves to the screen.
5. `takes the FIRST token when a link carries two` — pins `URLSearchParams.get` vs `getAll`.
6. Harness rows **TR21–TR24** isolate each new case.

Phase 10 also closed the two Phase 7/8 survivors:
- Added `a token arriving AFTER a tokenless visit is still sent` to kill **TR05**.
- Added an assertion that a 200 response shows the success text to kill **TR10**.

## Findings

### F1 — TR05 is now killed by a behavioural case
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:200-221` arrives with no token, asserts no request is sent, then navigates to the same route with a token and asserts one request is sent.
· **Assessment:** This measures the guard's semantics — "a request has been made" — rather than just its source order. The brittle source-order scan at lines 223-241 is kept as a complementary instrument.
· **Recommendation:** None.

### F2 — TR10 is now killed by the 200 mock case
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:306-326` mocks a 200 with a body, asserts the body is not rendered, **and** asserts `en.verify.success` is rendered.
· **Assessment:** This holds the deliberate `response.ok` choice against a narrower `=== 204`. It also documents why: any 2xx means the account is verified.
· **Recommendation:** None.

### F3 — The new token-visibility case covers idle and success, but not failure
· **Severity:** **Minor**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:453-475` checks `innerHTML` does not contain `TOKEN` while in-flight and after success.
· **Assessment:** Task 49 owns failure states. A failure response might render the token accidentally (e.g., in an error message), but that will be task 49's concern. The case is correctly scoped to task 48's states.
· **Recommendation:** None for this task. Task 49 should add a failure-state token-visibility assertion if it renders any new DOM.

### F4 — The console spy covers common methods but not every console API
· **Severity:** **Minor**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:482-488` spies on `log`, `warn`, `error`, `debug`, and `info`.
· **Assessment:** This covers the methods a token is likely to be logged with. `console.table`, `console.dir`, `console.trace`, `console.group`, and `console.time` are not spied. A future utility that logs through one of those would not be caught.
· **Recommendation:** Optional: expand the spy list to include `trace`, `dir`, `table`, and `group` for completeness, or keep the current list as the practical minimum.

### F5 — The content-type assertion is precise
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:505-507` asserts `method === 'POST'` and `contentType` matches `^application/json`.
· **Assessment:** This closes a real gap: a correct body on the wrong verb or with an unparseable content type would fail in production but passed the earlier suite.
· **Recommendation:** None.

### F6 — The trailing-slash case makes a synthetic probe end-to-end
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:510-521` drives `/app/verify/?token=...` through the real route table and asserts the POST is sent and success is rendered.
· **Assessment:** This converts an inherited synthetic-route-table probe into a real screen assertion.
· **Recommendation:** None.

### F7 — The repeated-token case pins a one-method difference
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:523-534` sends `?token=A&token=B` and asserts the first token is posted.
· **Assessment:** This is a strong adversarial case for a subtle API choice. The comment explains why first-wins is the safer branch.
· **Recommendation:** None.

### F8 — The behavioural log assertion is a good complement to the source scan
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:477-495` spies on `console` methods and asserts no output, regardless of how the call is spelled.
· **Assessment:** The source scan forbids the literal `console.`; this case catches aliases, re-exports, and future loggers. Together they cover more than either alone.
· **Recommendation:** None.

### F9 — D-T48-1 and D-T48-2 remain unaddressed
· **Severity:** **High/Medium (process)**
· **Evidence:** Phase 10 changed only test files. The T43-suite modification (D-T48-1) and the token-alphabet scope change (D-T48-2) from Phase 7/8 are still open for Phase 9/owner ratification.
· **Assessment:** Expected — test generation is not the phase that ratifies process deviations.
· **Recommendation:** Phase 12 or the owner should resolve these.

### F10 — Harness is 21/21 with TR20 the only survivor
· **Severity:** **Positive**
· **Evidence:** The harness run produced `regressions: 21/21 killed`, `canaries: ['TR18', 'TR19'] - none survived`, survivors `['TR20']`, 0 broken, 0 disagreements.
· **Assessment:** Phase 10 has closed every gap that had a mutant and added isolated rows for every new case. TR20 remains unfalsifiable by design.
· **Recommendation:** None.

---

## Summary

Phase 10 closed the two real gaps from Phase 7/8 (TR05 and TR10) and added five strong cases that pin constraints the brief stated but no assertion held. The new cases are each isolated by their own harness rows (TR21–TR24), and the two canaries died, proving the instruments are not inert.

The remaining Phase 11 concerns are small: the console spy could be widened (F4), and the token-visibility case does not cover task 49's failure states (F3, correctly out of scope). The process findings from earlier phases (F9) remain for the owner.

Final verification is clean: **32/32** in the named suite, **2048/2048** in the whole suite, and **21/21 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 11 complete.** No code or contract changed.
