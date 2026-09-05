<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T26 · Phase 5 — Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN, approved 2026-08-15). No code written in this phase.

---

## Files to Create

### `apikey/dto/CreateApiKeyRequest.java`
```java
public record CreateApiKeyRequest(
        @NotBlank @Size(max = 100) String name
) {}
```
Bounds mirror `ApiKeyService.requireValidName`'s own limit exactly (D1) — the service's check becomes unreachable defense-in-depth via this HTTP path, same relationship `create`'s MERCHANT/MFA re-check already has to a hypothetical non-HTTP caller.

No other new file. `apikey/dto/ApiKeyResponse.java` is explicitly **not created** (D2/D3).

---

## Files to Modify

### `common/ProblemTypes.java`
Add two constants (D5), following the existing pattern exactly:
```java
public static final URI API_KEY_NOT_FOUND = URI.create(BASE + "api-key-not-found");
public static final URI API_KEY_NOT_AUTHORIZED = URI.create(BASE + "api-key-not-authorized");
```

### `apikey/ApiKeyExceptionHandler.java`
Add two `@ExceptionHandler` methods to the existing class, same shape as its current `onExchangeRejected`:

**Public/package methods (Spring handler methods, package-private per existing convention):**
- `ProblemDetail onNotFound(ApiKeyNotFoundException e)` → `HttpStatus.NOT_FOUND`, `ProblemTypes.API_KEY_NOT_FOUND`, title "API key not found", no detail (R35/AC7 — identical whether nonexistent or unowned, since `ApiKeyNotFoundException` carries no distinguishing state).
- `ProblemDetail onNotAuthorized(ApiKeyNotAuthorizedException e)` → `HttpStatus.FORBIDDEN`, `ProblemTypes.API_KEY_NOT_AUTHORIZED`, title "Not authorized to perform this action", no detail (AC9).

No change to the existing `onExchangeRejected` method.

### `apikey/ApiKeyController.java`
Add three public handler methods to the existing class. No change to the existing `exchange` method, `SCHEME`/`MAX_CREDENTIAL_LENGTH` constants, or `extractCredential`.

**New field:** none — `ApiKeyService` is already an injected constructor dependency (`ApiKeyTokenIssuer` is the only other one; unused by the three new methods).

**Public methods (signatures):**
```java
@PostMapping
public ResponseEntity<ApiKeyService.CreateApiKeyResult> create(
        Authentication authentication, @Valid @RequestBody CreateApiKeyRequest request)

@GetMapping
public List<ApiKeyService.ApiKeyMetadata> list(Authentication authentication)

@DeleteMapping("/{keyUuid}")
public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable UUID keyUuid)
```

**Private methods:** none new. Caller-identity extraction (`UUID.fromString(authentication.getName())`, D7) is a one-line inline call at each method's start, matching `AccountController.me()`/`.changePassword()`'s established style exactly — those methods don't factor it into a shared private helper either, so introducing one here would be an unrequested abstraction over a single line, not a simplification.

**Method bodies (behavior, not final code):**
- `create` — extract `accountUuid`; call `apiKeyService.create(accountUuid, request.name())`; return `ResponseEntity.status(HttpStatus.CREATED).body(result)` (D4). No `Location` header (D8). `ApiKeyNotAuthorizedException`/`InvalidAccountStateException`/validation failures all propagate uncaught for the respective `@RestControllerAdvice` to translate (D5/D6/D1) — no local try/catch.
- `list` — extract `accountUuid`; return `apiKeyService.list(accountUuid)` directly (D3). No exception path — `ApiKeyService.list` cannot throw for a well-formed caller.
- `revoke` — extract `accountUuid`; call `apiKeyService.revoke(accountUuid, keyUuid)`; return `ResponseEntity.noContent().build()`. `ApiKeyNotFoundException` propagates uncaught for `ApiKeyExceptionHandler` to translate (404).

---

## Entities Used

None directly — `ApiKeyController` never imports `ApiKey` (matches L12; `ApiKeyService`'s public API is entity-free by construction).

## Repositories Used

None directly — `ApiKeyRepository` is package-private and already fully exercised by `ApiKeyService`.

## Services Used

- `ApiKeyService.create(UUID, String)` — pre-existing, T24, unmodified.
- `ApiKeyService.list(UUID)` — pre-existing, T24, unmodified.
- `ApiKeyService.revoke(UUID, UUID)` — pre-existing, T24, unmodified.

## Tests Required

Per the frozen brief's Required Tests list (verbatim numbering):

1. **`shouldCreateApiKeyAndShowPlaintextExactlyOnce`** (named, R30) — HTTP-layer, real filter chain.
2. **`shouldListAndRevokeOwnApiKeys`** (named, R34/R35) — HTTP-layer, real filter chain.
3. Non-`MERCHANT`/unconfirmed-MFA caller on `POST /api-keys` → 403 (`ApiKeyNotAuthorizedException` mapping) — plain unit test on `ApiKeyExceptionHandler` plus an integration-layer check.
4. Non-`ACTIVE` account on `POST /api-keys` → 409 with detail (confirms D6, not a regression) — integration-layer.
5. Blank name / 101-char name on `POST /api-keys` → 400, no rejected value echoed — can be proven at the controller-unit level (`@Valid` triggers before the method body runs in a real dispatch) or the integration level; **plan: integration-level**, since a Mockito-direct-construction unit test bypasses Spring's validation machinery entirely (the same reason `AccountControllerTest` never tests `@Valid` itself).
6. `POST /api-keys` response contains no 64-hex-character string and no field beyond `plaintextKey` holding key material — integration-layer, string-level assertion on the raw JSON body (mirrors `ApiKeyExchangeIntegrationTest`'s `responseEnvelopeHasExactlyTheThreeExpectedFieldsAndNoSecretMaterial` technique).
7. `GET /api-keys` with zero keys → 200, empty array — integration-layer.
8. `GET /api-keys` never returns another account's keys — integration-layer (two accounts, each creates a key, assert isolation).
9. `GET /api-keys` response items contain no hash-shaped field — integration-layer, same string-level technique as #6.
10. `DELETE` of an unowned key vs. a genuinely nonexistent UUID → byte-identical 404 — integration-layer, body comparison (mirrors `ApiKeyExchangeIntegrationTest.shouldRejectRevokedOrUnknownApiKeyWithUniform401`'s technique).
11. `DELETE` of an already-revoked key → 204 — integration-layer.
12. `POST /api-keys` response has no `Location` header — integration-layer, assert `response.getHeaders().getLocation()` is null.
13. Regression: `ArchitectureTest`, `ApiKeyServiceIntegrationTest`, and every T25 `apikey`/`token` test (not new tests — re-run).

**Unit-level (Docker-independent, plain JUnit + Mockito, mirroring `ApiKeyControllerTest`'s established style):**
- `ApiKeyController.create`/`list`/`revoke` each correctly derive `accountUuid` from `Authentication` and pass it straight through to the corresponding `ApiKeyService` method (argument-capture verification, same pattern as T25's `ApiKeyControllerTest`).
- `create` returns 201 with the exact `CreateApiKeyResult` the mocked service returned.
- `revoke` returns 204 with an empty body.
- `ApiKeyExceptionHandler.onNotFound`/`onNotAuthorized` — status/type/title/no-detail assertions, mirroring `ApiKeyExceptionHandlerTest`'s existing shape for `onExchangeRejected`.

**Integration-level (`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`, mirroring `ApiKeyExchangeIntegrationTest`):** items 1, 2, 4–12 above. Given how closely this mirrors T25's own integration test in setup (same `seedMerchantWithConfirmedMfa` helper shape, same `TestRestTemplate` pattern), **plan: a new `ApiKeyCrudIntegrationTest` class**, not an extension of `ApiKeyExchangeIntegrationTest` — keeps each task's test file self-contained and independently reviewable, matching this pipeline's one-file-per-task-unless-trivial convention (e.g. T25 added a new file rather than extending `ApiKeyServiceIntegrationTest`).

---

## Execution Order

1. **`common/ProblemTypes.java`** — add the two new URI constants first; nothing downstream compiles meaningfully without them.
2. **`apikey/dto/CreateApiKeyRequest.java`** — no dependencies beyond `jakarta.validation`, needed by both the controller and its test.
3. **`apikey/ApiKeyExceptionHandler.java`** — add the two new mappings; depends only on step 1's constants and the pre-existing `ApiKeyNotFoundException`/`ApiKeyNotAuthorizedException`.
4. **`apikey/ApiKeyController.java`** — add `create`/`list`/`revoke`; depends on steps 2–3 existing plus the pre-existing `ApiKeyService`.
5. **Tests**, in this order: unit tests for the exception handler (can start right after step 3) → unit tests for the controller (after step 4) → `ApiKeyCrudIntegrationTest` (after step 4, full stack).
6. **Full suite run** (Docker permitting; otherwise Docker-independent subset only, per this session's established constraint) before declaring Phase 6 complete.

---

## Traceability Check

Every file above appears in the frozen brief's Files to Create / Files to Modify lists exactly. No file outside that set is planned. `apikey/dto/ApiKeyResponse.java` is deliberately absent, per D2/D3.

---

**Phase 5 complete — plan written.** Proceed to Phase 6 (Implementation) on approval.
