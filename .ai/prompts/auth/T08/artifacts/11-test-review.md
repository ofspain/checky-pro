# auth · T08 — Phase 11: Test Review

Adversarial review of Phase 10 tests against the frozen brief acceptance criteria. All 55 affected tests compile and pass, but several gaps in assertion strength remain.

---

## Gap 1 — Success path does not prove `passwordEncoder.encode` received the raw new password

- **Why it matters:** `AC1` (R11) and `AC4` (L3) require that the new password is hashed through the configured `PasswordEncoder`. The current success test only asserts that the account ends up holding the stubbed encoder output; a broken implementation could call `encode("anything")` and still pass, because Mockito returns the stubbed value regardless of input.
- **Suggested test:** In `AccountServiceTest.shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword`, add:
  ```java
  verify(passwordEncoder).encode("new-correct-horse");
  ```

---

## Gap 2 — No test proves the strict gate ordering required by the frozen brief

- **Why it matters:** `Constraints` state the order is fixed: status check → current-password check → policy check → mutation → audit. The current tests verify that individual gates are hit, but none prove they execute in the required sequence. A regression could reorder `passwordPolicy.validate` and `passwordEncoder.encode` without failing any existing test.
- **Suggested test:** In `AccountServiceTest`, add an `InOrder` assertion to the success test:
  ```java
  InOrder inOrder = inOrder(passwordEncoder, passwordPolicy);
  inOrder.verify(passwordEncoder).matches("current-password", ENCODED);
  inOrder.verify(passwordPolicy).validate("new-correct-horse", accountUuid, accountUuid);
  inOrder.verify(passwordEncoder).encode("new-correct-horse");
  ```
  Also add a separate `InOrder`-based test for the policy-violation path that proves `matches` happens before `validate` and that `encode` is never reached.

---

## Gap 3 — No unit test for `ChangePasswordRequest` bean validation

- **Why it matters:** `ChangePasswordRequest` is a new boundary DTO with `@NotBlank` on both fields. A dedicated validation test prevents a future change (e.g., dropping `@NotBlank`, changing to `@NotNull`, or adding `@Size`) from silently altering the API contract. Other DTOs in this module (`RegisterAccountRequest`, `PasswordResetConfirmRequest`) already follow this pattern.
- **Suggested test:** Add `account/dto/ChangePasswordRequestValidationTest.java` that asserts:
  - `new ChangePasswordRequest("current", "new")` produces no violations.
  - `new ChangePasswordRequest("", "new")`, `new ChangePasswordRequest("current", "")`, and `new ChangePasswordRequest(null, null)` each produce a violation on the expected field.

---

## Gap 4 — Identical-current/new-password test does not verify the audit event

- **Why it matters:** `AC9` confirms that re-submitting the current password succeeds, but the success path also carries `AC6` (exactly one `password.changed` audit row with `accountUuid == actorUuid`). The identical-password test only asserts the hash changed and `passwordPolicy.validate` was called; it does not close the loop on auditing for this specific scenario.
- **Suggested test:** In `AccountServiceTest.shouldAllowNewPasswordIdenticalToCurrentPassword`, mirror the audit assertions from `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword`:
  ```java
  ArgumentCaptor<RecordAuditEventRequest> auditCaptor = ...;
  verify(auditService).record(auditCaptor.capture());
  assertThat(auditCaptor.getValue().eventType()).isEqualTo("password.changed");
  assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(accountUuid);
  assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(accountUuid);
  ```

---

## Gap 5 — No test proves `CurrentPasswordMismatchException` produces a stable problem body

- **Why it matters:** The handler maps this exception to a fixed `400`/`CURRENT_PASSWORD_MISMATCH` response. The existing test only checks one constructed instance; a regression that adds variable detail (e.g., the account UUID or timestamp) would not be caught.
- **Suggested test:** In `AccountExceptionHandlerTest`, add a test analogous to `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` that constructs two independent `CurrentPasswordMismatchException`s and asserts the resulting `ProblemDetail` status/type/title/detail are byte-for-byte equal.

---

## Gap 6 — No integration/MockMvc test exercises the full endpoint through Spring's dispatcher

- **Why it matters:** `AccountControllerTest` constructs the controller directly with a mocked `AccountService`, and `AccountExceptionHandlerTest` tests the advice methods in isolation. Neither proves that Spring's dispatcher actually applies the exception handler to exceptions thrown from `/accounts/me/password`, or that `@Valid` on `ChangePasswordRequest` yields the expected `400`/`VALIDATION_ERROR` body.
- **Suggested test:** Add a lightweight `@WebMvcTest(AccountController.class)` test for `POST /accounts/me/password` that uses `MockMvc` with a mocked `AccountService` and the real `AccountExceptionHandler`. Cover:
  - `204` on success.
  - `400`/`CURRENT_PASSWORD_MISMATCH` on wrong current password.
  - `400`/`VALIDATION_ERROR` on a blank `newPassword` in the request body.
  - `409`/`INVALID_STATE` when `AccountService` throws `InvalidAccountStateException`.

---

## Gap 7 — Breach-check-failure path emits a noisy stack-trace log during the test run

- **Why it matters:** `shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen` deliberately causes `AuditService.record` to throw and then asserts that `PasswordPolicy.validate` still succeeds. That is the correct behavior, but the production `recordBreachCheckFailedAudit` logs the exception at WARN with a full stack trace. In a test run this prints a long stack trace that looks like a failure, and in production it creates noisy logs for an expected, handled fail-open path.
- **Suggested test:** Update `PasswordPolicyTest.shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen` to capture `log.warn` output (e.g., with a `@LogCaptor` or a temporary in-memory appender) and assert that:
  - A single WARN message is emitted.
  - The message contains `password.breach_check_failed`.
  - The message does **not** contain the raw password.
  This is also a signal to consider logging the audit failure without a full stack trace in production.
