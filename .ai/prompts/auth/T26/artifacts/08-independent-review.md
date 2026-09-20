# auth · T26 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T26 — API-key CRUD controller |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/06-implementation-notes.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the completed implementation with fresh eyes. Findings only — no code changes made in this phase.

---

## Findings

### 1. Malformed `keyUuid` path segment on `DELETE /api-keys/{keyUuid}` produces 500, not 400

- **Issue:** A request such as `DELETE /api-keys/not-a-uuid` causes Spring's path-variable-to-`UUID` conversion to throw `MethodArgumentTypeMismatchException`. This exception has no dedicated handler anywhere in the service, so it falls through to the framework-level `@ExceptionHandler(Exception.class)` in `ApiExceptionHandler`, which returns an opaque 500 with a `trace_id` and logs a stack trace. This contradicts R46 (4xx errors should be RFC 9457 `application/problem+json`) and the frozen brief's own Constraints section, which states a malformed UUID "400s before the handler runs."
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java:89` — `@PathVariable UUID keyUuid`, no manual parsing or guard.
  - `services/auth/src/main/java/com/themistra/auth/common/ApiExceptionHandler.java` — no `MethodArgumentTypeMismatchException` or `TypeMismatchException` handler exists.
  - A codebase-wide search for `MethodArgumentTypeMismatchException` / `TypeMismatchException` returns no matches under `src/main/java`.
- **Recommendation:**
  - **Within T26 scope:** none. Fixing this properly requires adding a handler to `common/ApiExceptionHandler.java`, which is outside T26's authorized file set (frozen brief Files NOT to Modify).
  - **Brief correction:** amend the frozen brief's Constraints section to remove the inaccurate claim that malformed UUIDs 400; instead document this as a named, pre-existing, service-wide limitation (it affects every `@PathVariable UUID` admin endpoint as well).
  - **Forward owner:** a cross-cutting follow-up task to add a `MethodArgumentTypeMismatchException` → 400 handler in `ApiExceptionHandler`, benefiting all endpoints at once.
- **Confidence:** High — verified by code inspection and confirmed against the same gap the Phase 7 self-review identified.

---

### 2. `ApiKeyExceptionHandler` class Javadoc is stale (says "T25" only)

- **Issue:** The class-level Javadoc states it maps "this module's domain exceptions to RFC 9457 responses (T25)," but the class now also handles `ApiKeyNotFoundException` and `ApiKeyNotAuthorizedException` for T26.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExceptionHandler.java:9-11` — Javadoc mentions only T25; lines 35-50 add T26 handlers.
- **Recommendation:** Update the Javadoc to mention both T25 and T26 (e.g., "Maps this module's domain exceptions to RFC 9457 responses (T25, T26)."). No behavioral change.
- **Confidence:** High — purely cosmetic, but stale Javadoc on a freshly modified class is a minor maintenance hazard.

---

### 3. No explicit assertion that `CreateApiKeyRequest` validation stays in sync with `ApiKeyService.requireValidName`

- **Issue:** `CreateApiKeyRequest` uses `@NotBlank @Size(max = 100)` while `ApiKeyService.requireValidName` rejects null/blank/`length() > 100`. The bounds currently agree, but there is no test or compile-time link forcing them to stay in sync. A future change to one without the other could either (a) let an invalid name reach the service and produce a 500, or (b) reject names at the HTTP layer that the service would accept.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/apikey/dto/CreateApiKeyRequest.java:15` — `@NotBlank @Size(max = 100)`.
  - `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java:202-207` — `requireValidName` enforces the same boundary by hand.
- **Recommendation:** Add a unit test (Phase 10) that asserts the exact boundary values (1-character name accepted, blank/101-character name rejected) and consider adding a code comment in `CreateApiKeyRequest` that references `ApiKeyService.MAX_NAME_LENGTH` so a future change to the service constant prompts a matching DTO change. Alternatively, derive the DTO's `@Size(max = ...)` from a shared constant if the frozen file set permits.
- **Confidence:** Medium — the current code is correct; this is a future-regression guard, not a present bug.

---

### 4. `POST /api-keys` response DTO (`CreateApiKeyResult`) does not explicitly document its JSON shape in a test

- **Issue:** The controller returns `ApiKeyService.CreateApiKeyResult` directly. Its field names (`keyUuid`, `plaintextKey`, `name`, `createdAt`) become the JSON response field names. There is no test in the current suite that serializes this record and asserts the exact JSON keys or the absence of a `keyHash` field.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java:253-265` defines `CreateApiKeyResult` with the redacted `toString()` but no JSON annotations; `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java:62-67` returns it directly.
- **Recommendation:** Add a Phase 10 unit test that serializes a `CreateApiKeyResult` with an `ObjectMapper` and asserts the JSON keys are exactly `{keyUuid, plaintextKey, name, createdAt}` and that no 64-character-hex-shaped value (a SHA-256 hash) appears. This makes AC6 provable without relying solely on the record's construction.
- **Confidence:** Medium — structural safety by construction is good, but an explicit serialization assertion closes the loop on AC6.

---

## Non-Issues Confirmed

- **Bean validation boundary:** `@NotBlank @Size(max = 100)` on `CreateApiKeyRequest.name` matches `ApiKeyService.requireValidName`'s 100-character limit exactly; no off-by-one.
- **Rejected-value leakage:** `ApiExceptionHandler.onValidationFailure` builds violations from field + message only, never `getRejectedValue()`, satisfying R46.
- **No hash leak by construction:** `CreateApiKeyResult` and `ApiKeyMetadata` have no `keyHash` field; the controller cannot accidentally serialize it.
- **Exception routing across modules:** `InvalidAccountStateException` and `AccountNotFoundException` thrown via `ApiKeyService` resolve through the existing, untouched `AccountExceptionHandler`, consistent with global `@RestControllerAdvice` semantics.
- **Idempotency:** `DELETE /api-keys/{keyUuid}` relies on `ApiKeyService.revoke`'s already-idempotent `revokeIfActive`; second revoke returns 204.
- **Thread-safety / module boundaries:** `ApiKeyController` holds only `final` fields; no new class imports `PublicEndpoints` or a foreign-module entity.
- **No `Location` header on 201:** correctly omitted per D8; no endpoint exists for it to resolve to.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (human gate) on approval.
