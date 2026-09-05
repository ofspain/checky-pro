<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T28 · Phase 11 — Test Review

Consumes the Phase 10 test manifest and the actual test files in `services/auth/src/test/java/com/themistra/auth/token/` and `.../account/AccountControllerTest.java`. Findings only — no production or test code changes in this phase.

---

## Executive Summary

The Phase 10 test suite is well-structured and covers the core T28 acceptance criteria. All Docker-independent tests pass (`SessionServiceTest`: 11/11, `SessionExceptionHandlerTest`: 2/2, `AccountControllerTest`: 18/18). `SessionIntegrationTest` is written but cannot be executed in this environment due to the same Docker/Testcontainers unavailability noted in Phase 10.

The gaps below are concentrated in boundary/edge coverage and in a few places where assertions are weaker than the requirement they map to. None appear to indicate a production defect, but adding the suggested tests would close holes against the frozen brief's Required Tests list and D1–D3 decisions.

---

## Findings

### Gap 1 — `revokeAll` failure coverage is limited to authorization-lookup failure

**Why it matters:** D3 states that a failure on any one family — "revoke, authorization removal, or audit" — must be logged and the loop must continue. The current `SessionServiceTest.revokeAllContinuesPastAFailureOnOneFamily` only exercises the `authorizationService.findById(...)` throw path. It does not prove the same behavior when `familyRepository.save(...)` or `auditService.record(...)` throws inside `revokeFamily`.

**Suggested test:** Add two focused tests (or a parameterized test) that stub `findById(...)` to succeed but make `familyRepository.save(...)` and `auditService.record(...)` throw, respectively, and assert that the other family in the list is still revoked and audited. This directly covers the "revoke" and "audit" failure modes called out in D3.

**Evidence:** `token/SessionServiceTest.java:179-194`.

---

### Gap 2 — `list` response mapping and active-only filtering are under-asserted

**Why it matters:** R36/AC2 requires each listed session to expose `familyId`, `deviceLabel`, `createdAt`, and `rotatedAt`. `listMapsActiveFamiliesToResponses` only asserts `familyId`; a broken `SessionResponse.from` that returns nulls for the other three fields would pass. Additionally, there is no test proving that revoked families are excluded from the result, even though the repository query name implies it.

**Suggested test:** Expand `listMapsActiveFamiliesToResponses` to assert all four fields are populated from the source family (including a non-null `deviceLabel` case and the expected-null case). Add `listExcludesRevokedFamilies` that returns one active and one revoked family from a stubbed repository and asserts only the active one appears.

**Evidence:** `token/SessionServiceTest.java:70-80`.

---

### Gap 3 — `SessionExceptionHandlerTest` "identical regardless of construction site" is tautological

**Why it matters:** `onNotFoundResponseIsIdenticalRegardlessOfConstructionSite` constructs both exceptions the same way (`new SessionNotFoundException()`). Because the exception carries no state, the test cannot fail even if the handler later started including exception-derived data. It therefore does not meaningfully verify the "uniform response regardless of cause" guarantee beyond what `onNotFoundReturnsUniform404` already proves.

**Suggested test:** Either remove this duplicate test, or — if the intent is to guard against future exception-state leakage — simulate the two different construction sites by creating helper methods that throw `SessionNotFoundException` from different call stacks and assert the resulting `ProblemDetail` bodies are equal. As long as the exception remains stateless, removal is the cleaner option.

**Evidence:** `token/SessionExceptionHandlerTest.java:31-39`.

---

### Gap 4 — Integration test suite lacks the D3 partial-failure boundary test

**Why it matters:** The frozen brief explicitly lists as a boundary test: "bulk revoke where one family's processing fails but others still succeed." `SessionIntegrationTest` covers empty bulk revoke and normal bulk revoke, but not a mixed success/failure scenario.

**Suggested test:** Add `shouldContinueRevokeAllWhenOneFamilyFails` (or similar). Seed two families for the same account, arrange for one family's authorization removal to fail (e.g., by persisting a family whose `authorizationId` triggers an exception in `OAuth2AuthorizationService.remove`, or by using a test double if feasible), and assert that the other family's authorization is still removed and its row still revoked.

**Evidence:** `token/SessionIntegrationTest.java:135-151` and the manifest's boundary list.

---

### Gap 5 — Integration tests do not assert that the family row is actually marked revoked

**Why it matters:** R37/R38 require both (a) removal of the live SAS authorization and (b) marking the family revoked. The current tests prove (a) via `authorizationService.findById(...)` and infer (b) indirectly because `GET /accounts/me/sessions` returns an empty list. However, `list` queries `revokedAt IS NULL`; if the authorization were removed but the family row were never saved as revoked, the list could still be empty for a different reason. A bug that removed the authorization but failed to persist `revokedAt` would pass.

**Suggested test:** After `shouldRevokeSingleSessionFamily` and `shouldRevokeAllSessionFamilies`, load the `RefreshTokenFamily` row(s) directly via `entityManager` and assert `revokedAt` is non-null and `revokedReason` matches `"USER_REVOKED"` / `"USER_REVOKED_ALL"`.

**Evidence:** `token/SessionIntegrationTest.java:119-151`.

---

### Gap 6 — Integration tests do not verify audit row creation

**Why it matters:** R43 requires every revoke to be audited exactly once per family. The current integration tests verify HTTP status and authorization removal but never inspect `auth_audit` or the outbox mirror.

**Suggested test:** After successful single and bulk revokes, query `auth_audit` (or the outbox table if the project has a test helper) and assert one row with `event_type = 'session.revoked'`, `outcome = 'SUCCESS'`, and `account_uuid`/`actor_uuid` equal to the caller's UUID. For bulk revoke, assert the row count equals the number of families.

**Evidence:** `token/SessionIntegrationTest.java` — no audit assertions in any test.

---

### Gap 7 — `revokeOfUnownedAndNonexistentFamilyAreByteIdentical` compares maps, not bytes

**Why it matters:** The test name promises byte-identical 404 responses, but the assertion compares deserialized `Map<String, Object>` instances with `isEqualTo`. Map equality is order-independent and ignores whitespace, so the test passes even if the raw JSON bodies differ in key order or formatting. More importantly, it does not assert that the responses share the same `type`, `title`, and absence of `detail` in a way that would catch a handler leaking differing `instance` values or extra properties.

**Suggested test:** Compare the raw response body strings directly (or JSON-normalized trees) and assert equality, then additionally assert `type`, `title`, and the absence of `detail` explicitly.

**Evidence:** `token/SessionIntegrationTest.java:163-176`.

---

## Non-Issues Confirmed

- **Unit-test pass rate:** All Docker-independent T28 tests pass in this environment (31/31 across `SessionServiceTest`, `SessionExceptionHandlerTest`, and `AccountControllerTest`).
- **Integration-test environment gap:** `SessionIntegrationTest` fails only at Testcontainers startup (`ApplicationContext failure`) due to Docker unavailability, consistent with prior phases. No compile or logic error is evident from static review.
- **Cross-module test placement:** Keeping `SessionIntegrationTest` in `token` while exercising `AccountController` endpoints is correct per the frozen brief and avoids importing `RefreshTokenFamily` from `account` test code.
- **Authentication approach in integration tests:** Using `ApiKeyTokenIssuer` to mint real JWTs for test accounts is consistent with T25/T26 integration tests and valid for endpoints that require only authentication, not a specific authority.

---

## Traceability Summary

| Frozen Brief Requirement / Decision | Covered By | Gap |
|---|---|---|
| R36 / AC1 — list own active sessions | `SessionServiceTest.listMapsActiveFamiliesToResponses`, `SessionIntegrationTest.shouldListActiveSessions` | Gap 2 (field-level mapping, active-only filtering) |
| R36 / AC2 — response fields | `SessionIntegrationTest.shouldListActiveSessions` | Gap 2 (unit-level coverage) |
| R37 / AC3 — uniform 404, idempotent revoke | `SessionServiceTest.revokeOneThrowsWhenFamilyNotFoundOrNotOwned`, `SessionServiceTest.revokeOneOnAlreadyRevokedFamilyDoesNotThrow`, `SessionIntegrationTest.revokeOfUnownedAndNonexistentFamilyAreByteIdentical`, `SessionIntegrationTest.revokeOfAlreadyRevokedFamilyReturns204Again` | Gap 7 (byte-identical assertion) |
| R37 / AC4 — remove live authorization | `SessionServiceTest.revokeOneRevokesFamilyRemovesAuthorizationAndAudits`, `SessionIntegrationTest.shouldRevokeSingleSessionFamily` | Gap 5 (family row revocation) |
| R38 / AC5 — bulk revoke | `SessionServiceTest.revokeAllContinuesPastAFailureOnOneFamily`, `SessionIntegrationTest.shouldRevokeAllSessionFamilies` | Gap 1 (other failure modes), Gap 4 (integration partial failure) |
| R43 / AC6 — audit | `SessionServiceTest.revokeOneRevokesFamilyRemovesAuthorizationAndAudits`, `SessionServiceTest.revokeAllAuditsEachFamilyIndependently` | Gap 6 (integration audit verification) |
| R46 / AC7 — no detail | `SessionExceptionHandlerTest.onNotFoundReturnsUniform404`, `SessionIntegrationTest.rejectionBody` | Gap 3 (tautological test) |
| D1 — no `revokedAt` filter on lookup | `SessionServiceTest.revokeOneOnAlreadyRevokedFamilyDoesNotThrow` | None |
| D2 — null authorization is no-op | `SessionServiceTest.revokeOneTreatsNullAuthorizationAsNoOp`, `SessionIntegrationTest.revokeWhenAuthorizationAlreadyGoneSucceeds` | None |
| D3 — per-family independence | `SessionServiceTest.revokeAllContinuesPastAFailureOnOneFamily` | Gap 1 |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval, or to Phase 9 (Review Resolution) if any of the above gaps are accepted for fixing.
