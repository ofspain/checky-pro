# payment · T05 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T05/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`, `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`, `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueFloatingPointMoneyUser.java`  
Scope: T05 only. Recommendations only — no rewrites.

---

### Verification baseline

- `mvn -pl services/payment test` passes — **110/110 green**.
- `InvoiceServiceTest` covers AC1/R3, AC2/R4, AC4, and the brief's defensive-null distinction.
- `ArchitectureTest` covers L6 with a plain-`ArchRule` + `@Test` canary and a genuine-violation fixture.

---

### Gap 1 — `InvoiceServiceTest` happy path does not verify `repository.save(...)` is invoked

- **Why it matters:** `shouldCreateInvoiceWithOpenStateAndComputedExpiry` stubs `repository.save(...)` to return its argument, but it never verifies the repository was called. A future refactor that constructs an `Invoice` directly and returns it without persisting would still pass every assertion in this test.
- **Suggested test:** Add `verify(repository).save(invoice)` to the happy-path test (or assert that the returned invoice is the same instance produced by the stub).

---

### Gap 2 — No boundary test that a 78-digit whole amount is accepted

- **Why it matters:** `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` proves 79 digits are rejected, but there is no positive case at the exact precision boundary. A subtle off-by-one bug in the digit-count logic would not be caught.
- **Suggested test:** Add a case to `shouldAcceptWholeNumberAmountWithTrailingZerosAndRejectByTrueDigitCountNotReducedPrecision` (or a new test) that creates an invoice with a 78-digit whole number and asserts success.

---

### Gap 3 — `@Transactional` on `InvoiceService.create` is not tested

- **Why it matters:** AC5 requires `create(...)` to be `@Transactional` (forward-looking for task 6's outbox emit). The current unit test does not load a Spring context, so removing the annotation would leave the suite green.
- **Suggested test:** Add a lightweight source-text guard in `ApplicationPropertiesContentTest` or a dedicated canary that reads `InvoiceService.java` and asserts the method carries `@Transactional`, or load `InvoiceService` in a minimal `ApplicationContextRunner` and assert the bean's class/method is proxyable.

---

### Gap 4 — `ArchitectureTest` does not check constructors for forbidden floating-point parameters

- **Why it matters:** The custom condition scans fields, method return types, and method parameters, but constructors are not included in `JavaClass.getMethods()`. A future DTO introduced in task 6 with a constructor like `CreateInvoiceRequest(double amount)` would evade this rule.
- **Suggested test:** Extend the custom ArchUnit condition to also iterate `javaClass.getConstructors()` and check each constructor's parameter raw types against the forbidden set. Update `RogueFloatingPointMoneyUser` with a violating constructor parameter to keep the canary honest.

---

### Gap 5 — `ArchitectureTest` does not catch floating-point array types

- **Why it matters:** The forbidden-type set contains the scalar names only. `double[]` has raw type name `[D`, `Double[]` has `[Ljava.lang.Double;`, etc. A field or parameter declared as a floating-point array would not be flagged, even though it still carries floating-point money.
- **Suggested test:** Expand `FORBIDDEN_FLOATING_POINT_TYPES` to include the array type names (`[F`, `[D`, `[Ljava.lang.Float;`, `[Ljava.lang.Double;`) and add array-field/array-parameter cases to `RogueFloatingPointMoneyUser`.

---

### Gap 6 — `Invoice` JPA mapping is still not empirically verified against a real database

- **Why it matters:** `ddl-auto=validate` only surfaces mapping mistakes at Spring context boot against Postgres. The Phase 10 suite is pure unit/Mockito plus ArchUnit; no `@DataJpaTest` or Testcontainers test boots the entity against the real schema. This is the same carried-forward gap noted in Phase 7 finding 1.
- **Suggested test:** Either add a `@DataJpaTest` (Testcontainers-backed) that saves and reads an `Invoice`, or ensure task 6's first DB-backed invoice test exercises this as a side effect and close this gap explicitly there.

---

### Gap 7 — `Invoice` lifecycle fallback (`@PrePersist`/`@PreUpdate`) is not exercised

- **Why it matters:** The fallback uses `Instant.now()` rather than the injected `Clock`. The normal path sets timestamps from the `Clock`, so this is currently unreachable. If the fallback is retained, there is no test proving it works; if it is considered dead code, there is no test enforcing that all persistence goes through the factory method.
- **Suggested test:** Either add a test that constructs an `Invoice` without timestamps and triggers `@PrePersist` to assert non-null timestamps, or add an ArchUnit/source-text rule that forbids direct `Invoice` default-constructor usage outside the `invoice` package.
