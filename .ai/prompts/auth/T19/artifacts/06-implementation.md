# auth · T19 — Phase 6: Implementation

Consumes `artifacts/05-implementation-plan.md`. Real code, written and verified against a real
build/test run (Docker available this session).

---

## A correction applied, disclosed before any code was written for it

Phase 5's own plan carried forward Phase 2/4's `MfaCurrentPasswordMismatchException` → **403
Forbidden** mapping (modeled on `ApiKeyNotAuthorizedException`'s 403 — "caller lacks
MERCHANT/confirmed MFA"). Before writing `MfaExceptionHandler`, I read
`AccountExceptionHandler.java` directly (not done at any earlier phase) and found the real,
already-shipped precedent: `ProblemTypes.CURRENT_PASSWORD_MISMATCH` — the *same* type T19 reuses
(Phase 5's own, separately-caught correction) — is mapped at **400 Bad Request**, title "Current
password is incorrect" (`AccountExceptionHandler.onCurrentPasswordMismatch`, `/accounts/me/password`'s
own wrong-password case). A stable, published `ProblemTypes` URI cannot mean 400 in one place and
403 in another, so `MfaExceptionHandler.onPasswordMismatch` uses 400, not 403. Both
`MfaExceptionHandlerTest` and `MfaControllerIntegrationTest` assert this directly, including a
same-URI-instance proof that the two handlers share one type on purpose, not by coincidence.

## A second, unrelated gap found and fixed during this phase (not anticipated by Phases 1–5)

`AuthOpenApiContractTest` (T33/R47's own contract-conformance test, `shouldConformToAuthOpenApiContract`
named test) keeps an explicit, hand-maintained `CONTROLLERS` list plus two expected-schema tables
— it does not discover routes via a running Spring context. No phase before this one listed this
file as one to modify, and it isn't in Phase 5's "Files to Modify." Running the full suite after
writing `auth.yaml`'s four new paths failed two of its tests
(`authYamlDocumentsNoRouteThatDoesNotHaveARealHandler`, `shouldConformToAuthOpenApiContract`): the
newly-documented routes had no matching entry in `CONTROLLERS`/`expectedResponseSchemas()`/
`expectedRequestSchemas()`/`realInstancesByComponentName()`. This is squarely in scope (it's the
exact mechanism enforcing AC10, "contract documents all four endpoints"), so I added `MfaController`
to `CONTROLLERS`, the four new routes to both expected-schema tables, and real instances of the
four new DTOs to `realInstancesByComponentName()` — not a scope expansion, a dependency this task's
own acceptance criterion already implied but no earlier phase's file list named.

## A third finding, disclosed but NOT fixed (out of scope)

Fixing this task's own three flagged `package.md` §8 lines revealed the same test-name-to-R#
off-by-N drift recurs far more widely in that table than Phase 1–4 caught — documented in
`spec/auth-service/package.md` §8 itself (a blockquote note added there) rather than silently
absorbed or silently ignored. Left unfixed beyond this task's own four lines, per the frozen brief's
exact scope.

## Files created

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaController.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/BeginEnrollResponse.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/TotpCodeRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/PasswordAndTotpRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/RecoveryCodesResponse.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaControllerTest.java` (13 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaExceptionHandlerTest.java` (6 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaControllerIntegrationTest.java` (14 tests)

## Files modified

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — added
  `regenerateRecoveryCodes` + `RegenerateRecoveryCodesResult` (code matches Phase 2's draft exactly).
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — added
  `MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED`, `MFA_INVALID_CODE` (3, per Phase 5's correction).
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServiceTest.java` — extended with 7 new
  `regenerateRecoveryCodes` test cases, mirroring `disable`'s existing shape.
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` — extended
  per the second finding above (not in any earlier phase's file list).
- `contracts/api/auth.yaml` — four new paths under `/accounts/me/mfa`, four new schemas, success
  responses only (Phase 4's Finding 5 correction).
- `spec/auth-service/package.md` §8 — fixed the three flagged lines to R22/R23/R28, added the new
  `shouldRegenerateRecoveryCodesWithPasswordAndTotp` → R49 line, and added the disclosure note for
  the third finding above.

## Verification performed

- `mvn -pl services/auth -am compile` / `test-compile`: clean.
- `mvn -pl services/auth test -Dtest=MfaServiceTest,MfaControllerTest,MfaExceptionHandlerTest,ArchitectureTest`:
  all new/extended classes green (36/13/6 tests). `ArchitectureTest` itself reports `Tests run: 0`
  — the pre-existing, already-documented Surefire/`@ArchTest`-field bug (memory:
  `surefire-archtest-field-bug.md`), not a regression introduced here.
- **ArchUnit canaries independently re-verified**, bypassing Surefire entirely: a temporary
  standalone `main()` runner (same package, deleted after use — never committed) directly invoked
  `shouldPreventCrossModuleEntityImports.check(...)` and `shouldEnforcePublicEndpointAllowlist.check(...)`
  against the real, compiled class graph (187 classes) with `MfaController` included. **Both PASS.**
- `mvn -pl services/auth test -Dtest=MfaControllerIntegrationTest` (Docker available this session,
  real Postgres+Kafka via Testcontainers): 14/14 green, including all four named tests
  (R22/R23/R28/R49), confirmed wrong-password/wrong-code/already-enrolled/not-enrolled failure
  paths with the full RFC 9457 body, blank-field 400s, and an unauthenticated-caller check for all
  four endpoints (401 with `WWW-Authenticate: Bearer`, confirmed via a redirect-disabled
  `java.net.http.HttpClient` after `TestRestTemplate`/Apache HttpClient5 misreported a DELETE's real
  401 as a spurious "circular redirect" — a client-side quirk, not real server behavior, confirmed
  by inspecting the raw, non-redirect-following response).
- `mvn -pl services/auth test -Dtest=AuthOpenApiContractTest`: 10/10 green after the second finding's
  fix.
- Full suite (`mvn -pl services/auth test`): 748 tests. Every MFA-related and contract-related class
  is green across three separate full/partial runs. A shifting subset of unrelated Kafka-outbox/audit-
  mirror integration tests (`OutboxRelayTest`, `ApiKeyLifecycleIntegrationTest`,
  `EndToEndLifecycleIntegrationTest`, `AccountPersistenceIntegrationTest`, `ApiKeyExchangeIntegrationTest`,
  `AuditTrailIntegrationTest`) failed with `ConditionTimeout`/`broker unavailable` in each run, never
  the same subset twice — consistent with pre-existing Testcontainers-Kafka timing flakiness under
  this sandbox's resource constraints (15-second `awaitility` windows), not a regression: none of
  these classes touch MFA, `ProblemTypes`, `auth.yaml`, or `package.md`.

## Open Questions

None new. The two findings disclosed above are resolved within this phase; the third is explicitly
left open for a future, dedicated task.
