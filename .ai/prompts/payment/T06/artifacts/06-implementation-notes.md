# payment · T06 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | `artifacts/05-implementation-plan.md` |

## Files created

- `common/crypto/RegisterWatchRequest.java`, `common/crypto/RegisterWatchResponse.java` —
  mirror `contracts/api/crypto-internal.yaml` field-for-field; `expectedAmount` is a decimal
  `String` (AC5).
- `common/CryptoServiceTokenProvider.java` — `client_credentials` POST to auth-service's own
  `/oauth2/token`, `volatile` cached token + `ReentrantLock` single-flight refresh (AC7). Never
  called from inside a `@Transactional` method (Phase 3/4 disposition).
- `common/CryptoClient.java` — `registerWatch(...)` only, this task's own scope. A `RestClient`
  request interceptor attaches the bearer token from `CryptoServiceTokenProvider` on every call.
- `common/CryptoServiceUnavailableException.java` — **placement correction, disclosed in the
  class's own Javadoc**: the frozen brief listed this under `invoice`; placing it there would
  make `common` depend on a feature module (backward per L12). Moved to `common`, the package
  that actually throws it.
- `common/JwtRoleAuthoritiesConverter.java` — mirrors `auth-service`'s own identical class
  verbatim.
- `invoice/InvoiceController.java` — orchestrates `invoiceUuid`/`expiresAt` computation →
  `CryptoClient.registerWatch` (outside any transaction) → `InvoiceService.create` (with
  `watchId` already known). `@PreAuthorize("hasRole('MERCHANT')")`.
- `invoice/InvoiceExceptionHandler.java` — `InvalidInvoiceRequestException` → `400`;
  `CryptoServiceUnavailableException` → `503`, no internal detail in the body (AC6/AC8).
- `invoice/dto/CreateInvoiceRequest.java` — bean-validated, including `tokenDecimals`
  (`@Min(0) @Max(255)`) and `reference` (`@Size(max=140)`), new this task (Phase 3 finding 5).
  No `merchantAccountUuid` field (Phase 3 finding 6 — comes from the JWT `sub` claim).
- `invoice/dto/InvoiceResponse.java` — `invoiceUuid`/`payToAddress` (AC4).
- `invoice/event/InvoiceCreatedPayload.java` — outbox event body (Phase 3 finding 9), `amount`
  as a decimal string.

## Files modified

- `common/config/CryptoClientProperties.java` — new nested `Auth(tokenUri, clientId,
  clientSecret)` record.
- `common/ResourceServerConfig.java` — `@EnableMethodSecurity`; `JwtAuthenticationConverter`
  bean wired to `JwtRoleAuthoritiesConverter`, replacing `Customizer.withDefaults()`.
- `invoice/Invoice.java` — `create(...)` gains a `watchId` parameter (no setter added — Phase 3
  finding 4 resolved by this narrower fix instead of the literal recommendation).
- `invoice/InvoiceService.java` — new `computeExpiresAt()` (plain, not `@Transactional`);
  `create(...)` signature now takes `invoiceUuid`/`expiresAt`/`watchId` as parameters instead of
  computing/generating them; emits `invoice.created` via `OutboxPublisher` after the save, inside
  the same `@Transactional` method (AC3/R2).
- `application.properties` — 3 new `themistra.payment.crypto.auth.*` properties.
- `services/auth/src/main/resources/application.properties` — `internal.crypto:write` scope
  added to `payment-service`'s client registration (commit `2fcedd0f`, applied before this
  phase, approved via `AskUserQuestion`).

## Existing tests updated (not new test authorship — Phase 10 still owns the new test files)

- `ConfigPropertiesBindingTest.java` — all 6 `CryptoClientConfig`-based test blocks extended with
  the 3 new `auth.*` property values (otherwise every one would fail `@NotNull` validation on
  the new required `auth` field).
- `InvoiceServiceTest.java` — all call sites updated for `create(...)`'s new signature; added
  `shouldComputeExpiresAtFromInjectedClockAndConfiguredTtl`, `shouldEmitInvoiceCreatedInSameTransactionAsInsert`
  (R2's own named test, now meaningfully testable since `OutboxPublisher` is wired),
  `shouldThrowNullPointerExceptionForNullInvoiceUuid`/`...NullExpiresAt` (new parameters need
  the same defensive-guard coverage T05's existing ones had) — these are necessary-consequence
  updates to an existing file's own coverage of an extended method, mirroring the established
  "update, don't newly author, when a signature changes" pattern (T04's `@EnableScheduling`
  precedent).

## Not written this phase (deliberately deferred to Phase 10)

- `InvoiceControllerTest.java`, `CryptoClientTest.java`, `CryptoServiceTokenProviderTest.java`.

## Verification performed this phase

- `mvn -pl services/payment -am compile` — clean.
- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **99/99 passing** (up from 95 before this task; the 4 added are `InvoiceServiceTest`'s own
  necessary-consequence additions above, not Phase 10 test authorship).

## Traceability to frozen brief

- AC1 (L10) — `@PreAuthorize("hasRole('MERCHANT')")` + `JwtRoleAuthoritiesConverter` wired;
  not yet behaviorally tested (Phase 10, `InvoiceControllerTest`).
- AC2 (R1/R6) — `watchId` passed directly into `Invoice.create`, never null-then-updated.
- AC3 (R2) — outbox emit added inside `InvoiceService.create`'s own transaction; tested
  (`shouldEmitInvoiceCreatedInSameTransactionAsInsert`).
- AC4 (R1) — `InvoiceController` returns `201` with `InvoiceResponse`.
- AC5 — `RegisterWatchRequest.expectedAmount` is `amount.toPlainString()`.
- AC6 — `CryptoClient`/`CryptoServiceTokenProvider` wrap all transport/non-2xx failures in
  `CryptoServiceUnavailableException`; not yet behaviorally tested (Phase 10).
- AC7 — `CryptoServiceTokenProvider`'s single-flight refresh implemented; not yet concurrency-
  tested (Phase 10).
- AC8 — `InvoiceExceptionHandler` never includes the wrapped cause's own message; not yet
  behaviorally tested (Phase 10).

No code deviates from the frozen brief except the disclosed `CryptoServiceUnavailableException`
package correction above.
