# auth · T19 — Phase 0: Repository Understanding

No code written. Read-only pass over the repository and `spec/auth-service/`'s five files.

## 1. Architecture Summary

`auth-service` is Themistra's OIDC/OAuth2 issuer (Spring Boot 3.5.4 / Java 21), package-by-feature
under `com.themistra.auth.<module>`. Services are constructor-injected, `@Transactional` per public
method, and take a shared `Clock` bean rather than calling `Instant.now()` inline. Persistence is one
Postgres schema (`auth`), Flyway DDL-only (V1–V5, immutable). Security-relevant actions audit through
`AuditService.record(...)`, which both persists the row and mirrors it to Kafka via the outbox.

T16–T18 (seed generation/encryption, persistence, and the service layer) are all closed with `PASS`
verdicts. T19 (this task) is the only task in the 40-task auth pipeline with zero artifacts — nothing
has run for it yet. T20 (SAS MFA step) is also closed, and its own frozen brief explicitly recorded
T19 as "out-of-scope," confirming this gap was known and deliberate, not accidental.

## 2. Existing Code This Task Touches

**Already built, all read-only context for T19 (not modified except where noted in §3):**

- `mfa.MfaService` (T16–T18) — the complete business logic this task's three enroll/confirm/disable
  endpoints wrap:
  - `beginEnroll(UUID accountUuid)` → `BeginEnrollResult(byte[] secret, String provisioningUri)`.
    Throws `MfaAlreadyEnrolledException` if a confirmed enrollment already exists.
  - `confirm(UUID accountUuid, String submittedCode)` → `ConfirmResult(List<String> recoveryCodes)`.
    Throws `MfaNotEnrolledException` (no pending enrollment) or `InvalidTotpCodeException` (wrong code).
  - `disable(UUID accountUuid, String currentPassword, String submittedCode)` → `void`. Throws
    `MfaCurrentPasswordMismatchException`, `MfaNotEnrolledException`, or `InvalidTotpCodeException`.
  - `verifyRecoveryCode`, `hasConfirmedTotpEnrollment`, `verifyTotpCodeForLogin` — used by T20's own
    SAS login step, not by this task's controller.
- Exception classes, all already defined, none yet mapped to an HTTP response: `MfaAlreadyEnrolledException`,
  `MfaNotEnrolledException`, `MfaCurrentPasswordMismatchException`, `InvalidTotpCodeException`,
  `InvalidRecoveryCodeException`, `MfaEncryptionException`. No `MfaController` or `MfaExceptionHandler`
  exists yet — this task creates both, mirroring `ApiKeyController`/`ApiKeyExceptionHandler`'s own
  established pattern (constructor-injected service, `Authentication` → `UUID.fromString(authentication.getName())`,
  exceptions propagate uncaught for a dedicated `@RestControllerAdvice` to translate to RFC 9457
  `ProblemDetail`).
- `PublicEndpoints.java` — not touched by this task. Every `mfa` endpoint requires authentication
  (R22/R23/R28's own literal text), so none is added to the allowlist (L11).
- `contracts/api/auth.yaml` — has no `mfa` path today. `/accounts/me/password`'s own shape (`post`,
  `operationId`, `requestBody` → named schema, `204`/specific success code) is the style exemplar this
  task's own new paths should follow.

## 3. A real gap the task's own literal text does not disclose

Task 19's own text (`tasks.md` line 34) names four endpoints: `POST /accounts/me/mfa/totp`,
`POST /accounts/me/mfa/totp/confirm`, `DELETE /accounts/me/mfa/totp`, and
`POST /accounts/me/mfa/recovery-codes`. The first three map directly onto `MfaService.beginEnroll`,
`confirm`, and `disable` — real, tested, already-built methods, with real requirement IDs (R22, R23,
R28) each already stating their own acceptance criteria.

**The fourth does not.** Confirmed directly, not assumed:
- `MfaService` has no method that regenerates recovery codes. Its only recovery-code-producing code
  path is inside `confirm(...)`, which also confirms a pending enrollment — not something a
  regenerate call should repeat.
- `requirements.md` has no requirement for this endpoint. R22, R23, R28, and R29 cover enroll, confirm,
  disable, and the failed-audit event — none specifies what regeneration requires (a precondition? an
  audit event? invalidating the old codes first?), what it returns, or when it's allowed.
- `design.md` O5 ("recovery-code hashing primitive") is the only mention of recovery codes outside
  R23/R28, and it's about the hash algorithm, not regeneration.

This means the fourth endpoint needs a design decision and a small new `MfaService` method, not just
controller wiring — a materially different shape of work than the other three, and the task's own
literal text doesn't flag that difference.

## Decision needed before Phase 1

The most natural design, by direct symmetry with the two existing operations that already touch
recovery codes, is: require the current password and a valid TOTP code (the same precondition
`disable` already uses), delete the existing (unused and used) recovery codes, generate 10 new ones
with the same helper `confirm` already uses, and record a new audit event type (for example
`mfa.recovery_codes_regenerated`), returning the new codes exactly once. This is a proposal, not a
decision — no requirement governs it today.

Presented to the user as a genuine blocker.

## Decision

**User chose: add the requirement now, proceed with all four endpoints in this task.** Added
`R49` to `spec/auth-service/requirements.md` (appended, not inserted mid-sequence, so no existing
requirement ID shifts): current password + valid TOTP required, old codes invalidated, 10 new codes
generated and returned exactly once, a new `mfa.recovery_codes_regenerated` audit event on success, a
failed-attempt audit event (no mutation) on a wrong password or code — by direct symmetry with R23
(confirm) and R28 (disable), the two existing requirements closest in shape.

T19 proceeds with all four endpoints: `beginEnroll`/`confirm`/`disable` wired directly onto the
existing, tested `MfaService` methods (R22, R23, R28), plus one new `MfaService` method for recovery-code
regeneration (R49), all behind a new `MfaController` and `MfaExceptionHandler` mirroring
`ApiKeyController`/`ApiKeyExceptionHandler`'s own established pattern.

