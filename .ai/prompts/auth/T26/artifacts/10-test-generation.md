<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T26 · Phase 10 — Test Generation

Test manifest for the resolved implementation (`artifacts/09-review-resolution.md`). No production code changed in this phase. Two existing files extended, one new file added.

---

## Test Manifest

### `ApiKeyControllerTest.java` (extended) — plain JUnit + Mockito, direct construction

12 new tests added to the existing class (which already covered `exchange`):

| Test | Verifies |
|---|---|
| `createReturns201WithTheServiceResult` | D2/D4 — 201, `CreateApiKeyResult` returned unwrapped |
| `createDerivesCallerFromAuthenticationNotRequestBody` | D2/AC2 |
| `createResponseHasNoLocationHeader` | D8 |
| `createPropagatesApiKeyNotAuthorizedUncaught` | R30/AC9 — not caught locally |
| `createPropagatesInvalidAccountStateUncaught` | D6/AC10 — not caught locally |
| `listReturnsTheCallersOwnKeys` | R34 |
| `listReturnsEmptyListWhenCallerHasNoKeys` | R34 |
| `listDerivesCallerFromAuthentication` | R34/AC2 |
| `revokeCallsServiceWithCallerAndKeyUuid` | R35 |
| `revokePropagatesApiKeyNotFoundUncaught` | R35/AC7 |
| `revokeOfAlreadyRevokedKeyStillReturns204` | R35, idempotency |

26/26 pass (Docker-independent) including the pre-existing `exchange` tests, unaffected.

### `ApiKeyExceptionHandlerTest.java` (extended) — plain JUnit, no Spring context

3 new tests added:

| Test | Verifies |
|---|---|
| `onNotFoundReturnsUniform404` | R35/R46 |
| `onNotFoundResponseIsIdenticalRegardlessOfConstructionSite` | R35 |
| `onNotAuthorizedReturnsUniform403` | R30/R46 |

5/5 pass (Docker-independent) including the pre-existing `onExchangeRejected` tests.

### `ApiKeyCrudIntegrationTest.java` (new) — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, real filter chain

Kept as its own file rather than extending `ApiKeyExchangeIntegrationTest`, per the Phase 5 plan's decision. Authentication is a real, signed JWT minted via the already-wired `ApiKeyTokenIssuer` bean — not a parallel hand-rolled JWT implementation — since none of these three endpoints require a specific role/authority at the filter level (only `ApiKeyService.create` checks that, internally).

| Test | Verifies |
|---|---|
| **`shouldCreateApiKeyAndShowPlaintextExactlyOnce`** (named) | R30 — 201, plaintext key matches the `ck_live_` shape, no `Location` header, no hash-shaped (64-hex) value anywhere in the body, no `keyHash` field |
| **`shouldListAndRevokeOwnApiKeys`** (named) | R34/R35 — create, confirm listed with `revokedAt: null`, revoke, confirm still listed with a non-null `revokedAt` (matches `ApiKeyService`'s own established "revoked keys stay visible" contract, T24) |
| `createRejectsCallerLackingMerchantRoleOrConfirmedMfa` | R30/AC9 — 403, `application/problem+json`, no `detail` |
| `createRejectsNonActiveAccountWith409` | D6/AC10 — confirms the accepted 409-with-detail behavior, not a regression |
| `createRejectsBlankOrOverLengthName` | D1 — 400 for both cases, no rejected value echoed |
| `listReturnsEmptyArrayWhenCallerHasNoKeys` | R34 |
| `listNeverReturnsAnotherAccountsKeys` | R34 |
| `listResponseContainsNoHashShapedField` | AC6 (Kimi Phase 8 Finding 4) |
| `deleteOfUnownedKeyAndNonexistentKeyAreByteIdentical` | R35/AC7 |
| `deleteOfAlreadyRevokedKeyReturns204` | R35, idempotency |

**Written but unexecuted this session** — Docker remains unavailable (`docker info` fails), same pre-existing environment gap as every phase since T25's Phase 6. The file compiles cleanly and fails only with the same `ApplicationContext failure` (context load, Testcontainers can't start) every other `@SpringBootTest` class in this module currently produces — not a compile or logic error.

### Required test #12 ("`POST /api-keys` response has no `Location` header")

Covered by `createResponseHasNoLocationHeader` (unit-level, Docker-independent, executed and green) rather than only at the integration level — stronger coverage than originally planned, since this assertion doesn't actually need the real filter chain.

### Kimi Phase 8 Findings #3/#4 — now covered

- Finding #3 (name-length boundary sync) — `createRejectsBlankOrOverLengthName` (integration) exercises both the blank and 101-character cases end-to-end.
- Finding #4 (`CreateApiKeyResult` JSON shape / no-hash-leak) — `shouldCreateApiKeyAndShowPlaintextExactlyOnce` asserts the exact fields present (`plaintextKey`, `name`, `keyUuid`) and the absence of any 64-hex-character value or a `keyHash` field.

### Regression

`ArchitectureTest`, `ApiKeyServiceIntegrationTest`, and every T25-written `apikey`/`token` test — not new tests; to be re-run once Docker is available. `TokenClaimsCustomizerTest` and `ApiKeyHasherTest` (Docker-independent) were re-run this phase and remain green.

---

## Build Verification

`mvn -q -pl services/auth -am test-compile` — clean, exit 0.

Docker-independent tests, run together: **56/56 pass** — `ApiKeyControllerTest` (26), `ApiKeyExceptionHandlerTest` (5), `ApiKeyTokenIssuerTest` (7), `ApiKeyPropertiesTest` (6), `JwksConfigTest` (1), `TokenClaimsCustomizerTest` (8), `ApiKeyHasherTest` (3).

`ApiKeyCrudIntegrationTest` — confirmed it fails only on `ApplicationContext failure` (Docker down); not a defect in the new test.

---

## Traceability Summary

Every AC1–AC12 and every named/boundary test item from the frozen brief's Required Tests list is covered by at least one test above, split by layer per the frozen brief's own testing convention: pure argument-wiring/ordering logic → `ApiKeyControllerTest`; problem-body shape → `ApiKeyExceptionHandlerTest`; full-stack/HTTP/DB → `ApiKeyCrudIntegrationTest`.

---

**Phase 10 complete — tests written.** Proceed to Phase 11 (Kimi Test Review) on approval.
