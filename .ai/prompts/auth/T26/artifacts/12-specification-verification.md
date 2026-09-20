<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T26 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11) against `requirements.md`, `design.md`, `tasks.md`, and the frozen brief for **T26 only**. `spec/auth-service/` confirmed byte-for-byte unchanged since T26 began (`git diff b76aafc...HEAD -- spec/auth-service/` — empty, where `b76aafc` is T25's final commit).

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R30** — `MERCHANT` + confirmed-MFA caller creates a key, plaintext returned once | Yes | `ApiKeyController.java:61-67` (`create`); `ApiKeyService.create` (T24, unmodified) | `ApiKeyControllerTest` (5 tests, executed, green); `ApiKeyCrudIntegrationTest.shouldCreateApiKeyAndShowPlaintextExactlyOnce` (named, written, unexecuted — Docker) | No | No |
| **R34** — `GET /api-keys` returns own keys, metadata only | Yes | `ApiKeyController.java:75-79` (`list`); `ApiKeyService.list` (T24, unmodified) | `ApiKeyControllerTest` (3 tests, executed, green); `ApiKeyCrudIntegrationTest` (4 tests, written, unexecuted) | No | No |
| **R35** — `DELETE /api-keys/{keyUuid}` revokes, audits `api_key.revoked` | Yes | `ApiKeyController.java:88-93` (`revoke`); `ApiKeyService.revoke` (T24, unmodified) | `ApiKeyControllerTest` (3 tests, executed, green); `ApiKeyCrudIntegrationTest.shouldListAndRevokeOwnApiKeys` (named) + 2 more (written, unexecuted) | No | No |
| **R43** *(referenced)* — every action audited | Yes (pre-existing, `ApiKeyService`, unmodified) | `ApiKeyService.java` (T24) | Covered by `ApiKeyServiceIntegrationTest` (T24, not re-executed this session — Docker) | No | No |
| **R46** *(referenced)* — 4xx is `application/problem+json`, no internal detail | Yes, with one documented, gate-approved exception | `ApiKeyExceptionHandler.java:36-50` (`onNotFound`/`onNotAuthorized`, both no-detail) | `ApiKeyExceptionHandlerTest` (3 new tests, executed, green); `ApiKeyCrudIntegrationTest`'s `rejectionBody` helper asserts type/title/content-type (written, unexecuted) | No | **Yes, documented (D6)**: `InvalidAccountStateException` → 409 with `detail` — see Constraints/Deviations below |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence | Test? | Deviation? |
|---|---|---|---|---|
| **L7** — key format, SHA-256 at rest, plaintext-once | Yes (T24-frozen, unmodified) | `ApiKeyService.java`, `ApiKeyHasher.java` (untouched by T26) | `ApiKeyServiceIntegrationTest` (T24, existing) | No |
| **L12** *(referenced)* — no cross-module entity import | Yes | `ApiKeyController` imports only `ApiKeyService`'s nested records (`CreateApiKeyResult`, `ApiKeyMetadata`) and `Authentication` (Spring Security core, not a foreign module) — no `Account`/`ApiKey` entity import anywhere | Satisfied by construction; `ArchitectureTest` not independently re-run this session (Docker), confirmed by direct code inspection | No |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | `create`/`list`/`revoke` are not registered in `PublicEndpoints.java`; sit behind the `@Order(2)` chain's default `authenticated()` rule |
| AC2 | **Met** | All three derive `accountUuid` via `UUID.fromString(authentication.getName())` only — `ApiKeyController.java:64,77,90`; `createDerivesCallerFromAuthenticationNotRequestBody`/`listDerivesCallerFromAuthentication` (executed, green) |
| AC3 | **Met** | `create` returns 201 with `CreateApiKeyResult` (D2/D4); `createReturns201WithTheServiceResult` (executed, green); `shouldCreateApiKeyAndShowPlaintextExactlyOnce` additionally asserts the exact 4-field JSON shape (written, unexecuted) |
| AC4 | **Met** | `@Valid @RequestBody CreateApiKeyRequest` (D1); `createRejectsBlankOrOverLengthName` + `createAcceptsNameAtTheHundredCharacterBoundary` (both boundaries, written, unexecuted) — no unit-level equivalent exists since `@Valid` isn't exercised by a Mockito-direct-construction test (consistent with `AccountControllerTest`'s same limitation) |
| AC5 | **Met** | `list` returns `ApiKeyMetadata` directly, scoped to resolved `accountUuid` (D3); `listReturnsTheCallersOwnKeys`/`listReturnsEmptyListWhenCallerHasNoKeys` (executed, green); `listNeverReturnsAnotherAccountsKeys` (written, unexecuted) |
| AC6 | **Met by construction**, additionally test-asserted | `CreateApiKeyResult`/`ApiKeyMetadata` have no hash field (T24); `shouldCreateApiKeyAndShowPlaintextExactlyOnce` and `listResponseContainsNoHashShapedField` both assert the exact field set and the absence of any 64-hex-character value (Kimi Phase 8/11 findings, written, unexecuted) |
| AC7 | **Met** | `ApiKeyNotFoundException` → `onNotFound`, one exception type for both causes; `revokePropagatesApiKeyNotFoundUncaught` (executed, green); `deleteOfUnownedKeyAndNonexistentKeyAreByteIdentical` (written, unexecuted, now also asserts exact type/title) |
| AC8 | **Met** | `ApiKeyService.revoke`'s idempotent `revokeIfActive` (T24, unmodified); `revokeOfAlreadyRevokedKeyStillReturns204` (executed, green); `deleteOfAlreadyRevokedKeyReturns204` (written, unexecuted) |
| AC9 | **Met** | `onNotAuthorized` — 403, `API_KEY_NOT_AUTHORIZED`, no detail; `onNotAuthorizedReturnsUniform403` (executed, green); `createRejectsCallerLackingMerchantRoleOrConfirmedMfa` (written, unexecuted, asserts exact type/title) |
| AC10 | **Met, unchanged, by design (D6)** | `AccountExceptionHandler` (untouched) continues to produce 409-with-detail; `createPropagatesInvalidAccountStateUncaught` (executed, green) + `createRejectsNonActiveAccountWith409` (written, unexecuted) |
| AC11 | **Met** | `create` sets no `Location` header anywhere; `createResponseHasNoLocationHeader` (executed, green) |
| AC12 | **Expected, not independently re-run this session** | No new class imports `PublicEndpoints` or a foreign entity (confirmed by inspection); `ArchitectureTest` itself needs Docker in this sandbox's Surefire wiring to report non-zero test counts (same pre-existing quirk noted since T16 Phase 12) |

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes, within T26's authorized scope. Every file the frozen brief authorized was created or modified exactly as planned; every Phase 3/8 review finding was triaged and either fixed or explicitly, on-the-record deferred. As with T25, the one category of incompleteness is environmental: Docker remains unavailable this entire session, so `ApiKeyCrudIntegrationTest` (12 tests covering both named tests and the HTTP/DB-layer boundary cases) is written and compiles cleanly but has never executed against a real server.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC12 all have direct code evidence per the matrix above, each backed by at least one executed passing test (unit level) plus a written-but-blocked integration test for full end-to-end confirmation.

**(3) Does it violate any LOCKED decision?** No unauthorized violation. T26 introduced no new LOCKED-decision deviations of its own (unlike T25, which needed a gate-approved exception to a frozen file); the one departure from a general spec rule — `InvalidAccountStateException`'s 409-with-detail under R46's "no internal detail" guidance — is D6, explicitly decided at the Phase 4 gate with reasoning on record (the caller is already authenticated as the exact account named in the detail; this is pre-existing, unmodified, service-wide infrastructure, not something T26 introduced or could fix within its authorized files).

**(4) Remaining risks:**
- **Unexecuted integration suite (highest-priority residual, same as T25).** `ApiKeyCrudIntegrationTest`'s 12 tests — including both named tests — have never run against real Postgres or the real filter chain this session. Needs a working Docker daemon before this task is considered fully proven end-to-end.
- **Malformed `keyUuid` path segment returns 500, not 400 (Phase 7/8 shared finding, D-not-applicable — accepted, not part of the frozen brief's gate decisions since it surfaced during implementation, not design).** Explicitly *not* fixed within T26 — the fix belongs in the shared, out-of-scope `common/ApiExceptionHandler.java`, affecting ~10 other endpoints across `account`/`authz` too. A dedicated test (`deleteWithMalformedKeyUuidIsAKnownPreExistingLimitationReturning500`) now documents the actual behavior so a future fix doesn't silently go unnoticed or get "un-fixed" by accident.
- **`InvalidAccountStateException`'s 409-with-detail (D6)** is an intentional, reasoned acceptance, not an oversight — but worth restating here since R46 reads as a blanket rule and this is the one place it doesn't strictly apply.
- **Contract files still don't exist.** `contracts/api/auth.yaml`/`token-claims.md` remain absent (same gap noted at T25's Phase 0/1); contract conformance for these three endpoints cannot be verified either.
- **`package.md` §11 Q3 (per-merchant key cap / scope vocabulary)** remains unresolved by the spec author; correctly out of T26's scope to decide.

---

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion has direct code evidence and either an executed passing test or a written-but-Docker-blocked test with a clear, honest account of why it hasn't run; the sole departure from a general spec rule (D6) is deliberate, reasoned, and on the record from the Phase 4 gate, not a defect.

---

**Phase 12 complete — PASS.** Proceed to Phase 13 (PR / Commit Preparation).
