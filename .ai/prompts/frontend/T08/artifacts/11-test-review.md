# frontend · T08 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T08 — No POST replay |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

---

## Method

Reviewed the Phase 10 test manifest for the T08 portion of `rateLimitedResponseShowsGenericCopyWithoutReplay` (18 T08 cases, 5 T08-specific mutation breaks, 19 total caught) against the frozen T08 acceptance criteria (AC1–AC9) and the Phase 8 findings. No code was rewritten.

The manifest already resolves the Phase 8 findings: exact body assertions were added to the table-driven cases, `failures` is asserted in those cases, and the test file now has 47 cases.

---

## Gaps

### 1. No copy-invariance test for a POST 429
**Gap ·** T07's AC2 table covers `Retry-After`, problem titles, empty and non-JSON bodies for GETs. T08 does not repeat that table for a POST.  
**Why it matters ·** The notice middleware is operation-agnostic, but the generated client's serialization or the transport's body handling could differ for POSTs. A regression that accidentally read a 429 POST body for copy would be missed.  
**Suggested test ·** Add one T08 row: a POST `/api-keys` returning a 429 with a non-JSON body and a `Retry-After: 3600` header, and assert the rendered notice still equals `catalogue.rateLimit.message + catalogue.rateLimit.dismiss`.

### 2. No POST-only burst test
**Gap ·** T07 has a mixed GET+POST burst that proves one notification across operation types. T08 has no burst consisting solely of POSTs.  
**Why it matters ·** A notification-counting bug that only manifests when all requests are non-idempotent POSTs (and therefore all hit the same code path in the boundary) would not be caught.  
**Suggested test ·** Fire three different POST 429s concurrently and assert one notification, three transport calls, and the notice shown.

### 3. No explicit test that the no-notice path preserves POST bodies
**Gap ·** Case 10 loops over all driven POSTs with `createApi()` (no notice) and asserts one call and status 429, but it does not assert the body.  
**Why it matters ·** The no-notice path still goes through the boundary and openapi-fetch. A body-dropping bug there would be invisible to this case. The manifest notes that body-dropping on public POSTs is not expressible as a mutant of SPA code, so the assertion is the only guard.  
**Suggested test ·** Extend case 10 to assert `sent[0]!.body` matches the expected JSON for each POST in the loop.

### 4. The uncloneable-body case does not assert the original body reached the transport
**Gap ·** Case 13 records `'<uncloneable>'` because `Request.prototype.clone()` throws. It asserts one call and the 429 response, but it does not prove the actual fetch sent the original body.  
**Why it matters ·** If a future change consumed the original body before the fetch, the call count would still be one but the server would receive an empty body.  
**Suggested test ·** Make the transport spy inspect the *original* request's body (not the clone) and assert it contains the expected JSON, or assert that `fetch` was called with a request whose body was not empty. Alternatively, make the mocked server return 429 only if the body matches, so the case would fail if the body were dropped.

### 5. No test for a 429 on a POST after a failed renewal
**Gap ·** T08 covers the 401 → renewal → re-send → 429 boundary, but not the 401 → renewal-failure → no re-send path for a POST.  
**Why it matters ·** R23 says a 429 is never replayed; the related guarantee is that a POST that fails renewal is also not replayed. A boundary bug that retried on renewal failure could exist.  
**Suggested test ·** Optional: a POST returns 401, `renew()` rejects, and the final 401 is returned with exactly one transport call.

### 6. No test for the drift guard when the contract file is missing or unreadable
**Gap ·** Case 16 reads `contracts/api/auth.yaml` and asserts the parsed POSTs match the lists. It does not handle or assert behavior when the file is absent.  
**Why it matters ·** A sandboxed test runner or a packaging mistake that omits the contract would produce a noisy `ENOENT` failure rather than a clear assertion message.  
**Suggested test ·** Not required, but the guard could be wrapped to fail with a custom message like "auth.yaml not found; contract drift guard cannot run".

### 7. No test for a POST 429 with a request-abort signal
**Gap ·** None of the T08 cases pass an `AbortSignal` to the POST call.  
**Why it matters ·** TanStack Query (the confirmed stack) uses abort signals. A boundary bug that conflated abort with 429 could replay or leak.  
**Suggested test ·** Optional: start a POST call, abort it before the response, assert it rejects with `AbortError` and `sent` has one entry.

### 8. No test that the T07 notice is not affected by a POST 429 differently than a GET 429
**Gap ·** T08 cases render the notice and assert the copy is shown, but they do not explicitly compare the notice state after a POST 429 to a GET 429.  
**Why it matters ·** The notice is operation-agnostic; this is more of a regression sanity check than a functional gap.  
**Suggested test ·** Optional one-liner: after a POST 429, `notice.isShown()` is true and `view.text()` equals the message. Already implicit in cases 1–9.

---

## Non-gaps (acknowledged, no change needed)

- **All T08 ACs are covered** by at least one case.
- **19 of 19 mutation breaks caught**, including T07's set re-run and T08's five breaks.
- **Exact body assertions** are now in the table-driven cases (Phase 9 fix for P8-1).
- **`failures` is empty** is asserted in the table-driven cases (Phase 9 fix for P8-7).
- **401 → renewal → 429 boundary** is pinned by case 12 with a code comment.
- **Uncloneable body** is covered by case 13.
- **Structural AC5** is covered by case 14.
- **No timer / no resumption** is covered by case 15.
- **Contract drift guard** is covered by case 16.
- **POST-body privacy** is covered by case 17.
- **T05 parity** is covered by case 18.
- **No production, spec, contract, or earlier-task file changed.**

---

## Summary

The T08 test manifest is excellent: 18 cases, every AC covered, and 19/19 mutation breaks caught. The remaining gaps are minor: one copy-invariance row for POSTs, a POST-only burst, a body assertion in the no-notice loop, and an uncloneable-body test that also proves the original body was sent. Adding these would make the suite a complete regression guard for POST 429 handling.
