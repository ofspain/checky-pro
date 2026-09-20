<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T25 · Phase 10 — Test Generation

Test manifest for the resolved implementation (`artifacts/09-review-resolution.md`). No production code changed in this phase. Four new test files, all under `src/test/java/com/themistra/auth/apikey/`.

---

## Test Manifest

### `ApiKeyTokenIssuerTest.java` — plain JUnit, fixed `Clock`, no Spring context

Signs with a real in-test-generated RSA key (mirrors `SigningKeyMaterial`'s own construction technique) rather than mocking `JwtEncoder`, so claim assembly is verified against an actually-decoded compact JWT.

| Test | Verifies |
|---|---|
| `issueProducesTheFullL9ClaimSet` | R31, L8, L9 — every claim (`iss`, `sub`, `aud`, `client_id`, `amr`, `acr`, `scope`, `roles`, `email_verified`, `jti`, `iat`/`nbf`/`exp`), and R48's "no PII beyond `email_verified`" (asserts `email`/`name` absent) |
| `issueSignsWithRs256` | AC3 |
| `issueComputesExpiryFromConfiguredTtl` | AC7 — `exp − iat` and `expiresInSeconds` both driven by the injected `Clock` + configured TTL |
| `nonDefaultTtlChangesBothExpiryAndExpiresIn` | AC7, boundary/supporting #5 — a non-default `token-ttl-minutes` changes both together |
| `issueResolvesRolesFreshOnEveryCall` | Kimi#10 — roles never cached; a role change between two calls is reflected on the second |
| `issueEchoesScopesVerbatimAsAJsonArray` | D1/Kimi#12 — `scope` is the exact JSON array passed in, unmodified |
| `issueRejectsNullAccountUuid` | Phase 9 gate fix — explicit `IllegalStateException`, not a bare NPE |

7/7 pass (Docker-independent).

### `ApiKeyControllerTest.java` — plain JUnit + Mockito, direct construction (mirrors `AccountControllerTest`'s established style; no `MockMvc` precedent exists in this module)

| Test | Verifies |
|---|---|
| `exchangeReturnsTokenResponseForAValidHeader` | R31/R32 happy path wiring |
| `exchangeIsCalledBeforeIssue` | D5 — transaction-then-sign ordering, via `InOrder` |
| `signingFailurePropagatesUncaught` | D5/AC15 — not caught locally, surfaces for `ApiExceptionHandler` |
| `missingHeaderPassesNullToExchange` | R33/AC10 — no header → the single audited malformed path |
| `wrongSchemePassesNullToExchange` | D4 — a literal `Bearer` header, if it ever reached the controller |
| `completelyUnrecognizedSchemePassesNullToExchange` | R33 — e.g. `Basic` |
| `headerWithNoSeparatorPassesNullToExchange` | R33 — no space between scheme and credential |
| `blankCredentialPassesNullToExchange` | R33/AC10 |
| `overLengthCredentialPassesNullToExchange` | Security constraint — >256 chars, rejected before hashing |
| `exactlyMaxLengthCredentialIsPassedThrough` | boundary — 256 is inclusive |
| `schemeMatchIsCaseInsensitive` | D4 — RFC 7235 |
| `extraWhitespaceAroundCredentialIsTrimmed` | Phase 9 gate fix (Kimi finding #5) |
| `credentialCaseIsPreservedVerbatim` | D4/L7 — credential itself is case-sensitive |

13/13 pass (Docker-independent).

### `ApiKeyExceptionHandlerTest.java` — plain JUnit, no Spring context (mirrors `AccountExceptionHandlerTest`)

| Test | Verifies |
|---|---|
| `onExchangeRejectedReturnsUniform401` | R33/R46 — fixed status/type/title, no `detail`/`instance`/`properties` leakage |
| `onExchangeRejectedResponseIsIdenticalRegardlessOfConstructionSite` | R33 — byte-identical regardless of rejection cause |

2/2 pass (Docker-independent).

### `ApiKeyExchangeIntegrationTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, real filter chain (mirrors `SasLoginIntegrationTest`'s established precedent; no per-test rollback, unique email/prefix per test)

| Test | Verifies |
|---|---|
| **`shouldExchangeValidApiKeyForMerchantJwt`** (named) | R31/L8/L9/D1–D3 — full claim set on an actually-issued, actually-signed token reached through the real endpoint |
| **`shouldRejectRevokedOrUnknownApiKeyWithUniform401`** (named, HTTP layer) | R33/R46/AC10 — revoked, unknown-prefix, malformed, wrong-secret all byte-identical at the HTTP layer. **Note:** a service-layer test of the same name already exists in `ApiKeyServiceIntegrationTest` (T24) — that one proves `ApiKeyService.exchange`'s own uniformity; this one proves the full HTTP stack (controller + exception handler + filter chain) preserves it. Same name, different architectural layer, both legitimate — not a duplicate. |
| `lastUsedAtWrittenOnSuccessNeverOnRejection` | R32, boundary #3 |
| `expiredKeyRejectedUniformlyThroughTheEndpoint` | boundary #4 — the tight tie-instant boundary itself is T24-frozen service-layer behavior, already covered by `ApiKeyServiceIntegrationTest`; this proves the HTTP layer surfaces the same 401 for an expired key without introducing a different rejection path |
| `publicEndpointsRegistersApiKeysTokenAsPostOnly` | boundary #6 — CI-enforceable guard, kept in this `apikey`-scoped file rather than the shared `PublicEndpointsTest` (Phase 5 traceability decision) |
| `reachableAnonymouslyAndApiKeySchemeAvoidsTheBearerFilter` | AC1, boundary #7, D4 regression, **and the Phase 9 CSRF-fix regression** — a session-less, CSRF-token-less POST succeeding at all is the proof the CSRF fix works; also confirms a `Bearer`-schemed presentation of the same key is intercepted by the filter and does NOT get the uniform body (documented residual) |
| `missingWrongSchemeBlankAndOverLengthCredentialAllUniform401` | boundary #8 |
| `auditRecordsOneSuccessRowAndOneOutboxMirrorOnSuccess` | R43/AC12 |
| `auditRecordsOneFailureRowAndOneOutboxMirrorPerRejection` | R43/AC12 — including the account-less (`NULL` `account_uuid`) case |
| `responseEnvelopeHasExactlyTheThreeExpectedFieldsAndNoSecretMaterial` | AC11 |

Required test #10 (`ApiKeyExchangeRejectedException` → 401, not the 500 it would otherwise produce) is proven by every rejection test above reaching `ApiKeyExceptionHandler` rather than `ApiExceptionHandler.onUnexpected` — not written as a separate test.

**Written but unexecuted this session** — Docker remains unavailable (`docker info` fails), same pre-existing environment gap as every phase since Phase 6. All four new files compile cleanly (`mvn -pl services/auth -am test-compile`); running `ApiKeyExchangeIntegrationTest` fails only with the same `ApplicationContext failure` (Testcontainers can't start) every other `@SpringBootTest` class in this module currently produces — not a compile or logic error. One implementation detail flagged as unverified: native-query parameters bind `java.util.UUID` directly (`accountUuid`) against Postgres `uuid` columns — the idiomatic Hibernate 6 approach and expected to work, but not empirically confirmed against real Postgres this session.

### Required test #12 — regression list

Not new tests; reconfirm once Docker is available: `ArchitectureTest`, `PublicEndpointsTest`, `ApiKeyServiceIntegrationTest`, `TokenClaimsCustomizerTest`, `SasLoginIntegrationTest` (the last now doubly relevant — it verifies both D1's original claim and the Phase 9 `jwkSelector` fix don't disturb SAS's own grants). `TokenClaimsCustomizerTest` and `ApiKeyHasherTest` (Docker-independent) were re-run this phase and remain green.

---

## Build Verification

`mvn -q -pl services/auth -am test-compile` — clean, exit 0.

Docker-independent tests, run together: **22/22 pass** — `ApiKeyTokenIssuerTest` (7), `ApiKeyControllerTest` (13), `ApiKeyExceptionHandlerTest` (2), plus regression re-checks `TokenClaimsCustomizerTest` (8) and `ApiKeyHasherTest` (3).

`ApiKeyExchangeIntegrationTest` — confirmed it fails only on `ApplicationContext failure` (Docker down), the same class of failure as every other Testcontainers-backed test in this module; not a defect in the new test.

---

## Traceability Summary

Every AC1–AC15 and every named/boundary test item from the frozen brief's Required Tests list is covered by at least one test above, split across the layer where it's most meaningfully proven (pure logic → `ApiKeyTokenIssuerTest`; header parsing/ordering → `ApiKeyControllerTest`; problem-body shape → `ApiKeyExceptionHandlerTest`; full-stack/HTTP/DB/audit → `ApiKeyExchangeIntegrationTest`), consistent with the frozen brief's own instruction: "Pure logic (TTL arithmetic, claim assembly, header parsing) as plain JUnit with a fixed `Clock`; anything touching the schema or the filter chain as `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`."

---

**Phase 10 complete — tests written.** Proceed to Phase 11 (Kimi Test Review) on approval.
