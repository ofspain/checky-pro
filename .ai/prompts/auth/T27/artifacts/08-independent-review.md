# auth · T27 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T27 — API-key integration tests |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/06-implementation-notes.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the completed lifecycle test with fresh eyes. Findings only — no test code changed in this phase.

---

## Findings

### 1. `GET /api-keys` responses are parsed without asserting HTTP status first

- **Issue:** The three `GET /api-keys` calls in the lifecycle sequence are wrapped by `readJson(...)` without first asserting that the response status is `200 OK`. If any of these calls fail — for example, because the bearer token is rejected (401/403), the server returns 500, or the path changes — the test will fail with a JSON-parsing exception (`IllegalStateException: Could not parse response body as JSON: ...`) rather than a clear status-code assertion.
- **Evidence:** `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyLifecycleIntegrationTest.java:118`, `:132`, `:142` — each does `findByKeyUuid(readJson(get(bearer, "/api-keys")), keyUuid)`. The `get` helper returns `ResponseEntity<String>` but does not assert status; `readJson` parses unconditionally.
- **Recommendation:** Inline the `GET` response and assert `getStatusCode()` is `HttpStatus.OK` before parsing, or add a status assertion inside the `get` helper (with an overload for expected status if other tests need flexibility). For example:
  ```java
  ResponseEntity<String> listResponse = get(bearer, "/api-keys");
  assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  JsonNode beforeExchange = findByKeyUuid(readJson(listResponse), keyUuid);
  ```
- **Confidence:** High — the current code will produce confusing failure output for any GET-side regression.

---

### 2. Byte-for-byte 401 body comparison is brittle to Jackson serialization changes

- **Issue:** Step 8 compares `secondExchange.getBody()` and `malformedExchange.getBody()` as raw strings to prove byte-for-byte uniformity (AC3). This relies on `ProblemDetail`'s Jackson serialization producing identical field ordering and whitespace for the two responses. While the self-review argues this is deterministic for identically-constructed objects, it is still sensitive to any future change in Spring's `ProblemDetail` serialization mixin, Jackson configuration, or accidental addition of a varying field.
- **Evidence:** `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyLifecycleIntegrationTest.java:154` — `assertThat(secondExchange.getBody()).isEqualTo(malformedExchange.getBody());`.
- **Recommendation:** Keep the byte-for-byte assertion (it directly matches AC3's wording), but add a defensive fallback: parse both bodies as JSON trees/maps and assert they are equal as structured data. This way the test still fails if the bodies genuinely differ, but it does not fail on harmless formatting differences. Alternatively, assert each parsed body's `status`, `type`, and `title` individually as a secondary guard. The raw-string assertion can remain as the primary AC3 check.
- **Confidence:** Medium — the current approach is correct in the current codebase, but it is more fragile than a parsed comparison.

---

### 3. The 401 response Content-Type and absence of `detail` are not explicitly asserted

- **Issue:** Steps 7–8 assert status 401 and byte-for-byte body equality, but they do not assert that the response is `application/problem+json` or that the body contains no `detail` field. If both the revoked-key and malformed-key responses were to degrade to plain JSON or acquire a `detail` field simultaneously, the byte-for-byte comparison would still pass while the contract required by R46 would be violated.
- **Evidence:** `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyLifecycleIntegrationTest.java:146-154` — only status and raw body equality are checked.
- **Recommendation:** Add assertions on `secondExchange.getHeaders().getContentType()` (must contain `application/problem+json`) and parse the body to assert `detail` is absent. This aligns with R46 and the existing `ApiKeyCrudIntegrationTest` pattern (`createRejectsCallerLackingMerchantRoleOrConfirmedMfa` already checks `doesNotContain("detail")`).
- **Confidence:** High — the gap is real, though it is partially covered by other tests in the module.

---

## Non-Issues Confirmed

- **Race conditions:** synchronous `TestRestTemplate` calls ensure each HTTP step completes (and its server-side transaction commits) before the next begins.
- **Fresh account isolation:** a unique email is used, so `findByKeyUuid` matches exactly one key.
- **API-key JWT used for CRUD authentication:** confirmed intended behavior per frozen brief D4 and already documented in the test Javadoc.
- **No production code touched:** the file is test-only, as required.
- **`last_used_at` / `revoked_at` observation via `GET /api-keys`:** correctly implemented per frozen brief D1.
- **Byte-for-byte uniformity intent:** the deliberate raw-string comparison matches AC3's wording; the finding above is about robustness, not correctness.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (human gate) on approval.
