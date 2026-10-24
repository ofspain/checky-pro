# frontend · T07 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T07 — 429 generic copy |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **429 generic copy.** An SPA-originated 429 shows fixed copy with no countdown and no interval. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

---

## Method

Reviewed the Phase 10 test manifest for `rateLimitedResponseShowsGenericCopyWithoutReplay` (26 cases, 12 mutation breaks) against the frozen T07 acceptance criteria (AC1–AC8) and the Phase 8 findings. No code was rewritten.

The manifest already covers the Phase 8 focus-loss finding (cases 12 and 14, mutation M12) and the Phase 8 optional-notice wiring concern (case 24). The one whole-suite failure noted in the manifest is the pre-existing environmental `lintRejectsHandWrittenBackendFetch` case and is unrelated to T07.

---

## Gaps

### 1. No mixed-endpoint burst test
**Gap ·** Case 13 fires a burst of three 429s, but they are all the same operation. There is no case where a protected call and a public call (e.g., `/accounts/me` and `POST /accounts/password-reset`) return 429 in the same tick.  
**Why it matters ·** The one-notification-per-burst guarantee should hold across operation types, and the public operation has no bearer while the protected one does. A bug that keyed the notice state on the request would show multiple notices.  
**Suggested test ·** Add a case: fire a protected 429 and a public 429 concurrently and assert `notice.isShown()` is true and the subscriber is called exactly once. Both responses reach their callers with status 429.

### 2. No programmatic `dismiss()` test
**Gap ·** The `RateLimitNoticeState` API exposes `dismiss()` as a method, but every clearing test goes through the rendered button. There is no direct call to `notice.dismiss()`.  
**Why it matters ·** Future wiring or screen code may call `notice.dismiss()` directly (e.g., on route change). A regression that broke the method while leaving the button working would be missed.  
**Suggested test ·** Show the notice, then call `notice.dismiss()` directly, and assert `notice.isShown()` is false and subscribers were notified exactly once.

### 3. No copy-invariance test on the re-send-after-renewal 429
**Gap ·** Case 3 verifies the notice appears for a 429 on the re-send after a renewal, but it does not exercise the copy-invariance table (different `Retry-After`, problem titles, empty/non-JSON body) on that path.  
**Why it matters ·** The re-send path is the one that goes through T05's middleware bypass; the notice must still ignore body and headers on that response.  
**Suggested test ·** Add one or two `it.each` rows to case 3 with a non-JSON 429 body and a `Retry-After` header, asserting the rendered text still equals `catalogue.rateLimit.message + catalogue.rateLimit.dismiss`.

### 4. No assertion that a throwing subscriber does not prevent other subscribers from firing
**Gap ·** Case 26 verifies that a throwing subscriber doesn't break the response chain or the component, but it does not assert that a second, healthy subscriber still receives its notification.  
**Why it matters ·** The implementation iterates all listeners and catches errors, but a subtle bug that aborted the loop on the first throw would silence later subscribers.  
**Suggested test ·** Extend case 26 with a second subscriber that increments a counter, and assert it is called exactly once for the show notification despite the first subscriber throwing.

### 5. No explicit transport-call-count assertion in the burst case
**Gap ·** Case 13 asserts one notification for a burst of three 429s, but it does not assert that the transport was called three times.  
**Why it matters ·** AC6 requires "the transport is called once per call (no replay added)." A mutant that replayed one of the bursted calls while still showing one notice would pass case 13 but violate AC6.  
**Suggested test ·** In case 13, assert the transport received three requests and returned three 429 responses.

### 6. No re-show test after a successful response
**Gap ·** Case 13 covers staying shown across later 200s and re-showing after dismissal, but it does not cover the sequence: 429 → dismiss → 200 → 429.  
**Why it matters ·** A regression where a successful response accidentally cleared internal state would be caught only if the second 429 still re-shows the notice.  
**Suggested test ·** Add a case: show, dismiss, return 200, then return 429 again; assert the notice re-appears and subscribers are notified once for the second episode.

### 7. No privacy assertion for the throwing-subscriber case
**Gap ·** Case 25 checks storage/cookies/logs during a normal 429. Case 26 throws an error from a subscriber, but it does not assert that the thrown error (or the notice state) does not leak into logs or storage.  
**Why it matters ·** The error path is where defensive logging might accidentally emit the subscriber or state.  
**Suggested test ·** Extend case 26 to assert no `console.error`/`console.warn` and no storage/cookie writes occurred.

### 8. No test that the notice stays hidden on a 429 when no `rateLimit` is passed
**Gap ·** Case 24 verifies `createAuthApi(boundary)` behaves as in T05, but its primary assertion is about renewal and pass-through. It does not explicitly assert the notice stays hidden when a 429 occurs without a notice.  
**Why it matters ·** AC7 says `createAuthApi(boundary)` without a notice behaves exactly as in T05. Explicitly checking `notice.isShown()` is not applicable because there is no notice, but checking that no notice markup appears confirms the optional param path does not auto-create one.  
**Suggested test ·** In case 24, also assert that no rate-limit live region or dismiss button is rendered.

---

## Non-gaps (acknowledged, no change needed)

- **All 12 frozen cases are represented**, expanded into 26 `it` blocks where needed.
- **12 of 12 mutation breaks caught**, including the Phase 9 focus-fix break.
- **Middleware order** is guarded by case 3 and mutation M1.
- **Copy invariance** is guarded by cases 4–10 and mutations M2/M3.
- **No timer / no auto-dismiss** is guarded by case 11, case 13, and mutation M4.
- **Live region and catalogue** are guarded by cases 16–17 and mutations M5/M10/M11.
- **Pass-through and no replay** are guarded by case 18 and mutation M7.
- **Non-429 statuses** are guarded by cases 19–23 and mutations M8/M9.
- **Privacy (storage/cookies/logs)** is guarded by case 25.
- **Throwing subscriber isolation** is guarded by case 26.
- **Focus management** is guarded by cases 12, 14, 15 and mutation M12.

---

## Summary

The T07 test suite is comprehensive: 26 cases, 12 mutation breaks, and coverage of every acceptance criterion plus the Phase 9 focus fix. The main remaining gaps are around mixed-endpoint bursts, programmatic `dismiss()`, copy invariance on the renewal re-send path, subscriber isolation, and transport-call counts. Adding these would harden the suite against subtle regressions in the boundary/component contract.
