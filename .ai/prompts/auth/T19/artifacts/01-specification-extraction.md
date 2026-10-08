# auth · T19 — Phase 1: Specification Extraction

Consumes `artifacts/00-repository-understanding.md`. Extracting only what T19 ("MFA controller")
needs.

## Business Rules

- **R22.** WHEN an authenticated user without a confirmed TOTP enrollment calls
  `POST /accounts/me/mfa/totp`, THEN the system SHALL generate a random TOTP secret, encrypt it,
  persist it as unconfirmed, and return an `otpauth://` provisioning URI. T19 owns the endpoint;
  `MfaService.beginEnroll` (T18) already owns the generate+encrypt+persist logic.
- **R23.** WHEN the user submits the correct first TOTP code to `POST /accounts/me/mfa/totp/confirm`,
  THEN the system SHALL confirm the enrollment, generate 10 single-use recovery codes, store only
  hashes, and return the recovery codes exactly once. T19 owns the endpoint; `MfaService.confirm`
  (T18) already owns verify+confirm+generate-codes.
- **R28.** WHEN an authenticated user supplies their current password and a valid TOTP code to
  `DELETE /accounts/me/mfa/totp`, THEN the system SHALL remove the enrollment, invalidate all
  recovery codes, and record an `mfa.disabled` audit event. T19 owns the endpoint; `MfaService.disable`
  (T18) already owns the logic.
- **R29.** IF a TOTP code or recovery code verification fails, THEN the system SHALL record an
  `mfa.failed` audit event and deny authentication. Already enforced inside `MfaService.confirm`/
  `disable`; T19's job is to translate the exceptions those methods already throw into HTTP responses,
  not to re-implement the audit call.
- **R49** (added at Phase 0). WHEN an authenticated user with a confirmed TOTP enrollment supplies
  their current password and a valid TOTP code to `POST /accounts/me/mfa/recovery-codes`, THEN the
  system SHALL invalidate every existing recovery code, generate 10 new ones, store only hashes,
  return the new codes exactly once, and record an `mfa.recovery_codes_regenerated` audit event. IF
  the password or code is wrong, THEN the system SHALL record a failed-attempt audit event and SHALL
  NOT mutate any code. T19 owns both the endpoint and the one new `MfaService` method this requires —
  unlike R22/R23/R28, there is no pre-existing service method to wrap.

## Locked Decisions

- **L6.** RFC 6238 (30s, 6 digits, HMAC-SHA1); recovery codes are random single-use values, only
  SHA-256 hashes stored. Governs the new service method's own code generation (reuse `MfaService`'s
  existing private `generateRawRecoveryCode()` and `Hashing.sha256(...)`, not a new primitive).
- **L11.** Public endpoint discipline — the only unauthenticated paths are actuator, `POST /accounts`,
  the SAS protocol endpoints, and `POST /api-keys/token`. All four MFA endpoints require authentication
  (R22/R23/R28/R49's own literal text); none is added to `PublicEndpoints.java`.
- **L12.** Module boundaries — no feature module imports another's entity. `MfaController` depends only
  on `MfaService` (same module) and `Authentication` (framework), never on `Account` or any other
  module's entity directly, mirroring `ApiKeyController`'s own identical discipline.

## Files Involved

**New:**
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaController.java` — four endpoints
  (`@PostMapping`/`@PostMapping("/confirm")`/`@DeleteMapping`/`@PostMapping("/recovery-codes")`),
  mirroring `ApiKeyController`'s own shape: constructor-injected `MfaService`, `Authentication` →
  `UUID.fromString(authentication.getName())`, domain exceptions propagate uncaught.
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaExceptionHandler.java` — `@RestControllerAdvice`,
  `@Order(Ordered.HIGHEST_PRECEDENCE)` (mirrors `ApiKeyExceptionHandler`'s own documented reason: must
  outrank the catch-all advice), one `@ExceptionHandler` per existing MFA exception class.
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/` — request/response records: a begin-enroll
  response (secret as a provisioning URI, matching R22's own literal wording — never the raw secret
  bytes), a confirm request (submitted code) and response (10 recovery codes), a disable request
  (current password + code), a regenerate request (current password + code) and response (10 new
  recovery codes).
- One new `MfaService` method for R49 (regenerate), plus, if the current exceptions don't already
  cover a wrong-password-on-regenerate case distinctly, reusing `MfaCurrentPasswordMismatchException`
  (already used by `disable`) rather than inventing a new one.
- `contracts/api/auth.yaml` — four new paths, under `/accounts/me/mfa/...`, following
  `/accounts/me/password`'s own exact style (`post`/`delete`, `operationId`, `requestBody` → named
  schema, explicit response codes).

**Existing, read-only dependencies (not modified):**
- `mfa.MfaService` (T16–T18) — `beginEnroll`, `confirm`, `disable`, all fully built and tested.
- `mfa.MfaAlreadyEnrolledException`, `MfaNotEnrolledException`, `MfaCurrentPasswordMismatchException`,
  `InvalidTotpCodeException`, `InvalidRecoveryCodeException`, `MfaEncryptionException` — already
  defined, none yet mapped to an HTTP response.
- `apikey.ApiKeyController`/`ApiKeyExceptionHandler` — the direct structural exemplar for both new
  files this task creates.
- `common.ProblemTypes` — the shared constants class `ApiKeyExceptionHandler` uses
  (`ProblemTypes.API_KEY_NOT_AUTHORIZED`, etc.); T19 adds its own MFA-specific constants here, not a
  separate file.

## Dependencies

No new Maven dependency. `contracts/api/auth.yaml` **now exists** (22KB, confirmed directly) — this
differs from T18's own Phase 1 finding ("`contracts/api/` contains only a `.gitkeep`"), since API-key
tasks (T25/T26) authored it in the time since. T19 is the first MFA task that actually touches this
file, unlike T16–T18 which were service-layer only.

## Acceptance Criteria

- **AC1 (R22).** `POST /accounts/me/mfa/totp` requires authentication, calls `MfaService.beginEnroll`,
  and returns the provisioning URI with `201` or `200` (Phase 2 to pin the exact status, mirroring
  `ApiKeyController.create`'s own `201`-no-`Location` precedent since there's likewise no
  `GET .../totp` to resolve to).
- **AC2 (R22).** A caller with an existing confirmed enrollment gets `MfaAlreadyEnrolledException`
  mapped to a real HTTP response, not an unhandled 500.
- **AC3 (R23).** `POST /accounts/me/mfa/totp/confirm` calls `MfaService.confirm` and returns the 10
  recovery codes on success.
- **AC4 (R23, R29).** A wrong code maps `InvalidTotpCodeException` to a real response; no codes are
  returned.
- **AC5 (R28).** `DELETE /accounts/me/mfa/totp` calls `MfaService.disable` with the request body's
  password and code, and returns `204` on success.
- **AC6 (R28, R29).** A wrong password or wrong code maps `MfaCurrentPasswordMismatchException`/
  `InvalidTotpCodeException` to a real response; nothing is removed.
- **AC7 (R49).** `POST /accounts/me/mfa/recovery-codes` requires the current password and a valid TOTP
  code, invalidates existing codes, generates and returns 10 new ones exactly once, and records
  `mfa.recovery_codes_regenerated`.
- **AC8 (R49).** A wrong password or code on regenerate mutates nothing and records a failed-attempt
  audit event, reusing the existing exception types rather than inventing new ones unless Phase 2
  finds a real reason to.
- **AC9 (L11).** None of the four endpoints appear in `PublicEndpoints.java`.
- **AC10 (R47).** `contracts/api/auth.yaml` documents all four endpoints, and the generated TS client
  compiles against them.

## Tests Required

- **Named tests, already in `package.md` §8 but mismapped** (T18's Phase 1 already flagged this exact
  problem three times, never fixed): `shouldReturnTotpProvisioningUriOnEnrollmentBegin` is mapped to
  `R19`, should be `R22`; `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` is mapped to
  `R20`, should be `R23`; `shouldRequirePasswordAndTotpToDisableMfa` is mapped to `R25`, should be
  `R28`. T19 is the first task where these tests' own literal HTTP endpoints actually exist to test
  against — this task should fix the mapping in `package.md`, not carry the error forward a fourth
  time.
- **New named test needed for R49**: no existing `package.md` §8 line covers recovery-code
  regeneration at all (consistent with Phase 0's finding that no requirement covered it either).
  Propose `shouldRegenerateRecoveryCodesWithPasswordAndTotp` → R49.
- **Controller-level tests** for every exception → HTTP mapping (AC2, AC4, AC6, AC8): each of the six
  existing exception classes, plus whatever regenerate's own failure path maps to.
- **Contract test**: the generated client compiles and its request/response shapes match what the
  controller actually accepts/returns (R47).
- **Public-endpoint test**: `ArchitectureTest`'s own `shouldEnforcePublicEndpointAllowlist` rule (or
  equivalent) continues to pass with these four new, authenticated-only paths.

## Open Questions

No blockers from `package.md` §11 (Q1–Q6 don't concern this task; Q1 was resolved at T16). One
process item, not a blocker: whether to fix the three mismapped named tests in `package.md` §8 as
part of this task (recommended, since this is the first task where the mismatch is actually
observable against real endpoints) or leave it for a dedicated cleanup task. Proceeding to Phase 2
assuming the fix happens here, since it's a one-line correction per entry and directly touches the
same file this task's own new §8 entry (R49's named test) must be added to anyway.
