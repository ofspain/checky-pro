<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T26 · Phase 6 — Implementation Notes

Implements `artifacts/05-implementation-plan.md` against the frozen brief (`artifacts/04-frozen-task-brief.md`). No tests written in this phase (Phase 10). No file outside the plan's authorized set was touched.

---

## Files Created

### `apikey/dto/CreateApiKeyRequest.java`
`record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name)` — exactly as planned (D1). Bounds mirror `ApiKeyService.requireValidName`'s own limit.

---

## Files Modified

### `common/ProblemTypes.java`
Added `API_KEY_NOT_FOUND` and `API_KEY_NOT_AUTHORIZED` (D5), same pattern as the existing constants — a `URI.create(BASE + "...")` literal with a one-line Javadoc naming the requirement and task.

### `apikey/ApiKeyExceptionHandler.java`
Added `onNotFound(ApiKeyNotFoundException)` → 404 / `API_KEY_NOT_FOUND` / "API key not found", no detail. Added `onNotAuthorized(ApiKeyNotAuthorizedException)` → 403 / `API_KEY_NOT_AUTHORIZED` / "Not authorized to perform this action", no detail. Neither sets `detail`, matching R46 and the existing `onExchangeRejected`'s shape exactly. The pre-existing `onExchangeRejected` method is untouched.

### `apikey/ApiKeyController.java`
Added `create`, `list`, `revoke` exactly per the plan's signatures:
- `create` — `@PostMapping`, `Authentication` + `@Valid @RequestBody CreateApiKeyRequest`, returns `ResponseEntity.status(CREATED).body(ApiKeyService.CreateApiKeyResult)` (D2, D4). No `Location` header (D8).
- `list` — `@GetMapping`, returns `List<ApiKeyService.ApiKeyMetadata>` directly (D3).
- `revoke` — `@DeleteMapping("/{keyUuid}")`, `@PathVariable UUID keyUuid`, returns `ResponseEntity.noContent().build()`.

All three derive the caller via `UUID.fromString(authentication.getName())` inline (D7 — no shared private helper introduced for a single line, matching `AccountController`'s own style). None of the three catch any exception locally — `ApiKeyNotAuthorizedException`, `ApiKeyNotFoundException`, `InvalidAccountStateException`, and Bean Validation failures all propagate uncaught for the appropriate `@RestControllerAdvice` to translate. The pre-existing `exchange` method, `SCHEME`/`MAX_CREDENTIAL_LENGTH` constants, and `extractCredential` are untouched; the class Javadoc was updated to describe the class's now-broader responsibility (CRUD + exchange) without restating either task's requirement IDs beyond a one-line reference.

No file outside this list, and no file under `spec/`, was touched. `ApiKeyService`, `ApiKeyRepository`, `ApiKey`, `ApiKeyHasher`, `ApiKeyProperties`, `ApiKeyExchangeRejectedException`, `ApiKeyNotFoundException`, `ApiKeyNotAuthorizedException`, `ApiKeyTokenIssuer`, `AccountExceptionHandler`, `InvalidAccountStateException`, and `PublicEndpoints` are all unmodified, confirmed by `git status`/diff at the end of this phase.

---

## Acceptance Criteria — mapping

| AC | Status | Evidence |
|---|---|---|
| AC1 | Done | Neither `create`, `list`, nor `revoke` is registered in `PublicEndpoints`; they sit behind the `@Order(2)` chain's default `authenticated()` rule. |
| AC2 | Done | All three derive `accountUuid` from `Authentication` only — no path/body-supplied account identifier exists anywhere in these three endpoints. |
| AC3 | Done | `create` returns 201 with `CreateApiKeyResult`, whose `plaintextKey` field carries the one-time key. |
| AC4 | Done | `@Valid @RequestBody CreateApiKeyRequest` — a blank/over-length name triggers `MethodArgumentNotValidException`, already mapped by the framework-level `ApiExceptionHandler.onValidationFailure` (unchanged, no new code needed). |
| AC5 | Done | `list` returns `ApiKeyMetadata` directly, scoped to the resolved `accountUuid`; empty list is a plain `200`. |
| AC6 | Done by construction | Both `CreateApiKeyResult` and `ApiKeyMetadata` have no hash field (T24, unchanged) — not newly verified by an executed test this phase (Phase 10). |
| AC7 | Done | `ApiKeyNotFoundException` → `onNotFound`, one exception type for both "doesn't exist" and "not yours," no detail. |
| AC8 | Done (pre-existing) | `ApiKeyService.revoke`'s idempotent `revokeIfActive` (T24, unmodified) means a second revoke doesn't throw; `revoke` always returns 204 when the service call returns normally. |
| AC9 | Done | `onNotAuthorized` — 403, `API_KEY_NOT_AUTHORIZED`, no detail. |
| AC10 | Done, unchanged | No new code; `InvalidAccountStateException` continues to resolve via the existing, untouched `AccountExceptionHandler` (409 with detail), per D6. |
| AC11 | Done | `create` returns a plain `ResponseEntity` with only a body and status set — no `Location` header added anywhere. |
| AC12 | Expected, not independently re-run this session | No new class references `PublicEndpoints`; no entity import. `ArchitectureTest` itself needs Docker in this sandbox's Surefire wiring to report non-zero test counts (same pre-existing quirk noted at T16 Phase 12) — verified by direct code inspection instead. |

---

## Deviations Forced by Reality

None. Implementation matches the plan and frozen brief exactly — no surprises surfaced during coding (T24's service layer was already fully complete and needed zero changes, and the frozen brief's D1–D8 had already resolved every ambiguity Kimi's Phase 3 review found).

---

## Build Verification

`mvn -q -pl services/auth -am compile` — clean, exit 0.

Regression check on every Docker-independent test that exercises a file this phase touched: **42/42 pass** — `ApiKeyControllerTest` (15), `ApiKeyExceptionHandlerTest` (2), `ApiKeyTokenIssuerTest` (7), `ApiKeyPropertiesTest` (6), `JwksConfigTest` (1), `TokenClaimsCustomizerTest` (8), `ApiKeyHasherTest` (3). None of T25's existing tests for `exchange`/`onExchangeRejected` were affected by the new additions.

**Not verified this session (needs Docker):** the full request/response cycle for `create`/`list`/`revoke` through the real filter chain, `ArchitectureTest`, and `ApiKeyServiceIntegrationTest` — same pre-existing environment gap carried since T25 (`docker info` still fails). Phase 10 will write `ApiKeyCrudIntegrationTest` per the plan; it will compile but not execute until Docker is available.

---

**Phase 6 complete — implementation written.** Proceed to Phase 7 (Self Review) on approval.
