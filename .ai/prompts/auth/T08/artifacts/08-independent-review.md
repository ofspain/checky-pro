# auth · T08 — Phase 8: Independent Code Review

Fresh, adversarial review of Phase 6 (implementation) and Phase 7 (self-review) against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. Findings only.

---

## Finding 1 — `AccountServiceTest` does not compile because `AccountService` gained a `PasswordPolicy` parameter

- **Issue:** `AccountService`'s constructor was widened to accept `PasswordPolicy` before `Clock`, but the test's `@BeforeEach` `setUp()` still invokes the old 7-argument constructor.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/AccountService.java:45-58` — constructor now expects `AccountRepository, PasswordEncoder, OutboxPublisher, AuditService, VerificationTokenService, RefreshTokenTracker, PasswordPolicy, Clock`.
  - `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java:68-70` — still calls the 7-argument form.
- **Recommendation:** Add a `@Mock PasswordPolicy passwordPolicy` field and pass it into the `AccountService` constructor in `setUp`.
- **Confidence:** High

---

## Finding 2 — `PasswordPolicyTest` has 11 stale one-argument `validate(...)` calls and an assertion that contradicts the new audit-context requirement

- **Issue:** `PasswordPolicy.validate(String)` became `validate(String, UUID, UUID)`. Every existing call in `PasswordPolicyTest` uses the old signature, so the test class does not compile. In addition, `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` currently asserts that the recorded `accountUuid` and `actorUuid` are `null`; after the signature update those fields must carry real values, so the assertion must be flipped.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java:44` — new signature.
  - `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java:49,51,61,62,69,71,73,83,94,111,119` — old one-argument calls.
  - `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java:101-102` — `assertThat(recorded.accountUuid()).isNull()` and `actorUuid()` null assertions, which conflict with frozen brief AC10.
- **Recommendation:**
  - Update all 11 call sites to pass two non-null fixture UUIDs, e.g. `UUID.fromString("11111111-1111-1111-1111-111111111111")`.
  - In the breach-check-failure audit test, assert that `accountUuid`/`actorUuid` equal the values passed into `validate` (AC10).
- **Confidence:** High

---

## Finding 3 — The Have I Been Pwned network call runs inside the change-password transaction, tying up a pooled connection for up to the configured timeout

- **Issue:** `AccountService.changePassword` is `@Transactional`. After the current-password check passes, it calls `passwordPolicy.validate(...)` which may invoke `BreachCheckClient.isBreached(...)` — a synchronous outbound HTTP request bounded by `themistra.auth.password.breach-check.timeout-ms=3000`. If HIBP is slow or unreachable, the transaction (and the database connection it holds) remains open for the duration of the timeout. This is the first production request path to exercise `PasswordPolicy`.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/AccountService.java:217` — policy validation inside the `@Transactional` method.
  - `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java:66` — synchronous breach-check call.
  - `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java:53-60` — `RestClient` call wrapped only in `RestClientException`.
  - `services/auth/src/main/resources/application.properties` (`themistra.auth.password.breach-check.timeout-ms=3000`) — current bound.
- **Recommendation:** Not a correctness defect per the frozen brief (the brief authorized wiring `PasswordPolicy` as-is), but record it as an accepted operational trade-off. If later load/connection-pool issues appear, the clean fix is to move the current-password check (which only needs `PasswordEncoder.matches`) outside a write transaction, or to perform policy validation before acquiring the write transaction. Either change would require ADR-level design work and is out of scope for T08.
- **Confidence:** Medium

---

## Finding 4 — `PasswordPolicy.recordBreachCheckFailedAudit` will silently record null actor/target again if a future caller passes null UUIDs

- **Issue:** `PasswordPolicy.validate` now accepts `accountUuid`/`actorUuid` and forwards them to the audit event, but it does not reject `null`. A future caller from task 9 (`register`/`resetPassword`) that lacks a real actor could pass `null` and reintroduce the exact audit-context gap T08 was meant to close.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java:74-77` — uses the supplied UUIDs directly without validation.
  - `services/auth/src/main/java/com/themistra/auth/audit/RecordAuditEventRequest.java:12-21` — permits nulls in its record signature.
- **Recommendation:** Add an explicit guard in `PasswordPolicy.validate` (or `recordBreachCheckFailedAudit`): `Objects.requireNonNull(accountUuid, "accountUuid must not be null")` and `Objects.requireNonNull(actorUuid, "actorUuid must not be null")`. This makes misuse fail fast at the site that introduces it, rather than producing a silently incomplete audit trail.
- **Confidence:** Low

---

## Finding 5 — `InvalidAccountStateException` detail for a non-ACTIVE account exposes the account status to the caller

- **Issue:** `AccountExceptionHandler.onInvalidState` copies `e.getMessage()` into the RFC 9457 response detail. `InvalidAccountStateException`'s message format is `"Account <uuid>: cannot change password while <STATUS>"`, so a caller whose token pre-dates a status change learns whether the account is `PENDING_VERIFICATION`, `LOCKED`, `SUSPENDED`, or `DELETED`. The frozen brief explicitly reused the existing `409`/`INVALID_STATE` mapping, so this is not an implementation deviation, but it is a state-leak trade-off worth flagging.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/InvalidAccountStateException.java:9` — message includes both UUID and current status.
  - `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java:25-32` — detail is set from `e.getMessage()`.
  - `services/auth/src/main/java/com/themistra/auth/account/AccountService.java:211-213` — non-`ACTIVE` status triggers this path.
- **Recommendation:** Accept the trade-off if the human deliberately chose to reuse the existing mapping, but record the decision explicitly. If not, introduce a dedicated exception for the change-password status rejection (e.g. `ChangePasswordNotAllowedException`) mapped to `403 Forbidden` with a constant title and no detail.
- **Confidence:** Low

---

## Finding 6 — `AccountController.changePassword` assumes `Authentication.getName()` is a valid UUID without any defensive handling

- **Issue:** The endpoint parses `authentication.getName()` with `UUID.fromString(...)`. By construction the JWT `sub` is the account UUID, but if the security wiring ever admits a non-UUID principal name here, the request fails with an unhandled `IllegalArgumentException` rather than a controlled problem response.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/account/AccountController.java:126` — direct `UUID.fromString(authentication.getName())`.
- **Recommendation:** This is defensive only; the current security configuration makes it unreachable. Optionally add an `AccountExceptionHandler` mapping for `IllegalArgumentException` (or catch and rethrow as a domain exception) so that any misconfiguration does not surface as a 500 with a stack trace.
- **Confidence:** Low
