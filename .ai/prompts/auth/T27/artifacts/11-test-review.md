# auth · T27 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T27 — API-key integration tests |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the resolved T27 test against the frozen brief (`artifacts/04-frozen-task-brief.md`) and acceptance criteria AC1–AC5. Findings only — no test code changed in this phase.

---

## Gaps

### 1. AC3's required `ProblemTypes.API_KEY_EXCHANGE_REJECTED` is not explicitly asserted on the 401 responses

- **Gap:** Steps 7 and 8 assert status 401, `application/problem+json` Content-Type, no `"detail"` key, and byte-for-byte body equality. They do not parse the body and assert `type = "https://checky.pro/problems/api-key-exchange-rejected"` or `title = "API key is invalid or revoked"`.
- **Why it matters:** AC3 requires the post-revocation 401 to match "the shape every other rejection cause produces (`ProblemTypes.API_KEY_EXCHANGE_REJECTED`, no detail)." The current assertions prove uniformity and absence of `detail`, but they do not prove the correct problem type/title. If the handler somehow returned a different but consistent problem type for both the revoked key and the malformed key, the byte-for-byte check would still pass while AC3's named type requirement would be violated.
- **Suggested test:** Parse both 401 bodies as JSON objects and assert `type` and `title` equal the expected values before the raw-string equality check, or extract a shared helper that asserts the standard `ApiKeyExceptionHandler` problem shape.

---

### 2. `GET /api-keys` response Content-Type is not asserted

- **Gap:** Steps 2, 4, and 6 now assert status 200 (Phase 9 fix) before parsing, but they do not assert that the response Content-Type is `application/json`.
- **Why it matters:** AC5 relies on `GET /api-keys` as the observation point for `last_used_at`/`revoked_at`. A misconfigured controller or filter that returned 200 with a non-JSON body would still fail at `readJson`, but the failure message would be a parse error rather than a clear Content-Type mismatch. This is minor but weakens the contract assertion.
- **Suggested test:** Add `assertThat(listResponse.getHeaders().getContentType().toString()).contains("application/json")` alongside the existing 200 assertions.

---

### 3. The lifecycle test does not assert the exact field set of the `POST /api-keys` response

- **Gap:** Step 1 asserts the presence of `keyUuid`, `plaintextKey`, and the plaintext key's shape, but it does not assert that the response body contains exactly `{keyUuid, plaintextKey, name, createdAt}` or that no hash-shaped/internal field is present.
- **Why it matters:** While T26's `ApiKeyCrudIntegrationTest` already covers the create-response shape, this lifecycle test's own step 1 is the entry point for the entire flow. A regression that added an unexpected field (e.g., `accountUuid`) or omitted `createdAt` would not be caught here, even though the rest of the flow might still pass.
- **Suggested test:** Add `assertThat(created.fieldNames().toIterable()).containsExactlyInAnyOrder("keyUuid", "plaintextKey", "name", "createdAt")` and an assertion that the body does not contain a 64-character hex string (the hash shape) — mirroring T26's coverage but making the lifecycle test self-contained.

---

### 4. The exchanged JWT's usability is only proven by decoding, not by using it

- **Gap:** Step 3 decodes the JWT and asserts `sub`/`scope`/`amr`, proving structural correctness. It does not use the JWT to make an authenticated call to a resource-server endpoint (e.g., `GET /api-keys`).
- **Why it matters:** AC1 says "exchange succeeds (200, decodable JWT with expected `sub`/`scope`/`amr`) before any revocation" — the brief's wording is satisfied by decoding. However, a token that is structurally correct but rejected by the resource-server filter (e.g., due to an `aud` mismatch or signing-key issue) would still pass this assertion while being unusable in practice.
- **Suggested test:** Low priority — acceptable to leave as-is because the brief explicitly limits step 3 to decoding. If stronger "usability" proof is desired, add a step 3b that calls `GET /api-keys` with the exchanged JWT and asserts 200, but note this expands the test scope beyond the frozen brief.

---

### 5. `ApiKeyLifecycleIntegrationTest` has not been executed

- **Gap:** The Phase 10 notes state Docker is unavailable, so the one lifecycle test was compiled but not run.
- **Why it matters:** This is the task's sole deliverable. Compilation does not prove the eight-step sequence passes end-to-end, especially after the Phase 9 additions (status and Content-Type assertions).
- **Suggested test:** No new test needed — run `ApiKeyLifecycleIntegrationTest` with a working Docker daemon before the Phase 12 final verification gate. Per the frozen brief's D3, verify T25's `ApiKeyTokenIssuer`/`JwtEncoder` first if the test fails.

---

## Non-Gaps

- **Named tests from `package.md` §8 not duplicated here:** correctly delegated to `ApiKeyServiceIntegrationTest`, `ApiKeyExchangeIntegrationTest`, and `ApiKeyCrudIntegrationTest`, per the frozen brief and test Javadoc.
- **Byte-for-byte 401 body comparison:** matches AC3's wording; uniformity is the criterion.
- **`GET /api-keys` status assertions:** added in Phase 9; no longer a gap.
- **No `detail` in 401 bodies:** asserted in Phase 9.
- **`last_used_at` / `revoked_at` observation:** correctly implemented via `GET /api-keys` per D1.
- **No production code changes:** regression risk is zero.

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Final Verification) on approval.
