# payment · T10 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T10/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `VerificationRecordServiceTest.java`, `VerificationRecordRepositoryIntegrationTest.java`.

---

## Executive summary

- `VerificationRecordServiceTest` — **11/11 passing** locally. Strong unit coverage of transitions, reversals, ignored events, null handling, detail immutability, and the append-only source-text guard.
- `VerificationRecordRepositoryIntegrationTest` — **2 tests written but not run locally** because Docker is unavailable (disclosed carried-forward gap).
- Full non-Docker suite — **194/194 passing**.
- Remaining gaps are minor: the unmodifiable nature of `getDetail()` is only partially proven, and the integration tests are unverified until Docker is available.

---

## Gap 1 — `getDetail()` unmodifiability is not fully proven

- **Why it matters:** `VerificationRecord` now defensively copies the `detail` map on input and output using an unmodifiable `LinkedHashMap`. The service test proves that a caller cannot mutate the map *before* persistence, but it does not prove that a consumer cannot mutate the map *after* retrieving it from a persisted/loaded entity.
- **Suggested test:** Add a unit test in `VerificationRecordServiceTest` (or a small `VerificationRecordTest`) that calls `record.getDetail()` and asserts `assertThatThrownBy(() -> result.put("x", "y")).isInstanceOf(UnsupportedOperationException.class)`.
- **Confidence:** Low.

---

## Gap 2 — Integration tests cannot be run locally this session

- **Why it matters:** The JSONB round-trip test is the first real validation of the Hibernate 6 JSON mapping and the first `verification_records` foreign-key seeding. It is well-written, but it has never executed in this environment.
- **Suggested follow-up:** Run `VerificationRecordRepositoryIntegrationTest` in an environment with Docker as soon as possible. If it fails, the most likely causes are (a) Flyway running as `payment_app` before the admin `@BeforeAll` migration, or (b) JSON deserialization producing a map implementation that `containsExactlyInAnyOrderEntriesOf` does not consider equal to the original.
- **Confidence:** Medium — the code looks correct, but unverified.

---

## Gap 3 — `repository.save` invocation count is not asserted

- **Why it matters:** The service tests use `when(repository.save(...)).thenAnswer(...)` but never `verify(repository, times(1)).save(...)`. A broken implementation that returns a hand-constructed object without calling `repository.save` would still pass every service test.
- **Suggested test:** Add `verify(repository).save(any(VerificationRecord.class))` to one or two representative tests (e.g., `shouldRecordAnOrdinaryForwardTransition` and `shouldIgnoreOutOfOrderOrStaleChainEvents`).
- **Confidence:** Low.

---

## Gap 4 — Source-text guard regex could silently miss indirect delete calls

- **Why it matters:** The append-only test checks for literal substrings `.delete(`, `.deleteById(`, `.deleteAll(`, and `.update(`. This catches the obvious paths, but it will not catch a method reference such as `repository::deleteById`, a static import like `deleteById(repository, id)`, or a comment that accidentally contains the substring. The risk is low because the repository is package-private and only used by the service.
- **Suggested improvement:** Keep the source-text test as a fast guard, but add a structural ArchUnit rule in `ArchitectureTest` that classes in `com.themistra.payment.verification` must not call any method whose name starts with `delete` on `VerificationRecordRepository`.
- **Confidence:** Low.

---

## Gap 5 — No test proves `record` accepts a null `fromState`

- **Why it matters:** The integration test covers a null `fromState` for the ignored-event shape, but the `record` method also accepts `fromState = null` for any transition. A regression that accidentally adds `Objects.requireNonNull(fromState, ...)` in `record` would break the first-ever recording of a payment or any caller that legitimately passes null.
- **Suggested test:** Add a service test `shouldRecordTransitionWithNullFromState` that calls `service.record(PAYMENT_ID, null, PaymentState.CREATED, ...)` and asserts `saved.getFromState()` is null.
- **Confidence:** Low.

---

## Positive confirmations

- The test class correctly constructs the service in `@BeforeEach`, avoiding the field-initializer `NullPointerException` bug the author caught during writing.
- `shouldIgnoreOutOfOrderOrStaleChainEvents` correctly asserts `fromState == null`, closing Phase 3 Finding 1.
- `shouldNotBeAffectedByCallerMutatingTheDetailMapAfterRecording` is a proper regression guard for the Phase 8 mutability finding.
- The null-argument tests assert both the exception type and the parameter name in the message.
- `shouldPersistConfirmationCountWhenProvided` is a sensible extra covering the otherwise-untested `confirmations` field.
- `verificationPackageProductionFilesShouldNeverCallDeleteOrUpdate` covers all three production files, closing Phase 3 Finding 2.
- The integration test seeds a real `payments.invoices` → `payments.payments` row, respecting the real foreign key instead of using placeholder IDs.
- Mutation verification in Phase 10 shows the tests catch real regressions.

---

## Recommended follow-up order

1. Run `VerificationRecordRepositoryIntegrationTest` in a Docker-enabled environment.
2. Add the `getDetail()` unmodifiable-output assertion.
3. Add `verify(repository).save(...)` to a couple of service tests.
4. Consider adding the null-`fromState` transition test and the ArchUnit structural guard.
5. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
