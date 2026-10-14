# payment · T06 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

As of T05, `payment-service` has: the `payments` schema (9 tables, immutable), resource-server
security, 5 validated `@ConfigurationProperties` records (incl. `CryptoClientProperties` — base
URL, attest/watch paths, timeouts, all stubbed since T03 but never used by any code yet), the
outbox/Kafka pipeline (T04), and its first domain entity `Invoice` + `InvoiceService.create(...)`
(T05, unit-level only — no controller, no real DB round-trip test yet). T06 is the first task to
add: an HTTP-facing controller, an outbound HTTP client to another Themistra service, and the
first real end-to-end flow connecting all of the above.

## 2. Existing code this task touches

- **New**: `invoice/InvoiceController.java`, `invoice/dto/{CreateInvoiceRequest,
  InvoiceResponse}.java`, `invoice/event/InvoiceCreatedPayload.java`, and — not literally named
  by task 6's own text, but clearly implied by `design.md` §6's package map and L3/§4c's own
  "`CryptoClient.java` (RestClient: `/attest`, `/watches`)" — a `crypto` or `common` package
  `CryptoClient.java` plus its request/response DTOs mirroring `contracts/api/crypto-internal.yaml`.
- **Existing, extended**: `InvoiceService.create(...)` (T05) — currently persists `Invoice` only;
  this task adds the watch-registration call and the outbox emit, both inside the same method or
  a caller wrapping it.
- **Existing, reused as-is**: `OutboxPublisher` (T04, first real caller in this service),
  `EventTopics`'s `"invoice"` mapping (T04), `CryptoClientProperties` (T03, first real caller).

## 3. Established patterns to follow

- **Outbound HTTP client to an external, unauthenticated API**: `auth-service`'s own
  `BreachCheckClient` (`RestClient.Builder` + a dedicated client bean) is the only outbound-HTTP-
  client precedent anywhere in this codebase — but it calls an external, unauthenticated
  third-party API (pwnedpasswords.com), not another Themistra service.
- **Outbound HTTP client to ANOTHER Themistra service, authenticated**: **no precedent exists
  anywhere in this codebase.** This would be the first inter-service authenticated HTTP call in
  the entire system. See Known gaps below — this is a real, unresolved design question, not
  something to improvise silently.
- **RFC 9457 exception mapping**: `crypto-service`'s own per-module `@RestControllerAdvice`
  pattern (`WatchExceptionHandler`/`InvalidWatchRequestException`, confirmed at T05) is the
  template — this task is the first one with a real controller to attach `InvoiceExceptionHandler`
  to.
- **DTO validation**: no precedent yet in `payment-service` for a request DTO with
  `jakarta.validation` annotations (T03's `@ConfigurationProperties` records are config, not
  HTTP request bodies). `crypto-service`'s own `RegisterWatchRequest`
  (`@NotBlank @Pattern(regexp = "ETHEREUM|TRON")` for `chain`) is the closer template for
  `CreateInvoiceRequest`.
- **Merchant identity**: no code anywhere in `payment-service` yet extracts the caller's `sub`
  (merchant account UUID) from the validated JWT — `ResourceServerConfig` (T03) only validates
  the token, it doesn't expose a helper for reading claims. `crypto-service`'s/`auth-service`'s
  own controllers for a precedent on reading `sub` from `Authentication`/`Jwt` would need
  checking in Phase 1/2 if not already established generically.

## 4. Testing conventions

- No precedent in `payment-service` yet for a `@WebMvcTest`-based controller test with a mocked
  service layer — `ResourceServerConfigTest`/`PublicEndpointsTest` (T03) are the closest
  structural templates (security-slice tests), but neither exercises a real business controller.
- No precedent for mocking an outbound HTTP call in this service — `MockRestServiceServer` or
  WireMock would need to be introduced fresh; `package.md` §8's own text says integration tests
  "use ... a WireMock stand-in for the Crypto Service," confirming WireMock is the intended tool,
  not previously used anywhere in `payment-service`.

## 5. Known gaps / unknowns (I do not know — not assumed)

1. **How does `payment-service` obtain the `internal.crypto:write`-scoped JWT the contract
   requires?** `design.md` line 191 says only "service-to-service JWT, scope
   `internal.crypto:write`" — no implementation detail. No OAuth2 client-credentials caller
   exists anywhere in this codebase yet (confirmed by direct search). This would be new
   infrastructure, not a reuse of an existing pattern.
2. **A real, confirmed gap, not assumed: `payment-service`'s own OAuth2 client registration in
   `auth-service` does NOT have the `internal.crypto:write` scope.** Direct read of
   `services/auth/src/main/resources/application.properties` lines 54-56: `payment-service`'s
   registered scopes are `internal.accounts:read` only. Even once a client-credentials caller is
   built, it cannot obtain a token with the scope the contract requires until `auth-service`'s
   own config is changed — a cross-service touch, genuinely needed, not optional.
3. **Are `CREATED`/`WATCHING` ever persisted as `PaymentState` values on a real `payments` row,
   or are they purely conceptual, tracked only via `Invoice.watchId` (null vs. set)?** Strong
   evidence for the latter: R7's own text says "WHEN a `chain.tx.seen` event ... arrives, THEN
   the system SHALL **record a payment** in state `SEEN`" (first-ever mention of actually
   creating a payment row), and L14's own text says a second matching transaction "would still
   generate `chain.tx.seen` and create **a second** `Payment` row" (implying no `Payment` row
   exists before the first `SEEN`). The real `payments` table also has `tx_hash VARCHAR(128) NOT
   NULL` — no payment row could exist in `CREATED`/`WATCHING` without a real transaction hash to
   put there. Reading here: **no `Payment` entity/table row is created by this task; `CREATED`/
   `WATCHING` are tracked entirely via `Invoice.watchId`** (null = `CREATED`, set = `WATCHING`).
   Flagged for Phase 3 challenge — this is the single most consequential interpretation in this
   task, not something to treat as settled without adversarial review.
4. **Does `InvoiceService.create`'s existing `@Transactional` method hold the DB transaction
   open across the synchronous `CryptoClient.registerWatch` HTTP call, or is the HTTP call made
   outside any transaction, with the transaction opened only for the insert+outbox-write?** R1's
   own text ("create an invoice ... register a watch ... return the invoice") reads as a single
   synchronous flow where watch registration must succeed for invoice creation to succeed at all
   — but holding a DB transaction open across a network call is a known anti-pattern. Not
   resolved here.
5. **How is the caller's merchant account UUID (`sub`) read from the validated JWT inside the
   controller?** No existing code in this service does this yet.

Phase 1 (Specification Extraction) resolves what the spec itself answers; anything still open
after that goes to Phase 3 (Kimi design challenge) as a named finding — items 1/2/4 in
particular look like genuine architecture questions that may need a real Human Approval
decision at Phase 4, not a silent Phase 2 judgment call.
