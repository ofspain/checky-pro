# payment · T06 · Phase 2 — Task Implementation Brief

## Task

T06 — Invoice API + watch registration.

## Purpose

Give merchants a real way to create invoices over HTTP, wire that creation to Crypto Service
watch registration, and emit the `invoice.created` event — the first end-to-end flow in this
service, and the first inter-service authenticated HTTP call anywhere in the codebase.

## Scope

**In:**
- `InvoiceController.create(...)` — `POST /api/v1/invoices`, `MERCHANT`-role only.
- `CreateInvoiceRequest`/`InvoiceResponse` DTOs; `InvoiceCreatedPayload` (outbox event body).
- `InvoiceExceptionHandler` — maps `InvalidInvoiceRequestException` (T05) to `400`.
- `CryptoClient.registerWatch(...)` (new, `common` package — shared plumbing per L12/agents.md,
  not invoice-specific even though invoice is its only caller today) + its own
  `RegisterWatchRequest`/`RegisterWatchResponse` DTOs, mirroring `contracts/api/crypto-
  internal.yaml` field-for-field (`expectedAmount` serialized as a decimal **string**, never a
  JSON number, per the contract's own explicit text).
- `CryptoServiceTokenProvider` (new, `common` package) — see Open Questions below; a minimal,
  explicit client-credentials token fetch + cache component, **not** the full Spring
  `spring-security-oauth2-client` starter. Proposed, not frozen — this is the single biggest
  design decision in this task and needs real sign-off at Phase 4.
- `JwtRoleAuthoritiesConverter` + a `JwtAuthenticationConverter` bean in `ResourceServerConfig`
  — necessary implied consequence of enforcing `MERCHANT` (mirrors `auth-service`'s own
  identical class verbatim).
- `InvoiceService.create(...)` extended: call `CryptoClient.registerWatch`, store the returned
  `watchId`, emit `invoice.created` via `OutboxPublisher` — all inside the method's existing
  `@Transactional` boundary (see Constraints).

**Out:**
- `GET /api/v1/invoices`[/{invoiceUuid}] (task 7).
- `InvoiceExpirySweepJob` (task 8).
- Any `Payment` entity/row — per Phase 0/1's own confirmed reading, no payment row exists until
  `chain.tx.seen` (task 11); `CREATED`/`WATCHING` tracking is `Invoice.watchId` (null/set) only.
- `CryptoClient.attest(...)`/`unregisterWatch(...)` — only `registerWatch` is this task's scope.
- Adding `auth-service`'s own missing `internal.crypto:write` client scope — **proposed as a
  disclosed, minimal, one-line fix (see Open Questions), not silently done**; needs explicit
  sign-off since it touches a different service's config.

## Business Rules

- **R1** — `MERCHANT`-role JWT, `POST /api/v1/invoices` → `OPEN` invoice + registered watch +
  response with `invoiceUuid`/`payToAddress`.
- **R2** — invoice insert + `invoice.created` outbox write, same transaction.
- **R6** — watch-ack moves tracking `CREATED` → `WATCHING` (i.e., `Invoice.watchId` goes from
  null to the real value, synchronously within the same request).

## Locked Decisions

- **L3** — `CryptoClient` only calls Crypto Service's own REST API; no chain/KMS code here.
- **L8** — outbox write in the same transaction as the invoice insert.
- **L10** — `POST /api/v1/invoices` requires a valid JWT (already true via the existing filter
  chain) **and** the `MERCHANT` role (new — this task's own first authority-restricted path).
- **L12** — `CryptoClient`/`CryptoServiceTokenProvider`/`JwtRoleAuthoritiesConverter` are
  `common`, not `invoice`-owned.

## Dependencies

- `CryptoClientProperties` (T03).
- `OutboxPublisher`, `EventTopics` (T04).
- `Invoice`, `InvoiceService`, `InvalidInvoiceRequestException` (T05).
- `auth-service`'s own token-endpoint (`POST /oauth2/token`, client_credentials grant) — a real
  runtime dependency this task introduces for the first time.

## Inputs

- `contracts/api/crypto-internal.yaml` — `RegisterWatchRequest`/`RegisterWatchResponse` shapes,
  verbatim.
- `contracts/api/token-claims.md` — confirms `sub` = account UUID and `roles` = flat string array
  on a Path-1 (interactive) token, the shape `InvoiceController` reads from.
- `auth-service`'s own `JwtRoleAuthoritiesConverter.java` — exact template to mirror.
- `crypto-service`'s own `InvalidWatchRequestException`/`WatchExceptionHandler` — template for
  `InvoiceExceptionHandler` (already partly established at T05 for the exception class itself).

## Outputs

- `services/payment/src/main/java/com/themistra/payment/invoice/{InvoiceController,
  InvoiceExceptionHandler}.java`, `invoice/dto/{CreateInvoiceRequest,InvoiceResponse}.java`,
  `invoice/event/InvoiceCreatedPayload.java` (new).
- `services/payment/src/main/java/com/themistra/payment/common/{CryptoClient,
  CryptoServiceTokenProvider,JwtRoleAuthoritiesConverter}.java`,
  `common/crypto/{RegisterWatchRequest,RegisterWatchResponse}.java` (new).
- `InvoiceService.java` (modified), `ResourceServerConfig.java` (modified).
- `pom.xml` (modified — `spring-security-oauth2-client` is explicitly NOT added; no new
  dependency needed for the proposed minimal token-provider approach).

## State Changes

- `Invoice.watchId`: `null` → a real `UUID` (`CREATED` → `WATCHING`), on a successful
  `CryptoClient.registerWatch` call, within the same request/transaction as the insert.

## Files to Create

- `invoice/InvoiceController.java`
- `invoice/InvoiceExceptionHandler.java`
- `invoice/dto/CreateInvoiceRequest.java`
- `invoice/dto/InvoiceResponse.java`
- `invoice/event/InvoiceCreatedPayload.java`
- `common/CryptoClient.java`
- `common/CryptoServiceTokenProvider.java`
- `common/JwtRoleAuthoritiesConverter.java`
- `common/crypto/RegisterWatchRequest.java`
- `common/crypto/RegisterWatchResponse.java`
- `src/test/java/.../invoice/InvoiceControllerTest.java`
- `src/test/java/.../invoice/InvoiceServiceTest.java` (extended, not new)
- `src/test/java/.../common/CryptoClientTest.java`

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql` (immutable).
- Anything under `spec/`.
- `services/auth/**` — **proposed** as a disclosed follow-up, not done silently (Open Question 1).

## Acceptance Criteria

- **AC1 (R1/L10)** — a non-`MERCHANT` or unauthenticated caller gets `401`/`403` before
  `InvoiceService.create` runs.
- **AC2 (R1/R6)** — a successful call registers a watch and sets `Invoice.watchId` from the real
  response.
- **AC3 (R2)** — the invoice insert and the `invoice.created` outbox row are in one transaction;
  a `registerWatch` failure rolls back the whole transaction — no invoice persists.
- **AC4 (R1)** — the `201`/`200` response body carries `invoiceUuid` and `payToAddress`.
- **AC5** — `CryptoClient` sends `expectedAmount` as a decimal string, never a JSON number.

## Required Tests

- `shouldCreateInvoiceAndRegisterWatch` (R1).
- `shouldEmitInvoiceCreatedInSameTransactionAsInsert` (R2).
- `shouldTransitionCreatedToWatchingOnWatchAck` (R6).
- `shouldRejectNonMerchantCallerWithForbidden` (AC1, implied).
- `shouldRollBackInvoiceCreationWhenWatchRegistrationFails` (AC3, implied).

## Constraints

- **Transaction:** `InvoiceService.create` keeps the DB transaction open across the synchronous
  `CryptoClient.registerWatch` HTTP call. **Deliberate, disclosed deviation from the general
  "don't hold a transaction across a network call" best practice** — R1/R2's own text describes
  one atomic, synchronous flow (create invoice, register watch, emit event, return), and no
  compensating-transaction/saga pattern exists anywhere in this spec. Accepted as the cost of
  R1/R2's own explicit requirement; flagged for Phase 3 challenge.
- **Security:** `POST /api/v1/invoices` requires `ROLE_MERCHANT` (method or path-level
  `@PreAuthorize`/`hasAuthority`, Phase 5 decides which).
- **Module boundaries:** `CryptoClient`/`CryptoServiceTokenProvider` are `common`, callable by
  any feature module.
- **Null handling:** `CreateInvoiceRequest` fields are bean-validated (`@NotNull`/`@NotBlank`);
  `InvoiceService.create`'s own existing null-guards (T05) stay as the second line of defense.

## Open Questions

**Not "no blockers" — two genuine architecture decisions need real sign-off before Phase 4 freezes:**

1. **How `CryptoClient` authenticates.** Proposed: a minimal, explicit
   `CryptoServiceTokenProvider` (manual `client_credentials` POST to `auth-service`'s own
   `/oauth2/token`, in-memory token cache with expiry-aware refresh) rather than adding the full
   `spring-security-oauth2-client` starter — consistent with this codebase's established
   preference for explicit beans over autoconfiguration magic (`KafkaProducerConfig`'s own
   precedent, T04). **Alternative:** add `spring-security-oauth2-client` and use Spring's own
   `OAuth2AuthorizedClientManager` — more framework-idiomatic, more moving parts, a new
   dependency. Needs a real decision, not a silent Phase 2 call.
2. **Whether to fix `auth-service`'s own missing `internal.crypto:write` scope grant for
   `payment-service` now.** Confirmed real gap (Phase 0). Proposed: a one-line, low-risk,
   disclosed addition to `services/auth/src/main/resources/application.properties`'s existing
   `themistra.auth.clients.services[0].scopes` list — mirrors this session's own precedent
   (crypto-service task 30, added as a disclosed prerequisite for intelligence-service).
   **Alternative:** defer, and stub/skip real end-to-end testing of `CryptoClient` against a real
   `auth-service` token until that scope is granted by whoever owns that change. Needs sign-off
   since it touches a different service's file, even inside this same monorepo.
