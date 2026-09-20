# auth · T26 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T26 — API-key CRUD controller |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |
| **Status** | Findings for Phase 4 human gate |

## Summary

The TIB is largely a faithful pass-through over the already-implemented T24 service layer, but it under-specifies how validation errors and authentication-principal parsing reach the correct HTTP status. The biggest risk is that `ApiKeyService.create`'s `IllegalArgumentException` on a bad name currently maps to a 500 unless the controller adds bean validation. A smaller but real risk is the assumption that every authenticated caller has a UUID-shaped principal name.

---

## Findings

### 1. `POST /api-keys` name validation relies on service-level `IllegalArgumentException`, which maps to 500

- **Severity:** High
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java:202-207` — `requireValidName` throws `IllegalArgumentException` for null/blank/>100-character names.
  - `services/auth/src/main/java/com/themistra/auth/common/ApiExceptionHandler.java:56-67` — `IllegalArgumentException` is not explicitly mapped; it falls through to the catch-all `onUnexpected`, producing 500.
  - The TIB's Required Test #5 expects a **400 validation error** for blank or >100-character names, and AC8/R46 require RFC 9457 problem bodies for 4xx errors.
- **Recommended brief amendment:** Require `CreateApiKeyRequest.name` to carry bean-validation annotations (`@NotBlank` + `@Size(max = 100)`) and the controller method to accept `@Valid @RequestBody CreateApiKeyRequest`. This lets Spring's `MethodArgumentNotValidException` flow to the existing framework-level `ApiExceptionHandler.onValidationFailure`, producing the expected 400 `application/problem+json` body. Also add an explicit AC: "Invalid request body produces 400 `application/problem+json` with no rejected value echoed."

---

### 2. Caller identity assumes `Authentication.getName()` is always a UUID

- **Severity:** Medium
- **Evidence:**
  - The TIB states caller identity comes from `Authentication`, matching the established `UUID.fromString(authentication.getName())` pattern (`AccountController`).
  - That pattern is safe for interactive tokens (principal = account UUID) and API-key tokens (`sub` = account UUID), but not for client-credentials tokens or future service-account tokens where `sub`/`principal_name` may be a client id such as `checky-api-key` or `payment-service`.
  - `UUID.fromString("payment-service")` throws `IllegalArgumentException`, which maps to 500 (Finding #1).
- **Recommended brief amendment:** Either (a) explicitly scope these endpoints to interactive/API-key principals only and add a guard that converts non-UUID principal names into a 403 `ApiKeyNotAuthorizedException` (or generic 401), or (b) record as a known limitation that service-account JWTs are unsupported for these endpoints and a 500 is the accepted residual until a broader principal-resolution abstraction is introduced.

---

### 3. The exact response type for `POST /api-keys` is ambiguous

- **Severity:** Medium
- **Evidence:**
  - The TIB's Files to Create lists `apikey/dto/CreateApiKeyRequest.java` and `apikey/dto/ApiKeyResponse.java`, but `ApiKeyResponse` is described only for the `GET /api-keys` list.
  - `ApiKeyService.create` already returns `CreateApiKeyResult(keyUuid, plaintextKey, name, createdAt)` — a record that exactly matches the TIB's stated POST response fields.
  - The TIB says the POST response "must include the plaintext key and must not include the hash" but does not say whether to reuse `CreateApiKeyResult`, wrap it, or invent a new DTO.
- **Recommended brief amendment:** Lock the POST response type. Recommended: reuse `ApiKeyService.CreateApiKeyResult` directly (it already cannot leak the hash by construction) and avoid creating a second DTO that duplicates its fields; alternatively, define a dedicated `CreateApiKeyResponse` and delete `CreateApiKeyResult` from the public API. Add the exact field list and HTTP 201 status to the Outputs table.

---

### 4. HTTP 201 status for `POST /api-keys` has no implementation mechanism

- **Severity:** Medium
- **Evidence:**
  - The TIB Outputs table specifies **201** for `POST /api-keys`, but the planned controller methods are not described as returning `ResponseEntity` or using `@ResponseStatus`.
  - A plain `@PostMapping` method returning a DTO produces **200** by default.
- **Recommended brief amendment:** Require either `ResponseEntity.status(HttpStatus.CREATED).body(...)` or `@ResponseStatus(HttpStatus.CREATED)` on the `create` method, and add a test assertion for status 201 (Required Test #1).

---

### 5. `InvalidAccountStateException` maps to 409 CONFLICT with a detail message — verify this is the intended behavior

- **Severity:** Medium
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java:25-32` maps `InvalidAccountStateException` to HTTP 409 with `ProblemTypes.INVALID_STATE` and sets `detail(e.getMessage())`.
  - The TIB's Required Test #4 says "Non-`ACTIVE` account on `POST /api-keys` → whatever `AccountExceptionHandler` already produces" and flags it for verification.
  - R46 requires "no internal detail" for 4xx errors, but the 409 body will contain the exception message (e.g., the account UUID and target action).
- **Recommended brief amendment:** Confirm at the gate whether 409-with-detail is acceptable for this endpoint or whether a dedicated mapping (e.g., 403 with no detail) is needed. If the existing 409 is accepted, document it as an explicit deviation from the general "no internal detail" rule for this specific cross-module exception. If not, add a local mapping in `ApiKeyExceptionHandler` for `InvalidAccountStateException` → 403 with no detail, or request `AccountExceptionHandler` be changed.

---

### 6. `ApiKeyNotFoundException` and `ApiKeyNotAuthorizedException` problem-type naming is unresolved

- **Severity:** Low
- **Evidence:**
  - The TIB says `ApiKeyExceptionHandler` should map `ApiKeyNotFoundException` → 404 and `ApiKeyNotAuthorizedException` → 403, and that `ApiKeyNotFoundException` can "likely reuse the existing generic `NOT_FOUND` constant."
  - Reusing `ProblemTypes.NOT_FOUND` with a generic title is safe for enumeration. For `ApiKeyNotAuthorizedException`, no constant exists; the TIB says to add one.
- **Recommended brief amendment:** Lock the problem types. Recommended: add `API_KEY_NOT_FOUND` and `API_KEY_NOT_AUTHORIZED` constants in `ProblemTypes.java` with generic titles (e.g., "Not found", "Not authorized") and no detail. This is consistent with the module-specific `API_KEY_EXCHANGE_REJECTED` constant added in T25 and avoids overloading the cross-module `NOT_FOUND` type with a different title.

---

### 7. No explicit guard that the `POST /api-keys` response cannot contain the hash

- **Severity:** Low
- **Evidence:**
  - AC5 says no response except the single POST success body may contain a hash or plaintext secret. The POST success body must include the plaintext key.
  - `ApiKeyService.CreateApiKeyResult` contains only `keyUuid`, `plaintextKey`, `name`, and `createdAt` by construction — it has no hash field. So the controller cannot accidentally leak the hash if it returns this record.
  - The TIB does not make this invariant explicit in the acceptance criteria.
- **Recommended brief amendment:** Add an AC or constraint note: "The `POST /api-keys` response DTO must be derived from `ApiKeyService.CreateApiKeyResult` (or an equivalent DTO) that has no hash field by construction; the plaintext key field must be named and typed so it cannot be confused with a hash." Also add a test assertion that the response body does not contain a 64-character hex string (the SHA-256 hash shape).

---

### 8. `Location` header for the 201 create response is not specified

- **Severity:** Low / informational
- **Evidence:**
  - The TIB specifies 201 for `POST /api-keys` but says nothing about a `Location` header.
  - REST convention would point to `GET /api-keys/{keyUuid}`, but no `GET /api-keys/{keyUuid}` endpoint exists (only list and delete).
- **Recommended brief amendment:** Explicitly decide and lock: either (a) omit the `Location` header entirely, or (b) add `Location: /api-keys/{keyUuid}` even though there is no GET-by-UUID endpoint. Do not leave it implicit; the test author needs to know whether to assert its absence.

---

## Non-Findings

- **Thread-safety / stateless controller:** the planned design is a standard `@RestController` with `final` service dependencies — no mutable instance state.
- **Module boundaries:** `ApiKeyService` already returns records/DTOs, not the `ApiKey` entity, so the controller satisfies L12 by construction.
- **Idempotent revoke / identical 404:** already enforced by `ApiKeyService.revoke` and the single `ApiKeyNotFoundException` type.
- **No `PublicEndpoints` changes:** all three endpoints are authenticated, consistent with R30/R34/R35.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (human gate) on approval.
