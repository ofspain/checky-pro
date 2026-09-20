<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T26 · Phase 7 — Self Review

Reviews the Phase 6 diff (`artifacts/06-implementation-notes.md`) against the frozen brief and `agents.md`. Findings only — no fixes applied here (Phase 9).

---

### Finding 1

**Issue:** A malformed `keyUuid` path segment on `DELETE /api-keys/{keyUuid}` (e.g., `DELETE /api-keys/not-a-uuid`) does not produce the 400 the frozen brief assumed. Spring's path-variable-to-`UUID` conversion failure raises `MethodArgumentTypeMismatchException`, which is not `MethodArgumentNotValidException` and has no dedicated handler anywhere in this codebase — it falls through to `ApiExceptionHandler`'s generic `@ExceptionHandler(Exception.class)` catch-all, producing an opaque 500 (logged with a stack trace and `trace_id`) instead of a client-correctable 400.

**Severity:** Medium

**Evidence:** `apikey/ApiKeyController.java:89` (`@PathVariable UUID keyUuid`, no manual parsing/guard); `common/ApiExceptionHandler.java` (no `MethodArgumentTypeMismatchException`/`TypeMismatchException` handler exists anywhere under `src/main/java`, confirmed by search); the frozen brief's own Constraints section stated "`keyUuid` path variable is framework-validated as a UUID; malformed input 400s before the handler runs" — that assumption does not hold given this codebase's actual exception-handling setup.

**Failure scenario:** A client (or an attacker probing the endpoint) sends `DELETE /api-keys/abc` — gets a 500 with a stack trace logged server-side and a `trace_id` in the body, rather than a clean 400 telling them the path segment isn't a valid UUID.

**Not a T26-introduced regression:** this exact gap already exists, unaddressed, for every other `@PathVariable UUID` in the service — `AdminAccountController.get/activate/suspend/reinstate/delete/unlock` and `AdminAccountRoleController`'s four endpoints all take `@PathVariable UUID accountUuid` with the identical exposure. T26 inherits a pre-existing, service-wide gap rather than introducing a new one; fixing it properly (a `MethodArgumentTypeMismatchException` handler in the framework-level `ApiExceptionHandler`) is a change well beyond this task's authorized file scope (`common/ApiExceptionHandler.java` is not in T26's Files to Modify).

**Recommendation:** No fix within T26's scope. Flagged for the record — same disposition class as D7 (the non-UUID-`Authentication`-principal limitation already accepted at the Phase 4 gate): a named, pre-existing, out-of-scope limitation, not silently inherited unremarked. Worth a dedicated cross-cutting follow-up task if this rises to priority (would fix it for every admin endpoint at once, not just this one).

---

## Non-Issues Considered and Ruled Out

- **Validation boundary consistency:** `CreateApiKeyRequest`'s `@Size(max = 100)` and `ApiKeyService.requireValidName`'s `length() > 100` check agree exactly at the 100-character boundary (both accept exactly 100) — no off-by-one mismatch between the DTO-level and service-level checks, even though the service-level check is now unreachable via this HTTP path.
- **Rejected-value leakage on validation failure:** `ApiExceptionHandler.onValidationFailure` builds its `Violation` records from `field`/`message` only, never `getRejectedValue()` — a caller who submits an over-length or malicious `name` never gets it echoed back, satisfying AC4/R46 with zero new code.
- **No hash-shaped field reachable in any response:** `ApiKeyService.CreateApiKeyResult` (`keyUuid`, `plaintextKey`, `name`, `createdAt`) and `ApiKeyService.ApiKeyMetadata` (`keyUuid`, `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, `revokedAt`) both lack a hash field by construction (T24) — returning either directly cannot leak `keyHash` regardless of what Phase 10's tests assert.
- **Exception resolution across module boundaries:** confirmed by re-reading Spring's `@RestControllerAdvice` semantics (global, type-matched, not package-scoped) that `InvalidAccountStateException` (thrown from `apikey` via a call into `account`) and `AccountNotFoundException` (thrown by `ApiKeyService.list`/`.create`'s internal account lookups) both resolve correctly via the existing, untouched `AccountExceptionHandler` with zero new `apikey`-module code.
- **`@Valid` without class-level `@Validated`:** works correctly on a `@RequestBody` parameter regardless of class-level annotations (unlike simple path/query param validation, which does need `@Validated`) — confirmed against the identical precedent in `AccountController.register`.
- **Idempotency:** `POST /api-keys` is correctly non-idempotent (each call mints a distinct new key, matching R30); `DELETE /api-keys/{keyUuid}` is correctly idempotent (matches `ApiKeyService.revoke`'s pre-existing behavior, unchanged by this task).
- **Thread-safety / module boundaries:** `ApiKeyController` holds only `final` fields, no new mutable state; no new class imports `PublicEndpoints` or a foreign-module entity.

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi Independent Review) on approval.
