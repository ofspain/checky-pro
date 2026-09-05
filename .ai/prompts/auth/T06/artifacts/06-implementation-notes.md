# auth · T06 — Phase 6: Implementation Notes

## What changed

**Created:**
- `account/dto/VerifyEmailRequest.java` — `@NotBlank String token`.
- `account/dto/ResendVerificationRequest.java` — `@NotBlank @Email String email`.
- `account/event/EmailRequestedEventPayload.java` — `accountUuid`, `purpose` (plain `String`, not
  the internal `VerificationToken.Purpose` enum), `token` (raw), `occurredAt`. Overrides
  `toString()` to omit `token` (Finding 1's mitigation).

**Modified:**
- `common/ProblemTypes.java` — added `INVALID_TOKEN`.
- `events/EventTopics.java` — `TOPIC_BY_AGGREGATE_TYPE` gained
  `"verification-token" -> "auth.email.requested"` (replacing the placeholder comment).
- `common/PublicEndpoints.java` — `METHOD_SCOPED` gained both new paths.
- `account/AccountService.java`:
  - Constructor now also takes `VerificationTokenService` (new field).
  - `register(...)` now calls the new private `issueAndEmitVerificationEmail(saved)` after a
    successful save, before returning — same transaction (R3).
  - New public `activateFromVerificationToken(String rawToken)` — consumes the token via T05's
    service, checks `PENDING_VERIFICATION` *before* calling `account.activateEmail()` (Finding 2),
    reuses the existing `publishLifecycleEvent`/`recordAudit` private helpers with `actorUuid` =
    the account's own UUID (Finding 5).
  - New public `resendVerificationIfPending(String email)` — no-op unless the email resolves to a
    `PENDING_VERIFICATION` account, in which case it calls the same
    `issueAndEmitVerificationEmail` helper.
  - New private `issueAndEmitVerificationEmail(Account)` — shared by both callers above; sets the
    outbox `aggregateId` to the account UUID (Finding 6).
  - New nested `public static class VerificationTokenRejectedException extends RuntimeException`
    — the single exception type for every R5 rejection reason.
- `account/AccountExceptionHandler.java` — new `@ExceptionHandler` mapping
  `AccountService.VerificationTokenRejectedException` to `400 Bad Request` /
  `ProblemTypes.INVALID_TOKEN`, fixed title, no variable detail.
- `account/AccountController.java` — two new endpoints: `POST /accounts/verify-email` (returns
  `204` on success, lets the rejection exception propagate to the handler) and
  `POST /accounts/resend-verification` (always returns `RegistrationAcknowledgement.standard()`).

No other files touched. No new top-level exception file (nested in `AccountService`, per the
plan) and no new acknowledgement DTO (reused `RegistrationAcknowledgement` unmodified).

## Mapping to the plan

Matches `artifacts/05-implementation-plan.md` exactly — method signatures, the private-method flow
for `activateFromVerificationToken`/`resendVerificationIfPending`/`issueAndEmitVerificationEmail`,
and the file list all as planned.

## Mapping to acceptance criteria (frozen brief §Acceptance Criteria)

- **R3:** `register` still returns its unchanged ack; `issueAndEmitVerificationEmail` runs inside
  the same `@Transactional register(...)` method, so the outbox row is written in the same
  transaction as account creation.
- **R4:** `activateFromVerificationToken` activates the account and calls
  `publishLifecycleEvent(account, "user.registered")` on success.
- **R5 (including Finding 2's specific case):** every rejection path —
  `verificationTokenService.consume(...)` returning empty, *or* the new status check finding
  anything other than `PENDING_VERIFICATION` — throws the identical
  `VerificationTokenRejectedException`, mapped to the identical `400`/`INVALID_TOKEN` response.
  `account.activateEmail()` is only ever called after the status check passes, so its own
  `InvalidAccountStateException` guard is structurally unreachable from this call path.
- **R6 (as modified):** `resendVerificationIfPending` only calls
  `issueAndEmitVerificationEmail` inside the `.filter(...).ifPresent(...)` chain — no state change,
  no branch the controller could observe, for any non-match.
- **R44:** `EventTopics.forAggregateType("verification-token")` now returns
  `"auth.email.requested"`.
- **L11:** both paths registered in `PublicEndpoints.METHOD_SCOPED`; confirmed
  `SecurityChainsConfig` consumes that list automatically (no change needed there) and the
  ArchUnit rule doesn't enumerate the list's contents (no change needed there either).
- **Finding 1 mitigations:** `EmailRequestedEventPayload.toString()` omits `token`; no log
  statement anywhere in the changed files references a raw token.
- **Finding 5:** `recordAudit("account.activated", account.getAccountUuid(),
  account.getAccountUuid())` — `actorUuid` is the account's own UUID for the self-service path,
  distinct from the admin path's `activateEmail(UUID, UUID)`, which is untouched and still takes
  the admin's UUID as `actorUuid`.
- **Finding 6:** `issueAndEmitVerificationEmail` passes `account.getAccountUuid().toString()` as
  the outbox `aggregateId`.

## Deviations from the plan

None. Implementation matches the plan's signatures and flow descriptions exactly.

## Build verification

Same situation as every prior task in this chain: `mvn -pl services/auth compile` still fails on
the pre-existing, unrelated `token` package issue. Verified the nine new/changed production files
independently via targeted `javac` against the module's resolved dependency classpath
(`-sourcepath services/auth/src/main/java`) — this pulls in only the transitive chain these files
actually reference (`account.*`, `events.*`, `common.*`, plus T05's `VerificationTokenService` and
T03's unrelated `PasswordPolicy` sibling files, none of which touch `token`). **Compiled with zero
errors.**

**Not run in this phase** (Phase 10 scope, per guardrails): unit/controller tests for the new
endpoints and `AccountService` methods, and therefore `mvn test`.
