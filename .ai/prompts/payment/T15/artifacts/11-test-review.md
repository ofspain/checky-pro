# payment · T15 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T15/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `CryptoClientTest.java`, `ArchitectureTest.java`.

---

## Executive summary

- All 10 new `CryptoClientTest` methods are present and pass; `CryptoClientTest` is now **25/25**.
- `ArchitectureTest` remains **7/7** (L3 canary + negative proof unchanged from Phase 6).
- Full payment suite is **309/309 green**.
- The test set covers every realistic response shape from crypto-service (`SIGNED`, `BLOCKED`, `409`, `400`, `5xx`, connection failure, timeout, malformed body) and includes a mutation-verified guard for hollow `SIGNED` responses.
- Remaining gaps are minor edge-case strengthenings (lowercase outcome, partial missing fields, `BLOCKED` without a reason, request method/path assertions).

---

## Test manifest verification

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldSendCorrectBodyAndBearerTokenOnAttest` | ✅ | AC1/AC5 — correct JSON body + bearer token |
| `shouldReturnSignedOutcomeWhenAttestResponseIsSigned` | ✅ | AC1 — `200 SIGNED` → `AttestationResult.Signed` |
| `shouldReturnBlockedOutcomeWhenAttestResponseIsBlocked` | ✅ | AC2 — `200 BLOCKED` → `AttestationResult.Blocked` |
| `shouldThrowCryptoServiceUnavailableWhenSignedOutcomeHasMissingFields` | ✅ | Phase 9 fix — hollow `SIGNED` fails loudly |
| `shouldThrowAttestationRefusedExceptionOnConflict` | ✅ | AC3 — `409` → `AttestationRefusedException` |
| `shouldThrowCryptoServiceUnavailableOnAttestBadRequest` | ✅ | AC4 — `400` → `CryptoServiceUnavailableException` |
| `shouldThrowCryptoServiceUnavailableOnAttestServerError` | ✅ | AC4 — `5xx` → `CryptoServiceUnavailableException` |
| `shouldThrowCryptoServiceUnavailableOnAttestConnectionFailure` | ✅ | AC4 — connection failure → `CryptoServiceUnavailableException` |
| `shouldThrowWhenAttestServerExceedsConfiguredReadTimeout` | ✅ | AC6 — bounded read timeout |
| `shouldThrowCryptoServiceUnavailableOnAttestMalformedResponseBody` | ✅ | AC4 — missing `outcome` → exception |

ArchUnit L3 rule canary and negative-proof tests are unchanged from Phase 6 and still pass.

---

## Gap 1 (Low) — No test for lowercase `outcome` values

- **Why it matters:** `CryptoClient` uses `equalsIgnoreCase` for both `SIGNED` and `BLOCKED`, mirroring `registerWatch`'s own case-insensitive status check. The tests only send uppercase outcomes, so a regression that accidentally switched to `equals` would not be caught.
- **Suggested test:** Add a test sending `{"outcome":"signed","signature":"...",...}` and assert it still parses into `AttestationResult.Signed`. Same for `"blocked"`.
- **Confidence:** Low.

---

## Gap 2 (Low) — No test for a `BLOCKED` response without a `reason`

- **Why it matters:** The contract says `reason` is present only when `outcome` is `BLOCKED`, but it does not mark `reason` as `required`. A crypto-service response `{"outcome":"BLOCKED"}` is legal and would currently produce `AttestationResult.Blocked(null)`. Whether that is acceptable is an undocumented edge case.
- **Suggested test:** Add `shouldReturnBlockedOutcomeWithNullReasonWhenReasonIsAbsent` and assert either (a) the result carries a null reason by design, or (b) the client rejects it. Either choice should be explicit.
- **Confidence:** Low.

---

## Gap 3 (Low) — Missing-field test only covers all-fields-missing

- **Why it matters:** The current test sends `{"outcome":"SIGNED"}` with no signature, no `kmsKeyId`, and no `signedAt`. The guard checks all three, so this exercises the branch, but it does not prove the guard is checking *each* field rather than just one.
- **Suggested test:** Add three focused cases (or a parameterized test) where exactly one of `signature`, `kmsKeyId`, or `signedAt` is missing. This makes the mutation surface narrower and the guard intent clearer.
- **Confidence:** Low.

---

## Gap 4 (Low) — No explicit assertion that `attest` uses `POST` and the correct path

- **Why it matters:** `shouldSendCorrectBodyAndBearerTokenOnAttest` captures the body and auth header, but does not assert the HTTP method or request path. A future regression that changed the method to `PUT` or the path to `/internal/v1/attest/` (trailing slash) would fail at runtime but not in this test.
- **Suggested test:** Extend `shouldSendCorrectBodyAndBearerTokenOnAttest` to also capture `exchange.getRequestMethod()` and `exchange.getRequestURI().getPath()` and assert `POST` and `/internal/v1/attest`.
- **Confidence:** Low.

---

## Gap 5 (Low) — `409` test uses plain text, not real problem+json

- **Why it matters:** The real crypto-service returns an RFC 9457 `ProblemDetail` JSON body for `409`. The test sends a plain-text body and asserts the message contains it. This verifies the exception carries the body, but it does not document what the real body shape looks like or guard against JSON parsing surprises.
- **Suggested test:** Add a second `409` test that sends a realistic problem+json body (e.g., `{"title":"Attestation refused","detail":"preconditions not met"}`) and assert the exception message contains the detail text.
- **Confidence:** Low.

---

## Gap 6 (Low) — No test for `outcome: null` in a `200` response

- **Why it matters:** The client explicitly checks `response.outcome() == null` and throws `CryptoServiceUnavailableException`. The malformed-body test uses a response with no `outcome` field at all (Jackson deserializes `outcome` to `null`). A separate test with `"outcome":null` would directly exercise the null-check path and guard against a subtle Jackson difference.
- **Suggested test:** Add `shouldThrowCryptoServiceUnavailableWhenAttestOutcomeIsNull`.
- **Confidence:** Low.

---

## Positive confirmations

- `startAttestServer(...)` was added alongside the existing watch helper, exactly as the manifest planned, avoiding risk to already-reviewed watch tests.
- Every required AC1-AC6 behavior has at least one named test.
- The hollow-`SIGNED` test is genuinely mutation-verified: removing the null-field guard would make it fail.
- `shouldThrowWhenAttestServerExceedsConfiguredReadTimeout` asserts elapsed time `< 500ms`, proving the configured timeout is actually enforced.
- `shouldThrowCryptoServiceUnavailableOnAttestMalformedResponseBody` uses a `200` with an unrecognizable body, not just an invalid status, covering the malformed-response case.
- The `ArchitectureTest` L3 canary and rogue-fixture negative proof remain in place and pass.
- Full suite passes: **309/309**.

---

## Build context

- `CryptoClientTest`: **25/25 passing** (15 existing + 10 new).
- `ArchitectureTest`: **7/7 passing**.
- Full `mvn -pl services/payment -am test`: **309/309 passing**.

---

## Recommended follow-up order

1. Add explicit `POST` + path assertions to `shouldSendCorrectBodyAndBearerTokenOnAttest` (Gap 4).
2. Add lowercase-outcome tests (Gap 1).
3. Add focused per-field missing-field tests (Gap 3).
4. Decide and test the `BLOCKED`-without-`reason` behavior (Gap 2).
5. Optionally add a problem+json `409` test (Gap 5) and an explicit `outcome: null` test (Gap 6).
