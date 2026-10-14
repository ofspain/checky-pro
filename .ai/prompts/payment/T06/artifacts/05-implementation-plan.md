# payment · T06 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## Files to create

1. `common/crypto/RegisterWatchRequest.java`, `common/crypto/RegisterWatchResponse.java`
2. `common/config/CryptoClientProperties.java` — **modified, not created** (adds nested `Auth`)
3. `common/CryptoServiceTokenProvider.java`
4. `common/CryptoClient.java`
5. `common/JwtRoleAuthoritiesConverter.java`
6. `invoice/CryptoServiceUnavailableException.java`
7. `invoice/dto/CreateInvoiceRequest.java`, `invoice/dto/InvoiceResponse.java`
8. `invoice/event/InvoiceCreatedPayload.java`
9. `invoice/InvoiceController.java`
10. `invoice/InvoiceExceptionHandler.java`

(Tests — `InvoiceControllerTest`, `CryptoClientTest`, `CryptoServiceTokenProviderTest`, the
`InvoiceServiceTest` signature update — are Phase 10, not this phase.)

## Files to modify

- `common/config/CryptoClientProperties.java` — add `Auth(tokenUri, clientId, clientSecret)`.
- `common/ResourceServerConfig.java` — `@EnableMethodSecurity`, wire the converter.
- `invoice/Invoice.java` — `create(...)` gains a `watchId` parameter.
- `invoice/InvoiceService.java` — `computeExpiresAt()` (new); `create(...)` signature change
  (`invoiceUuid`, `expiresAt`, `watchId` now parameters); outbox emit added.
- `application.properties` — 3 new `themistra.payment.crypto.auth.*` properties.

## Public methods (signatures)

- `RegisterWatchRequest(UUID invoiceUuid, String chain, String address, String
  tokenContractAddress, String expectedAmount, Instant expiresAt)` — record; `expectedAmount` is
  `String` (AC5), built by the caller via `amount.toPlainString()`.
- `RegisterWatchResponse(UUID watchId, String status)` — record.
- `CryptoClientProperties.Auth(String tokenUri, String clientId, String clientSecret)` — record.
- `CryptoServiceTokenProvider.getAccessToken(): String`.
- `CryptoClient.registerWatch(UUID invoiceUuid, String chain, String address, String
  tokenContractAddress, BigDecimal expectedAmount, Instant expiresAt): RegisterWatchResponse` —
  throws `CryptoServiceUnavailableException` on any transport/non-2xx failure.
- `JwtRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>>` —
  `convert(Jwt): Collection<GrantedAuthority>`.
- `CryptoServiceUnavailableException(String message, Throwable cause)` — public, extends
  `RuntimeException`.
- `CreateInvoiceRequest(String chain, String tokenContractAddress, short tokenDecimals,
  BigDecimal amount, String payToAddress, String reference)` — record, bean-validated.
- `InvoiceResponse(UUID invoiceUuid, String payToAddress)` — record.
- `InvoiceCreatedPayload(UUID invoiceUuid, UUID merchantAccountUuid, String chain, String
  tokenContractAddress, short tokenDecimals, String amount, String payToAddress, String
  reference, Instant expiresAt)` — record; `amount` is `String` (decimal, L6-safe).
- `InvoiceController.create(CreateInvoiceRequest, Jwt): ResponseEntity<InvoiceResponse>` —
  `@PostMapping`, `@PreAuthorize("hasRole('MERCHANT')")`.
- `InvoiceService.computeExpiresAt(): Instant` (new, not `@Transactional`).
- `InvoiceService.create(UUID invoiceUuid, UUID merchantAccountUuid, String chain, String
  tokenContractAddress, short tokenDecimals, BigDecimal amount, String payToAddress, String
  reference, Instant expiresAt, UUID watchId): Invoice` — signature change; `@Transactional`.
- `Invoice.create(..., UUID watchId, Instant now): Invoice` — signature change.

## Private methods

- `CryptoServiceTokenProvider`:
  - `private CachedToken fetchNewToken()` — POSTs `grant_type=client_credentials` with HTTP
    Basic auth (`clientId`/`clientSecret`) to `tokenUri`; parses `access_token`/`expires_in`.
  - `private record CachedToken(String accessToken, Instant expiresAt)` with
    `isValidAt(Instant now)` (a small safety margin before real expiry, e.g. 30s).
- `CryptoClient`:
  - `private HttpHeaders authHeader()` — `Authorization: Bearer <token>` via
    `tokenProvider.getAccessToken()`.
- `InvoiceService` (unchanged from T05): `validateChainAndTokenAddress`, `validateAmount`.

## Entities used

- `Invoice` (T05, extended).

## Repositories used

- `InvoiceRepository` (T05, unchanged).

## Services used

- `InvoiceController` depends on: `InvoiceService`, `CryptoClient`.
- `InvoiceService` depends on (unchanged + extended): `InvoiceRepository`, `InvoiceProperties`,
  `Clock`, plus (new) `OutboxPublisher`.
- `CryptoClient` depends on: `CryptoClientProperties`, `CryptoServiceTokenProvider`,
  `RestClient.Builder`.
- `CryptoServiceTokenProvider` depends on: `CryptoClientProperties.Auth`, `Clock`,
  `RestClient.Builder`.

## Unit/integration tests required (Phase 10, not this phase)

- `InvoiceControllerTest` (`@WebMvcTest` or plain MockMvc + mocked `InvoiceService`/
  `CryptoClient`): `shouldCreateInvoiceAndRegisterWatch`,
  `shouldTransitionCreatedToWatchingOnWatchAck`, `shouldRejectNonMerchantCallerWithForbidden`,
  `shouldReturnServiceUnavailableWhenWatchRegistrationFails`,
  `shouldReturnProblemJsonForInvalidInvoiceRequest`.
- `InvoiceServiceTest` (extended): `shouldEmitInvoiceCreatedInSameTransactionAsInsert` (new,
  verifies `OutboxPublisher.publish` is called with the right args inside the same call).
- `CryptoClientTest` (MockRestServiceServer or WireMock, per `package.md` §8's own stated
  convention): request/response shape conformance (AC5 — amount as string), error mapping to
  `CryptoServiceUnavailableException`.
- `CryptoServiceTokenProviderTest`: `shouldReuseCachedTokenUntilExpiryAndRefreshOnceUnderConcurrency`
  (AC7).

## Execution order

1. `common/config/CryptoClientProperties.java` (extend with `Auth`).
2. `common/crypto/{RegisterWatchRequest,RegisterWatchResponse}.java`.
3. `common/CryptoServiceTokenProvider.java`.
4. `common/CryptoClient.java`.
5. `common/JwtRoleAuthoritiesConverter.java`.
6. `common/ResourceServerConfig.java` (wire method security + converter).
7. `invoice/Invoice.java` (extend factory).
8. `invoice/InvoiceService.java` (extend: `computeExpiresAt`, `create` signature, outbox emit).
9. `invoice/CryptoServiceUnavailableException.java`.
10. `invoice/dto/{CreateInvoiceRequest,InvoiceResponse}.java`.
11. `invoice/event/InvoiceCreatedPayload.java`.
12. `invoice/InvoiceController.java`.
13. `invoice/InvoiceExceptionHandler.java`.
14. `application.properties` (3 new properties).
15. Compile-check (`mvn -pl services/payment -am compile`) before any test is written.
16. *(Phase 10)* all tests above.
