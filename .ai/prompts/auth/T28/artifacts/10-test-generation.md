<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T28 · Phase 10 — Test Generation

Test manifest for the resolved implementation (`artifacts/09-review-resolution.md`). No production code changed in this phase. One existing test file extended, four new test files added.

---

## Test Manifest

### `token/SessionServiceTest.java` (new) — plain JUnit + Mockito, fixed `Clock`, no Spring context

Directly verifies the frozen brief's D1–D5 behaviors, most importantly the load-bearing revocation ordering documented on `SessionService`'s own class Javadoc.

| Test | Verifies |
|---|---|
| `listMapsActiveFamiliesToResponses` | R36 |
| `listReturnsEmptyListWhenNoActiveSessions` | R36 |
| `revokeOneRevokesFamilyRemovesAuthorizationAndAudits` | R37, R43 — the full happy path in one assertion set |
| `revokeOneThrowsWhenFamilyNotFoundOrNotOwned` | R37/AC3 — no enumeration oracle |
| `revokeOneTreatsNullAuthorizationAsNoOp` | D2 |
| `revokeOneOnAlreadyRevokedFamilyDoesNotThrow` | D1 — idempotency doesn't overwrite the original reason/timestamp |
| `revokeOneRemovesAuthorizationBeforeMarkingFamilyRevoked` | The ordering itself — asserts `family.isRevoked()` is still `false` *at the moment* the authorization lookup happens |
| `revokeOneDoesNotMarkFamilyRevokedWhenAuthorizationRemovalFails` | The failure-direction half of the same ordering guarantee |
| `revokeAllContinuesPastAFailureOnOneFamily` | D3 — the core bulk-revoke guarantee |
| `revokeAllSucceedsTriviallyWithZeroSessions` | R38 |
| `revokeAllAuditsEachFamilyIndependently` | R43 — one row per family, not one row per bulk call |

11/11 pass (Docker-independent). **Implementation note from this phase:** an early version constructed `SessionService` as a field initializer rather than in `@BeforeEach`, which silently captured `null` collaborators (Mockito's `@Mock` injection runs after field initializers, not before) — caught immediately by the resulting `NullPointerException`s on first run, fixed before this manifest was written.

### `token/SessionExceptionHandlerTest.java` (new) — plain JUnit, no Spring context

| Test | Verifies |
|---|---|
| `onNotFoundReturnsUniform404` | R37, R46 |
| `onNotFoundResponseIsIdenticalRegardlessOfConstructionSite` | R37 |

2/2 pass.

### `account/AccountControllerTest.java` (extended) — plain JUnit + Mockito

4 new tests added to the existing file (which already covered `register`/`me`/`verifyEmail`/etc.):

| Test | Verifies |
|---|---|
| `listSessionsReturnsTheCallersOwnSessions` | R36/AC2 |
| `revokeSessionCallsServiceWithCallerAndFamilyId` | R37/AC2 |
| `revokeSessionPropagatesSessionNotFoundUncaught` | R37/AC3 — not caught locally |
| `revokeAllSessionsCallsServiceWithCaller` | R38/AC2 |

18/18 pass (including the pre-existing tests, unaffected — this file's earlier 14 tests were already updated at Phase 6 for the constructor-signature change).

### `token/SessionIntegrationTest.java` (new) — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, real filter chain

Lives in `token`, not `account`, despite exercising `AccountController`'s endpoints — constructing `RefreshTokenFamily` fixtures directly (via its public factory + a raw `EntityManager`, since the repository is package-private) is a same-package operation there, avoiding any cross-module entity reference even in test code. Authenticates via a real JWT minted through `ApiKeyTokenIssuer` (T26's established technique).

| Test | Verifies |
|---|---|
| **`shouldListActiveSessions`** (named) | R36 — own sessions only, all four fields present, cross-account isolation |
| **`shouldRevokeSingleSessionFamily`** (named) | R37 — a **real** `OAuth2Authorization`, saved via the actual `OAuth2AuthorizationService` bean, is confirmed gone via `findById(...)` returning `null` afterward — not just inferred from the family row |
| **`shouldRevokeAllSessionFamilies`** (named) | R38 — same real-authorization proof, for two families at once |
| `listReturnsEmptyArrayWhenCallerHasNoSessions` | R36 |
| `revokeOfUnownedAndNonexistentFamilyAreByteIdentical` | R37/AC3 |
| `revokeOfAlreadyRevokedFamilyReturns204Again` | D1 |
| `revokeWhenAuthorizationAlreadyGoneSucceeds` | D2, at the HTTP layer |
| `revokeAllSucceedsTriviallyWithNoSessions` | R38 |

**Written but unexecuted this session** — Docker remains unavailable (`docker info` fails), the same pre-existing environment gap as T25/T26/T27's own Phase 10s. The file compiles cleanly and all 8 tests fail only with the same `ApplicationContext failure` (Testcontainers can't start) every other `@SpringBootTest` class in this module currently produces — not a compile or logic error.

### Regression

`ArchitectureTest`, `ApiKeyServiceIntegrationTest`, and every T25/T26/T27-written test — not new tests; to be re-run once Docker is available. `ReuseDetectingAuthorizationServiceTest` (Docker-independent, fixed during Phase 8/9's review of this task) was re-run this phase and remains green at 8/8.

---

## Build Verification

`mvn -q -pl services/auth -am clean test-compile` — clean, exit 0.

Docker-independent tests, run together: **95/95 pass** — `AccountControllerTest` (18), `ApiKeyControllerTest` (26), `ApiKeyExceptionHandlerTest` (5), `ApiKeyTokenIssuerTest` (7), `ApiKeyPropertiesTest` (6), `JwksConfigTest` (1), `TokenClaimsCustomizerTest` (8), `ApiKeyHasherTest` (3), `SessionServiceTest` (11), `SessionExceptionHandlerTest` (2), `ReuseDetectingAuthorizationServiceTest` (8).

`SessionIntegrationTest` — confirmed all 8 tests fail only on `ApplicationContext failure` (Docker down); not a defect in the new tests.

---

## Traceability Summary

Every AC1–AC8 and every named/boundary test item from the frozen brief's Required Tests list is covered, split by layer per this module's established convention: pure logic/ordering → `SessionServiceTest`; problem-body shape → `SessionExceptionHandlerTest`; caller-derivation/status codes → `AccountControllerTest`; full-stack/HTTP/DB, including the real-authorization-removal proof → `SessionIntegrationTest`.

---

**Phase 10 complete — tests written.** Proceed to Phase 11 (Kimi Test Review) on approval.
