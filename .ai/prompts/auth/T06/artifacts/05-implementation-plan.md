# auth · T06 — Phase 5: Implementation Plan

Plans against `artifacts/04-frozen-task-brief.md` only. Every file traces to that brief's Files to
Create / Files to Modify. No new exception file: `VerificationTokenRejectedException` is a
`public static` nested class inside `AccountService.java` (already in Files to Modify) — the
brief's Files to Create list doesn't authorize a new top-level exception file, so it follows the
T03/T05 nested-type precedent rather than this package's older convention of standalone exception
files (`AccountNotFoundException.java` etc.). `resend-verification`'s response reuses the existing
`RegistrationAcknowledgement.standard()` unmodified — no new DTO, no edit to a file outside the
brief's authorization.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/account/dto/VerifyEmailRequest.java`
2. `services/auth/src/main/java/com/themistra/auth/account/dto/ResendVerificationRequest.java`
3. `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java`
4. Mirrored tests: `AccountServiceTest` additions (existing file, extended — see Files to Modify),
   plus a new `AccountControllerTest` extension (existing file, extended).

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` — two new
   endpoints.
2. `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `register`
   gains event emission; two new public methods; one nested exception class.
3. `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java` — one
   new `@ExceptionHandler`.
4. `services/auth/src/main/java/com/themistra/auth/events/EventTopics.java` — one new map entry.
5. `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` — two new
   `MethodScoped` entries.
6. `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — one new constant.
7. `services/auth/src/main/java/com/themistra/auth/account/AccountServiceTest.java` (existing) —
   new test methods.
8. `services/auth/src/main/java/com/themistra/auth/account/AccountControllerTest.java`
   (existing, if present — confirm at implementation time) — new test methods.
9. `services/auth/src/test/java/com/themistra/auth/events/EventTopicsTest.java` (existing) — one
   new assertion.

## Public methods (signatures)

**`VerifyEmailRequest`** (record, `account.dto`):
```java
record VerifyEmailRequest(@NotBlank String token)
```

**`ResendVerificationRequest`** (record, `account.dto`):
```java
record ResendVerificationRequest(@NotBlank @Email String email)
```

**`EmailRequestedEventPayload`** (record, `account.event`, mirrors `UserLifecycleEventPayload`'s
existing shape):
```java
record EmailRequestedEventPayload(UUID accountUuid, String purpose, String token, Instant occurredAt)
```
`purpose` is a plain `String` (`"verify_email"`, matching R3/R6's literal wording), not
`VerificationToken.Purpose` — decouples the external event payload from the internal JPA enum
(task 7 will pass `"password_reset"` through the same field later). `token` is the raw value
(Finding 1's formalized exception) — this record must **not** override `toString()` to include it
by default... actually the opposite: because records auto-generate a `toString()` that includes
every component (the exact T05 Finding-2 lesson), this record **must** override `toString()` to
omit `token`, matching the frozen brief's "no default `toString` exposure" mitigation.

**`AccountService`** (existing class, new members):
```java
@Transactional
public AccountResponse activateFromVerificationToken(String rawToken)
        throws VerificationTokenRejectedException

@Transactional
public void resendVerificationIfPending(String email)

public static class VerificationTokenRejectedException extends RuntimeException {
    public VerificationTokenRejectedException() {
        super("Verification token is invalid, expired, or already used");
    }
}
```
`register(...)`'s existing signature is unchanged; its body gains one new step (see Private
methods).

**`AccountController`** (existing class, two new handlers):
```java
@PostMapping("/verify-email")
public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request)

@PostMapping("/resend-verification")
public RegistrationAcknowledgement resendVerification(@Valid @RequestBody ResendVerificationRequest request)
```
`verifyEmail` returns `204 No Content` on success (R4 says "return success," no body is specified
or needed); `activateFromVerificationToken`'s exception propagates to `AccountExceptionHandler`
for the uniform `400`/`INVALID_TOKEN` response — no local try/catch needed here (unlike
`register`'s `DuplicateEmailException` catch), because verify-email's success (204) and failure
(400) are *supposed* to differ per R4; only failure reasons must be uniform *among themselves*
(R5), which the single exception type + single handler mapping already guarantees.
`resendVerification` never throws for a non-match — `resendVerificationIfPending` handles that
internally — so it unconditionally returns the same `RegistrationAcknowledgement.standard()`.

**`AccountExceptionHandler`** (existing class, one new handler):
```java
@ExceptionHandler(AccountService.VerificationTokenRejectedException.class)
ProblemDetail onVerificationTokenRejected(AccountService.VerificationTokenRejectedException e)
```
Returns `400 Bad Request`, `ProblemTypes.INVALID_TOKEN`, a fixed generic title/detail — never
includes the submitted token or any account-identifying detail (Finding 7).

**`EventTopics`**: `TOPIC_BY_AGGREGATE_TYPE` map gains
`"verification-token", "auth.email.requested"` (replacing the existing comment placeholder).

**`PublicEndpoints`**: `METHOD_SCOPED` gains
`new MethodScoped(HttpMethod.POST, "/accounts/verify-email")` and
`new MethodScoped(HttpMethod.POST, "/accounts/resend-verification")`.

**`ProblemTypes`**: `INVALID_TOKEN = URI.create(BASE + "invalid-token")`.

## Private methods

**`AccountService`:**
- `private void issueAndEmitVerificationEmail(Account account)` — shared by `register` and
  `resendVerificationIfPending`: calls `verificationTokenService.issue(account.getAccountUuid(),
  VerificationToken.Purpose.EMAIL_VERIFY)`, then `outboxPublisher.publish("verification-token",
  account.getAccountUuid().toString(), "email.requested", SCHEMA_VERSION,
  new EmailRequestedEventPayload(account.getAccountUuid(), "verify_email", result.rawToken(),
  clock.instant()))`.

**`register(...)` flow (modified, one new step):** unchanged duplicate-check and
`saveAndFlush(...)`, then — only on successful save, still inside the same transaction — calls
`issueAndEmitVerificationEmail(saved)` before returning `AccountResponse.from(saved)` (R3,
Finding 6's aggregate-ID-is-account-UUID requirement satisfied by using
`account.getAccountUuid().toString()` as the outbox `aggregateId`).

**`activateFromVerificationToken(String rawToken)` flow:**
1. `verificationTokenService.consume(rawToken)` → `Optional<UUID>`; empty →
   `throw new VerificationTokenRejectedException()` (Finding 2/R5 — the uniform path).
2. Resolve the `Account` via `accountRepository.findByAccountUuid(...)` (present by construction —
   T05's `consume` only resolves UUIDs of accounts it already validated as usable, but this task
   still needs the entity to check status and activate it).
3. **Status pre-check (Finding 2):** if `account.getStatus() != AccountStatus.PENDING_VERIFICATION`,
   `throw new VerificationTokenRejectedException()` — same uniform path, *before* calling
   `activateEmail()`, so the entity's own guard exception
   (`InvalidAccountStateException`) is never reached from this call path.
4. `account.activateEmail()` (entity method — now guaranteed to succeed, status already checked).
5. `publishLifecycleEvent(account, "user.registered")` (existing private helper, unchanged, reused
   — not the public `activateEmail(UUID, UUID)` method, to avoid a redundant second
   `findByAccountUuid` and to control the audit `actorUuid` explicitly per Finding 5).
6. `recordAudit("account.activated", account.getAccountUuid(), account.getAccountUuid())` (existing
   private helper, unchanged — `actorUuid` = the account's own UUID per Finding 5, distinct from
   the admin path's `activateEmail(UUID, UUID)` which passes the admin's UUID).
7. Return `AccountResponse.from(account)`.

**`resendVerificationIfPending(String email)` flow:**
1. Normalize the email (reuse the existing private `normalize(String)` helper).
2. `accountRepository.findByEmail(normalized)` → if empty, return (no-op, per Finding 4's accepted
   trade-off — no artificial delay or padding).
3. If present and `status != PENDING_VERIFICATION`, return (no-op).
4. If present and `PENDING_VERIFICATION`, call `issueAndEmitVerificationEmail(account)`.

No public method on the entity `Account` changes — `activateEmail()`'s existing guard is exactly
what Finding 2's pre-check exists to avoid triggering.

## Entities used

- `Account` (existing) — read via `findByAccountUuid`/`findByEmail`; `activateEmail()` called only
  after the status pre-check.
- No new entity.

## Repositories used

- `AccountRepository` (existing) — `findByAccountUuid`, `findByEmail`, both already present.
- `VerificationTokenRepository` — not used directly; accessed only through `VerificationTokenService`.

## Services used

- `VerificationTokenService.issue`/`consume` (T05, unchanged).
- `AuditService` (existing, via `AccountService`'s existing `recordAudit` helper).
- `OutboxPublisher`, `EventTopics` (existing; one new map entry).

## Unit / controller tests required

All unit-level (plain JUnit 5 + Mockito, no Spring context for `AccountServiceTest`, per
`agents.md`); `AccountControllerTest` (existing file, presumed `MockMvc`-based per its name and
T02-era precedent — confirmed at implementation time) covers the HTTP layer.

**`AccountServiceTest` additions:**
- `shouldActivateAccountWithValidVerificationToken` — named test; `consume` returns a UUID, status
  is `PENDING_VERIFICATION` → account becomes `ACTIVE`, `user.registered` lifecycle event
  published, audit recorded with `actorUuid` = the account's own UUID.
- `shouldRejectVerificationWhenTokenConsumeReturnsEmpty` — `consume` returns `Optional.empty()` →
  `VerificationTokenRejectedException`, no account mutation.
- `shouldRejectVerificationWhenAccountIsNotPendingVerification` (Finding 2's specific case) —
  `consume` resolves a UUID whose account is already `ACTIVE` → `VerificationTokenRejectedException`
  thrown *before* `activateEmail()` is reached; assert `account.activateEmail()`/the entity's state
  is never mutated and no `InvalidAccountStateException` is thrown or leaked.
- `shouldEmitVerifyEmailEventOnRegistration` — named test; `register` still returns its existing
  ack; `outboxPublisher.publish` called once with aggregate type `"verification-token"`, aggregate
  ID equal to the new account's UUID, and an `EmailRequestedEventPayload` containing the issued raw
  token and purpose `"verify_email"`.
- `shouldResendVerificationOnlyForPending accounts` *(verbatim spelling)* — matching
  `PENDING_VERIFICATION` email issues+emits; `ACTIVE`-account email and nonexistent email both
  produce zero interactions with `VerificationTokenService`/`OutboxPublisher`.
- `EmailRequestedEventPayload`'s `toString()` never contains the raw token (mirrors T05's
  equivalent test for `VerificationTokenResult`).

**`EventTopicsTest` addition:**
- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` — named test;
  `EventTopics.forAggregateType("verification-token")` equals `"auth.email.requested"`.

**`AccountControllerTest` additions** (confirmed: this file is plain Mockito —
`new AccountController(accountService)` with a mocked `AccountService`, calling controller methods
directly; it does **not** go through Spring's dispatcher, so `@RestControllerAdvice` exception
translation is not observable at this layer):
- `verify-email` with a mocked successful `activateFromVerificationToken` → controller returns
  `204 No Content`.
- `verify-email` where the mocked service throws `AccountService.VerificationTokenRejectedException`
  → the controller method propagates it uncaught (`assertThatThrownBy`), since no local catch is
  planned (unlike `register`'s `DuplicateEmailException` handling) — the actual HTTP translation
  to `400`/`INVALID_TOKEN` is `AccountExceptionHandler`'s responsibility, tested separately below.
- `resend-verification` → controller always returns `RegistrationAcknowledgement.standard()`
  regardless of what the mocked `resendVerificationIfPending` does internally (it's `void`, so
  there's nothing for the controller to branch on by construction).

**`AccountExceptionHandler` addition (new, direct unit test of the handler method itself, not
through the controller):**
- `onVerificationTokenRejected(...)` returns `400 Bad Request`, `ProblemTypes.INVALID_TOKEN`, and a
  fixed generic detail — this is the only place the actual HTTP status/problem-type mapping is
  verifiable without a `MockMvc`/`@SpringBootTest` slice, which is not this codebase's established
  pattern for this module (`AccountControllerTest`'s existing style is the precedent to follow, not
  to introduce a new testing style for this task alone).

**`PublicEndpoints` coverage:** confirm at implementation time whether `ArchitectureTest` (or a
sibling test) already asserts against `PublicEndpoints.METHOD_SCOPED`'s contents directly — if so,
it needs no change (the two new entries are picked up automatically); if a test enumerates the
list literally, it needs the two new patterns added.

## Execution order

1. `common/ProblemTypes.java` — add `INVALID_TOKEN` (no dependencies on anything else new).
2. `events/EventTopics.java` — add the `verification-token` mapping (no dependencies).
3. `common/PublicEndpoints.java` — add both new paths (no dependencies).
4. `account/dto/VerifyEmailRequest.java`, `account/dto/ResendVerificationRequest.java` — new DTOs.
5. `account/event/EmailRequestedEventPayload.java` — new event payload, with the overridden
   `toString()`.
6. `account/AccountService.java` — the nested exception, `issueAndEmitVerificationEmail`,
   `activateFromVerificationToken`, `resendVerificationIfPending`, and `register`'s one new step.
   Depends on steps 2, 4, 5.
7. `account/AccountExceptionHandler.java` — the new mapping. Depends on step 6 (the nested
   exception type).
8. `account/AccountController.java` — the two new endpoints. Depends on steps 4, 6.
9. Tests (`AccountServiceTest`, `AccountControllerTest`, `EventTopicsTest` additions) — depend on
   all of the above.
10. Compile/test verification, following the established precedent: direct `javac` against the
    module's resolved classpath (the `token` package compile failure remains unrelated and
    unfixed), then execute via the JUnit Platform `Launcher` API.
