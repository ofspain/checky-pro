<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T27 · Phase 5 — Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN, approved 2026-08-15). No code written in this phase.

---

## Files to Create

### `apikey/ApiKeyLifecycleIntegrationTest.java`

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestcontainersConfiguration.class)`, mirroring `ApiKeyExchangeIntegrationTest`/`ApiKeyCrudIntegrationTest`'s established shape exactly (same helper style, same seeding discipline, same `TestRestTemplate` usage — no shared base class, per this module's established one-file-independent-implementation convention).

**Public methods (test methods):**
```java
@Test
void shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle() throws Exception
```
The single required test (D-resolved OQ1). One method, eight sequential steps per the frozen brief's Scope:
1. `POST /api-keys` with a seeded merchant's bearer token — 201, capture `keyUuid` and `plaintextKey`.
2. `GET /api-keys` — find the created item by `keyUuid`, assert `lastUsedAt` is null.
3. `POST /api-keys/token` with `Authorization: ApiKey <plaintextKey>` — 200; decode the JWT; assert `sub` = the seeded account's UUID, `scope` contains `merchant.api`, `amr` contains `api_key` (AC1, L8 — not exhaustive L9 coverage, that's `ApiKeyTokenIssuerTest`'s job).
4. `GET /api-keys` again — assert `lastUsedAt` is now non-null (AC5/D1).
5. `DELETE /api-keys/{keyUuid}` — 204 (AC-implicit, R35).
6. `GET /api-keys` again — assert `revokedAt` is now non-null (AC5/D1).
7. `POST /api-keys/token` again, same `plaintextKey` — 401; capture the raw response body.
8. `POST /api-keys/token` with a deliberately malformed key (`"ApiKey not-a-valid-key-shape"`) on the same running server — 401; assert its body is byte-for-byte equal to step 7's body (AC3/D2).

No other test methods — this is a single-scenario task; adding independent boundary tests here would duplicate `ApiKeyExchangeIntegrationTest`/`ApiKeyCrudIntegrationTest`'s existing coverage, which the frozen brief's Scope explicitly excludes.

**Private methods (helpers, mirroring existing sibling files' naming exactly for consistency):**
- `private String baseUrl()` — lazy `"http://localhost:" + port`.
- `private ResponseEntity<String> postCreate(String bearer, String jsonBody)`
- `private ResponseEntity<String> get(String bearer, String path)`
- `private ResponseEntity<String> delete(String bearer, String path)`
- `private ResponseEntity<String> postToken(String authorizationHeaderValue)` — for the `/api-keys/token` calls, which use the `ApiKey` scheme, not `Bearer` (D4 of T25's frozen brief, unrelated to this task's own D4).
- `private JsonNode readJson(ResponseEntity<String> response)`
- `private String bearerTokenFor(UUID accountUuid)` — `apiKeyTokenIssuer.issue(accountUuid, List.of("merchant.api")).accessToken()`, exactly T26's established technique (this task's own D4 confirms this is intended, not merely convenient).
- `private UUID seedMerchantWithConfirmedMfa(String email)` — register → activate → assign `MERCHANT` → begin/confirm TOTP (independently reimplemented, matching every sibling file).
- `private UUID registerAndActivate(String email)`
- `private void ensureRoleExists(String roleName)`
- `private static String referenceGenerateCode(byte[] secret, Instant now)` — independent RFC 6238 implementation, matching every sibling file's own copy.
- `private JsonNode findByKeyUuid(JsonNode listResponse, String keyUuid)` — a new small helper specific to this file: scans the `GET /api-keys` array for the item matching the created key's UUID, since the frozen brief's flow needs to read `lastUsedAt`/`revokedAt` off a specific item, not just assert list size.

## Entities Used

None — no direct entity access; every observation goes through the HTTP responses.

## Repositories Used

None.

## Services Used

- `ApiKeyTokenIssuer` (autowired, T25) — solely to mint the bearer JWT authenticating the `POST`/`GET`/`DELETE` calls (D3: this is a real, documented dependency — if `ApiKeyTokenIssuer`/`JwtEncoder` is broken, T27 fails for that reason, not its own logic).
- `AccountService`, `RoleService`, `MfaService` (autowired) — merchant + MFA seeding only, same as every sibling integration test.

## Tests Required

Exactly the one test method described above. No unit-level tests — this task is inherently full-stack by its own task statement ("with Testcontainers"); there is no pure-logic slice to test in isolation, since T27 adds no production code.

## Execution Order

1. Write `apikey/ApiKeyLifecycleIntegrationTest.java` in full (single file, single test method, no dependencies on any other new file).
2. Compile (`mvn -pl services/auth -am test-compile`) to confirm correctness against the real `ApiKeyService`/`ApiKeyController`/`ApiKeyTokenIssuer` signatures.
3. Run (Docker permitting; otherwise document as written-but-unexecuted, per this session's established constraint) alongside a Docker-independent regression check of any unit test suite that might be affected (none expected, since no production code changes).

---

## Traceability Check

The single planned file appears in the frozen brief's Files to Create list exactly. No file outside that set is planned. No modification to any existing file.

---

**Phase 5 complete — plan written.** Proceed to Phase 6 (Implementation) on approval.
