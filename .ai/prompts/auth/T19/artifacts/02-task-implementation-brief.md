# auth · T19 — Phase 2: Task Implementation Brief

## Task

Add the MFA self-service HTTP surface: `MfaController` and `MfaExceptionHandler`, wrapping the
already-built `MfaService.beginEnroll`/`confirm`/`disable` (T16–T18), plus one new `MfaService`
method for recovery-code regeneration (R49), and the `contracts/api/auth.yaml` documentation for all
four endpoints.

## Purpose

T16–T18 built the complete MFA business logic with no way to reach it over HTTP. This task is the
thin translation layer — request in, `MfaService` call, response or `ProblemDetail` out — mirroring
`ApiKeyController`/`ApiKeyExceptionHandler`'s own established shape exactly, plus the one genuinely
new piece of business logic (regenerate) that Phase 0 found was never specified anywhere.

## Scope

**In:**
- `MfaController`, `@RequestMapping("/accounts/me/mfa")`, four endpoints:
  - `POST /totp` → `beginEnroll`. Returns `201` with `BeginEnrollResponse(String provisioningUri)` —
    only the URI, never the raw secret bytes (R22's own literal wording names only the URI; the
    secret is already encoded inside it for manual entry if a client ever needs it). No `Location`
    header, mirroring `ApiKeyController.create`'s own identical precedent (no `GET` to resolve to).
  - `POST /totp/confirm` → `confirm`. Request: `TotpCodeRequest(String code)`. Returns `200` with
    `RecoveryCodesResponse(List<String> recoveryCodes)`.
  - `DELETE /totp` → `disable`. Request: `PasswordAndTotpRequest(String currentPassword, String
    code)`. Returns `204`, mirroring `ApiKeyController.revoke`.
  - `POST /recovery-codes` → the new `regenerateRecoveryCodes` method below. Request: the same
    `PasswordAndTotpRequest` shape as disable (identical fields, reused rather than duplicated).
    Returns `200` with the same `RecoveryCodesResponse` shape as confirm.
- One new `MfaService` method, built by direct symmetry with `disable` and `confirm` — no new
  primitive, only recombining what both already do:
  ```java
  @Transactional
  public RegenerateRecoveryCodesResult regenerateRecoveryCodes(
          UUID accountUuid, String currentPassword, String submittedCode) {
      AccountResponse account = requireActiveAccount(accountUuid, "regenerate recovery codes");
      Long accountId = resolveAccountId(accountUuid);

      LoginView loginView = accountService.findLoginView(account.email()).orElse(null);
      if (loginView == null || !passwordEncoder.matches(currentPassword, loginView.passwordHash())) {
          recordAudit("mfa.recovery_codes_regenerate_failed", AuditOutcome.FAILURE, accountUuid);
          throw new MfaCurrentPasswordMismatchException();
      }

      MfaEnrollment enrollment = mfaEnrollmentRepository
              .findByAccountIdAndTypeAndConfirmedAtIsNotNull(accountId, MfaEnrollment.Type.TOTP)
              .orElseThrow(MfaNotEnrolledException::new);

      byte[] secret = mfaSeedEncryption.decrypt(enrollment.getSecretEncrypted());
      if (!totpVerifier.verify(secret, submittedCode, clock.instant())) {
          recordAudit("mfa.failed", AuditOutcome.FAILURE, accountUuid);
          throw new InvalidTotpCodeException();
      }

      recoveryCodeRepository.deleteByAccountId(accountId);
      Instant now = clock.instant();
      List<String> rawCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
      for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
          String rawCode = generateRawRecoveryCode();
          recoveryCodeRepository.save(RecoveryCode.create(accountId, Hashing.sha256(rawCode), now));
          rawCodes.add(rawCode);
      }
      recordAudit("mfa.recovery_codes_regenerated", AuditOutcome.SUCCESS, accountUuid);
      return new RegenerateRecoveryCodesResult(List.copyOf(rawCodes));
  }
  ```
  The two audit-event names on failure mirror `disable`'s own established split exactly: a wrong
  *password* gets its own specific event name (`mfa.disable_failed` there, `mfa.recovery_codes_regenerate_failed`
  here), while a wrong *TOTP code* always gets the generic `mfa.failed` (R29's own literal text,
  identical everywhere it already appears). No new pattern invented.
- `MfaExceptionHandler`, `@RestControllerAdvice`, `@Order(Ordered.HIGHEST_PRECEDENCE)` (mirrors
  `ApiKeyExceptionHandler`'s own documented reason — must outrank the catch-all advice), one mapping
  per exception this task's four endpoints can actually throw:
  - `MfaAlreadyEnrolledException` → `409 Conflict`.
  - `MfaNotEnrolledException` → `404 Not Found` (no enrollment to confirm/disable/regenerate against).
  - `InvalidTotpCodeException` → `401 Unauthorized`, mirroring `ApiKeyExceptionHandler.onExchangeRejected`'s
    own choice of 401 for a failed-credential-style rejection.
  - `MfaCurrentPasswordMismatchException` → `403 Forbidden`, mirroring `ApiKeyExceptionHandler.onNotAuthorized`'s
    own choice for a precondition the caller is authenticated but fails.
  - New `ProblemTypes` constants for each, following the existing `API_KEY_*` naming convention
    (`MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED`, `MFA_INVALID_CODE`, `MFA_PASSWORD_MISMATCH`).
- `contracts/api/auth.yaml`: four new paths under `/accounts/me/mfa`, following
  `/accounts/me/password`'s own exact style (`operationId`, `requestBody` → named schema, explicit
  response codes), all `security: [bearerAuth]` (none added to `PublicEndpoints.java`, per L11).
- `package.md` §8: fix the three mismapped named tests flagged at Phase 1
  (`shouldReturnTotpProvisioningUriOnEnrollmentBegin` → `R22`, not `R19`;
  `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` → `R23`, not `R20`;
  `shouldRequirePasswordAndTotpToDisableMfa` → `R28`, not `R25`), and add
  `shouldRegenerateRecoveryCodesWithPasswordAndTotp` → `R49`.

**Out:**
- `InvalidRecoveryCodeException` gets **no mapping in `MfaExceptionHandler`**. None of this task's
  four endpoints can ever throw it — it exists solely for `MfaService.verifyRecoveryCode`, which is
  task 20's own login-flow call site, not reachable from anything T19 builds. Mapping it here would
  be dead code.
- `MfaEncryptionException` (a KMS/infrastructure failure, not a caller error) gets **no special
  mapping** either — it falls through to the existing generic catch-all advice, which already
  produces a safe, detail-free `5xx` response (R46). Adding a specific mapping here would be
  defending against a failure mode this task has no way to test meaningfully.
- No change to `TokenClaimsCustomizer` (task 21), no SAS/login-flow change (task 20, already done),
  no Flyway migration (no schema change — this task only adds a repository-level call already proven
  safe at T18: `recoveryCodeRepository.deleteByAccountId`, reused, not reimplemented).
- No change to `MfaEnrollment`, `MfaEnrollmentRepository`, `RecoveryCode`, or `AccountService` beyond
  calling their existing public methods.

## Business Rules

R22, R23, R28, R29, R49 (unchanged from Phase 1).

## Locked Decisions

L6, L11, L12 (unchanged from Phase 1).

## Dependencies

No new library. `common.Hashing`, `common.ProblemTypes` (extended, not replaced), the existing
`MfaService` collaborators. `contracts/api/auth.yaml` confirmed present and is modified, not created.

## Inputs / Outputs

- `POST /totp` → `BeginEnrollResponse(provisioningUri)`.
- `POST /totp/confirm` (`TotpCodeRequest(code)`) → `RecoveryCodesResponse(recoveryCodes)`.
- `DELETE /totp` (`PasswordAndTotpRequest(currentPassword, code)`) → no body.
- `POST /recovery-codes` (`PasswordAndTotpRequest(currentPassword, code)`) → `RecoveryCodesResponse(recoveryCodes)`.

## State Changes

Same tables T18 already writes (`mfa_enrollments`, `recovery_codes`, `auth_audit` + Kafka mirror) —
this task adds no new table and no new column. The new method adds one more `recovery_codes` bulk
delete + 10-insert cycle, identical in shape to `confirm`'s own.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaController.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/BeginEnrollResponse.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/TotpCodeRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/RecoveryCodesResponse.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/PasswordAndTotpRequest.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — add
  `regenerateRecoveryCodes` and `RegenerateRecoveryCodesResult`.
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — add four MFA constants.
- `contracts/api/auth.yaml` — four new paths + their schemas.
- `spec/auth-service/package.md` — fix three mismapped §8 test lines, add one new line for R49.

## Files NOT to Modify

`AccountService.java`, `Account.java`, `MfaEnrollment.java`, `MfaEnrollmentRepository.java`,
`RecoveryCode.java`, `RecoveryCodeRepository.java` (already has `deleteByAccountId` from T18),
`PublicEndpoints.java`, `TokenClaimsCustomizer.java`, every Flyway migration.

## Acceptance Criteria

AC1–AC10, unchanged from Phase 1.

## Required Tests

Named tests (corrected mapping): `shouldReturnTotpProvisioningUriOnEnrollmentBegin` (R22),
`shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` (R23),
`shouldRequirePasswordAndTotpToDisableMfa` (R28), `shouldRegenerateRecoveryCodesWithPasswordAndTotp`
(R49, new). Plus: one controller-level test per exception → HTTP mapping (6 exceptions, 5 of them
mapped, 1 deliberately not — see Scope/Out), a contract test confirming the generated TS client
compiles against the four new paths, and a public-endpoint test confirming none of the four appear
in the allowlist.

## Constraints

- **Security:** raw TOTP secret, raw recovery codes, and raw passwords never logged or echoed in any
  `ProblemDetail`. Recovery codes returned exactly once per operation (confirm, regenerate) — never
  re-derivable afterward.
- **Thread-safety:** `MfaController` and `MfaExceptionHandler` are stateless Spring singletons.
- **Transaction:** `regenerateRecoveryCodes` is one `@Transactional` boundary, matching `confirm`'s
  and `disable`'s own convention — the delete-old/generate-new cycle is atomic.
- **Module boundaries (L12):** `MfaController` depends only on `MfaService` and framework types,
  never on `Account` or another module's entity.
- **Null handling:** request DTOs validate non-blank fields the same way `CreateApiKeyRequest`
  already does (`@NotBlank`), so a blank or missing field 400s via the framework's existing
  `MethodArgumentNotValidException` handling rather than reaching `MfaService` at all.

## Open Questions

No blockers. One disclosed scope choice, not silently assumed: `InvalidRecoveryCodeException` is
deliberately unmapped in `MfaExceptionHandler`, since no path in this task can throw it. If Phase 3
finds a reason this task's own endpoints could reach it after all, that would be a real finding, not
a style nitpick.
