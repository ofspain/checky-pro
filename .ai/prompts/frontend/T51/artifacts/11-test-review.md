<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T51 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T51 — Resend acknowledgement is uniform |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | **51 / 51 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2137 / 2137 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T51/artifacts/07-mutation-harness.py` | **6/6 regressions killed**, 1 canary killed, **0 survivors**, **0 broken**, **0 disagreements** |

## Phase 10 changes consumed

Phase 10 added four changes to the T51 half of the shared suite:

1. **Two redirect arms** — `302` with body/Location and `304` with no body — added to the `ARMS` list.
2. **Plain-text arm given distinctive words** — changed from `'OK'` to `'no account matched: nothing queued'` so it can hold AC10.
3. **Wordless-arm count pin** — a case that names the five genuinely wordless arms and asserts the other ten hold AC10.
4. **Retry request uniformity case** — fails once, retries, and asserts the second request's body, path, method, and absence of `Authorization` match a first attempt.

Phase 10 also added two harness rows (TR29, TR30) and declined two of Kimi's Phase 8 gaps (I10 mid-flight region, cross-arm request identity) as impossible to fail by construction.

## Findings

### F1 — The redirect-arm gap from Phase 8 is closed
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:823-826` adds `302` and `304` arms. The case at `:861-891` compares each arm's rendering to the declared reply.
· **Assessment:** The Phase 10 self-review correctly observed that the transport is stubbed, so `fetch` does not follow redirects; the screen simply sees a `3xx` resolve. Both arms collapse to the same acknowledgement. This was a real gap and is now held.
· **Recommendation:** None.

### F2 — The server-words count is now pinned
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:894-916` lists the five wordless arms (`200 no body`, `200 empty string`, `204`, `503`, `304`) and asserts `ARMS.length - wordless.length === 10`. The plain-text arm at `:802-803` now carries `serverWords: 'no account matched'`.
· **Assessment:** This removes the implied-coverage problem. Adding a wordless arm without justification now fails a named case, and TR30 isolates it.
· **Recommendation:** None.

### F3 — The retry request uniformity case is well-designed
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:971-992` fails the first send with `installTransportFailure`, asserts the `unsent` line and one call, then retries with `installTransport`, and asserts the retry body/path/method/authorization.
· **Assessment:** The case proves the retry is a real send (not a re-render) and that the request shape is identical. It addresses the half of I11 that can actually vary by outcome.
· **Recommendation:** None.

### F4 — I10 and the cross-arm half of I11 are correctly declined
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:953-967` documents why these cases cannot fail: the request and the in-flight render happen before the reply exists, so no mutant can make them vary by outcome.
· **Assessment:** The reasoning is sound. T50 already owns the in-flight region claim for one arm. Adding cases that cannot fail would be vacuous.
· **Recommendation:** None.

### F5 — No production changes
· **Severity:** **Positive**
· **Evidence:** The Phase 10 manifest states no production file changed; `ResendVerificationScreen.tsx` still contains only the `parseAs: 'stream'` fix from T51 implementation.
· **Assessment:** Phase 10 is test-only, as expected.
· **Recommendation:** None.

### F6 — The suite count and baseline are clean
· **Severity:** **Positive**
· **Evidence:** Named suite: 51 cases. Whole suite: 2137/2137. `tsc` and `oxlint` clean.
· **Assessment:** Phase 10 added four cases (two new arms × one markup comparison, plus wordless-count case, plus retry case) without regressions.
· **Recommendation:** None.

### F7 — The wordless-arm case is a structural pin, not a behavioural one
· **Severity:** **Observation**
· **Evidence:** The case at `:894-916` does not render anything; it inspects the `ARMS` array directly.
· **Assessment:** This is appropriate for a coverage pin whose property is of the arm list itself. TR30 is the behavioural complement.
· **Recommendation:** None.

### F8 — OQ-T51-6 remains open and is the highest-value follow-up
· **Severity:** **Observation**
· **Evidence:** The Phase 10 manifest records OQ-T51-6 as a measured bug on `VerifyScreen` (and potentially `RegisterScreen`), not fixed here.
· **Assessment:** Out of T51 scope, but real. The owner should schedule it.
· **Recommendation:** None for T51.

### F9 — Harness is 6/6 with no survivors
· **Severity:** **Positive**
· **Evidence:** The harness run produced `regressions: 6/6 killed`, `canaries: ['TR28'] - none survived`, no survivors, 0 broken, 0 disagreements.
· **Assessment:** Every measured gap is closed.
· **Recommendation:** None.

---

## Summary

Phase 10 closed the remaining Phase 8 gaps that could be tested: redirect arms, pinned server-words count, and retry request uniformity. The two gaps declined (mid-flight region, cross-arm request identity) are correctly identified as impossible to fail by construction. The suite is now 51 cases, the whole suite is 2137/2137, and type/lint checks are clean.

The only notable open item is OQ-T51-6, a measured bug on other public screens that is out of T51 scope.

Final verification is clean: **51/51** in the named suite, **2137/2137** in the whole suite, and **6/6 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 11 complete.** No code or contract changed.
