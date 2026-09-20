<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T33 · Phase 5 — Implementation Plan

No production code. Plan covers the 3 contract files, 3 new test files, and the 1 pom.xml
dependency the frozen brief authorizes. The exhaustive DTO/response inventory Kimi's Phase 3
Finding 2 demanded (and Phase 4 required before authoring) was built by reading all 7 controllers
and every DTO they reference in full — recorded below so Phase 6 authors from it directly rather
than re-deriving it.

## Exhaustive endpoint → schema inventory (closes Finding 2)

| # | Method + Path | Request body | Success response | Status |
|---|---|---|---|---|
| 1 | `POST /accounts` | `RegisterAccountRequest` | `RegistrationAcknowledgement` | 202 |
| 2 | `GET /accounts/me` | — | `AccountResponse` | 200 |
| 3 | `POST /accounts/verify-email` | `VerifyEmailRequest` | — (empty) | 204 |
| 4 | `POST /accounts/resend-verification` | `ResendVerificationRequest` | `RegistrationAcknowledgement` | 200 |
| 5 | `POST /accounts/password-reset-request` | `PasswordResetRequest` | `RegistrationAcknowledgement` | 200 |
| 6 | `POST /accounts/password-reset` | `PasswordResetConfirmRequest` | — (empty) | 204 |
| 7 | `POST /accounts/me/password` | `ChangePasswordRequest` | — (empty) | 204 |
| 8 | `GET /accounts/me/sessions` | — | `SessionResponse[]` | 200 |
| 9 | `DELETE /accounts/me/sessions/{familyId}` | — | — (empty) | 204 |
| 10 | `DELETE /accounts/me/sessions` | — | — (empty) | 204 |
| 11 | `GET /admin/accounts/{accountUuid}` | — | `AccountResponse` | 200 |
| 12 | `POST /admin/accounts/{accountUuid}/activate` | — | `AccountResponse` | 200 |
| 13 | `POST /admin/accounts/{accountUuid}/suspend` | — | `AccountResponse` | 200 |
| 14 | `POST /admin/accounts/{accountUuid}/reinstate` | — | `AccountResponse` | 200 |
| 15 | `DELETE /admin/accounts/{accountUuid}` | — | `AccountResponse` | 200 |
| 16 | `POST /admin/accounts/{accountUuid}/unlock` | — | `AccountResponse` | 200 |
| 17 | `POST /api-keys` | `CreateApiKeyRequest` | `ApiKeyService.CreateApiKeyResult` | 201 |
| 18 | `GET /api-keys` | — | `ApiKeyService.ApiKeyMetadata[]` | 200 |
| 19 | `DELETE /api-keys/{keyUuid}` | — | — (empty) | 204 |
| 20 | `POST /api-keys/token` | — (header-only credential) | `ApiKeyTokenResponse` | 200 |
| 21 | `GET /admin/accounts/{accountUuid}/roles` | — | `Set<String>` (inline array) | 200 |
| 22 | `POST /admin/accounts/{accountUuid}/roles/{roleName}` | — | — (empty) | 204 |
| 23 | `DELETE /admin/accounts/{accountUuid}/roles/{roleName}` | — | — (empty) | 204 |
| 24 | `POST /admin/accounts/{accountUuid}/role-templates/{templateName}` | — | — (empty) | 204 |
| 25 | `DELETE /admin/accounts/{accountUuid}/role-templates/{templateName}` | — | — (empty) | 204 |
| 26 | `POST /admin/roles` | `CreateRoleRequest` | `RoleResponse` | 201 |
| 27 | `GET /admin/roles` | — | `RoleResponse[]` | 200 |
| 28 | `POST /admin/role-templates` | `CreateRoleTemplateRequest` | `RoleTemplateResponse` | 201 |
| 29 | `GET /admin/role-templates` | — | `RoleTemplateResponse[]` | 200 |
| 30 | `GET /admin/audit` | — (query params `accountUuid?`, `page`, `size`, `sort`) | `Page<AuditEventResponse>` (inline) | 200 |

**Distinct component schemas needed** (13, not 30 — most endpoints share a shape):
`RegistrationAcknowledgement`, `AccountResponse`, `SessionResponse`, `ApiKeyCreateResult` (mapped
from `ApiKeyService.CreateApiKeyResult`), `ApiKeyMetadata`, `ApiKeyTokenResponse`, `RoleResponse`,
`RoleTemplateResponse`, `AuditEventResponse`, and the 6 request bodies
(`RegisterAccountRequest`, `VerifyEmailRequest`, `ResendVerificationRequest`,
`PasswordResetRequest`, `PasswordResetConfirmRequest`, `ChangePasswordRequest`,
`CreateApiKeyRequest`, `CreateRoleRequest`, `CreateRoleTemplateRequest` — 9 request bodies, listed
in `auth.yaml` for documentation per D2 but not verified by the conformance test per D2's own
scope).

**Field shapes** (from reading every DTO in full this phase — Phase 6 authors `auth.yaml`'s
schemas directly from these, no re-reading needed):
- `AccountResponse`: `accountUuid` (uuid), `email` (string), `emailVerified` (boolean), `status`
  (enum: `PENDING_VERIFICATION`/`ACTIVE`/`LOCKED`/`SUSPENDED`/`DELETED`), `createdAt` (date-time).
- `RegistrationAcknowledgement`: `message` (string).
- `SessionResponse`: `familyId` (uuid), `deviceLabel` (string, **nullable** — always `null` today
  per its own Javadoc, D6), `createdAt` (date-time), `rotatedAt` (date-time).
- `ApiKeyService.CreateApiKeyResult`: `keyUuid` (uuid), `plaintextKey` (string), `name` (string),
  `createdAt` (date-time).
- `ApiKeyService.ApiKeyMetadata`: `keyUuid` (uuid), `name` (string), `scopes` (string array),
  `createdAt` (date-time), `lastUsedAt`/`expiresAt`/`revokedAt` (date-time, all **nullable**).
- `ApiKeyTokenResponse`: `access_token`/`token_type` (string), `expires_in` (integer) — snake_case
  wire names via explicit `@JsonProperty`, not this service's usual camelCase (RFC 6749 §5.1).
- `RoleResponse`: `name` (string), `description` (string, nullable — no `@NotBlank` on the field).
- `RoleTemplateResponse`: `name` (string), `description` (string, nullable), `roleNames` (string
  array).
- `AuditEventResponse`: `id` (integer), `occurredAt` (date-time), `eventType` (string), `outcome`
  (enum: `SUCCESS`/`FAILURE`), `accountUuid`/`actorUuid` (uuid, nullable), `ip` (string, nullable),
  `userAgentHash` (string, nullable), `traceId` (string, nullable), `details` (object, free-form).
- `Page<AuditEventResponse>` (endpoint 30 only, D3 inline): Spring Data's actual default Jackson
  shape (`content`, `pageable`, `totalElements`, `totalPages`, `last`, `size`, `number`, `sort`,
  `numberOfElements`, `first`, `empty`) — **to be confirmed against one real serialized instance in
  Phase 6** before finalizing the inline schema, not assumed from memory of Spring Data's general
  behavior.

## Files to create

- `contracts/api/auth.yaml` — OpenAPI 3.0, one `paths` entry per row above, `components.schemas`
  for the 13 distinct shapes, generic wrappers inlined per D3.
- `contracts/events/auth/email-requested.v1.schema.json` — mirrors
  `user-lifecycle.v1.schema.json`'s exact shape: `$id
  https://checky.pro/contracts/events/auth/email-requested.v1.schema.json`, `required: [accountUuid,
  purpose, token, occurredAt]` (all 4 fields are non-null per `EmailRequestedEventPayload`'s
  record, no nullable fields), `purpose` documented as a free-form string with the two known values
  (`verify_email`, `password_reset`) noted in its `description` (not an `enum` — the payload's own
  Javadoc frames `purpose` as "later `password_reset`," i.e. open to more values, so a closed enum
  would misrepresent it).
- `contracts/events/auth/security-audit.v1.schema.json` — `required: [eventType, outcome,
  occurredAt]` only (`accountUuid`/`actorUuid` excluded per Phase 1's confirmed nullability);
  `outcome` as `enum: [SUCCESS, FAILURE]`.
- `services/auth/src/test/java/com/themistra/auth/account/event/EmailRequestedEventPayloadContractTest.java`
  — mirrors `UserLifecycleEventPayloadContractTest` exactly: one test serializing a real
  `EmailRequestedEventPayload` and checking required/declared fields; a second test asserting both
  known `purpose` values round-trip (no enum to check against, so this test instead documents the
  two known values directly, since the schema itself doesn't constrain them to a closed set).
- `services/auth/src/test/java/com/themistra/auth/audit/AuditMirrorPayloadContractTest.java` —
  same pattern: one test with a real `AuditMirrorPayload` (all fields populated) checking
  required/declared fields; a second test asserting every `AuditOutcome` value is covered by the
  schema's `outcome` enum (mirrors `everyAccountStatusValueIsCoveredByTheSchemaEnum` exactly).
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` — the named
  test, `shouldConformToAuthOpenApiContract`, plus its supporting private methods (below).

## Public methods (signatures)

- `AuthOpenApiContractTest.shouldConformToAuthOpenApiContract()` — `@Test`, no params, no return
  (the named test itself; D1's two halves happen inside it or as two separately-named `@Test`
  methods within the same class — Phase 6's call, both count toward the same named-test intent).

## Private methods (planned, `AuthOpenApiContractTest`)

- `loadAuthYaml()` → `JsonNode` — parses `contracts/api/auth.yaml` via
  `new YAMLMapper().findAndRegisterModules()`, same relative-path convention as the existing event
  contract test (`../../contracts/api/auth.yaml`).
- `realControllerRoutes()` → `Set<Route>` (a small local record `Route(String method, String
  path)`) — reflection over the 7 known `@RestController` classes' `@GetMapping`/`@PostMapping`/
  `@PutMapping`/`@DeleteMapping`/`@PatchMapping` annotations, combined with each class's
  `@RequestMapping` base path, normalizing Spring's `{pathVar}` syntax to OpenAPI's identical
  `{pathVar}` syntax (no translation needed — both use curly braces).
- `yamlRoutes(JsonNode authYaml)` → `Set<Route>` — walks the parsed YAML's `paths` object and its
  per-path HTTP method keys.
- `assertEveryControllerRouteIsDocumented(...)` / `assertEveryDocumentedRouteHasARealHandler(...)`
  — D1a, the two-directional completeness check.
- `assertResponseSchemaMatchesRealDto(JsonNode componentSchema, Object realInstance)` — D1b, reused
  per distinct DTO, same required/declared-fields traversal as the existing event contract test.

## Entities used

None (no persistence in this task).

## Repositories used

None.

## Services used

None directly — `AuthOpenApiContractTest` constructs DTO instances directly (`new
AccountResponse(...)`, etc.), the same way `UserLifecycleEventPayloadContractTest` constructs a
`UserLifecycleEventPayload` directly rather than going through `AccountService`.

## Unit / integration tests required

- `EmailRequestedEventPayloadContractTest` (2 tests) — plain JUnit, no Spring context.
- `AuditMirrorPayloadContractTest` (2 tests) — plain JUnit, no Spring context.
- `AuthOpenApiContractTest` (`shouldConformToAuthOpenApiContract` + however many supporting `@Test`
  methods Phase 6 splits D1a/D1b into) — plain JUnit, no Spring context, no Testcontainers
  (reflection over controller classes needs no running application).
- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` — already exists, no change.

## Execution order

1. Author `contracts/events/auth/email-requested.v1.schema.json` and
   `security-audit.v1.schema.json` (smallest, most mechanical, exact template already exists).
2. Write `EmailRequestedEventPayloadContractTest` and `AuditMirrorPayloadContractTest`; run them
   green before moving on — proves the two schema files are correct in isolation first.
3. Add the `jackson-dataformat-yaml` test dependency to `pom.xml`; confirm it resolves and a
   trivial YAML-parse compiles.
4. Author `contracts/api/auth.yaml` from the inventory table above — all 30 paths, 13 component
   schemas, generic wrappers inlined per D3. Confirm `Page`'s real serialized shape (execution
   step, not assumption) before finalizing endpoint 30's inline schema.
5. Write `AuthOpenApiContractTest`'s completeness check (D1a) first — run it against the freshly
   authored `auth.yaml` to catch any path/method typos before writing the schema-correctness half.
6. Write `AuthOpenApiContractTest`'s schema-correctness check (D1b) for all 13 components; run
   green.
7. Full `services/auth` compile + the 3 new test classes run together; confirm
   `EventTopicsTest.shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` (pre-existing,
   untouched) still passes as a regression check.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
