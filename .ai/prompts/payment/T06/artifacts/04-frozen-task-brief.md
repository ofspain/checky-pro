STATUS: FROZEN (approved 2026-10-14, Human Approval gate)

# payment · T06 · Frozen Task Brief

## Phase 2 Open Questions — resolved directly by the product owner (AskUserQuestion)

1. **`CryptoClient` authentication:** minimal explicit `CryptoServiceTokenProvider` (manual
   `client_credentials` POST, in-memory cache, thread-safe single-flight refresh). No new Maven
   dependency; `spring-security-oauth2-client` is NOT added.
2. **`auth-service`'s missing scope:** fixed immediately (commit `2fcedd0f`, before this brief was
   written) — `payment-service`'s client registration in `services/auth/src/main/resources/
   application.properties` now has `internal.crypto:write` in addition to its existing
   `internal.accounts:read`.

## Phase 3 disposition (9 findings, all accepted — 4 resolved by a structural redesign, not
just a patch)

**Findings 1, 2, 4, and 7 share one root cause** — the Phase 2 TIB proposed calling
`CryptoClient.registerWatch` *inside* `InvoiceService.create`'s own `@Transactional` method.
Re-reading R1/R2 at this gate: **R2's own atomicity requirement is invoice-row + outbox-event
only** ("the system SHALL write the invoice row and an `invoice.created` outbox event in the
same database transaction") — it never says the external watch-registration call must be inside
that same transaction. R1 describes a flow, not a transaction boundary.

**Redesign, superseding the TIB's own proposal:** `InvoiceController` orchestrates the flow
directly (not `InvoiceService`), calling two separate beans in sequence — never a transactional
method calling another transactional method on `this` (which would silently skip Spring's AOP
proxy and NOT be transactional at all, a related Spring gotcha worth naming here even though
this design avoids it):
1. Compute `invoiceUuid` (`UUID.randomUUID()`) and `expiresAt`
   (`InvoiceService.computeExpiresAt()` — a new, plain, non-`@Transactional` method, so the
   service stays the single owner of "how `expiresAt` is computed," computed **once**, not
   independently in two places with two slightly different `Clock` reads).
2. Call `CryptoClient.registerWatch(invoiceUuid, chain, payToAddress, tokenContractAddress,
   amount, expiresAt)` — **no DB transaction is open during this call at all.** This fully
   resolves finding 1 (no transaction held across the network call) and finding 7's ordering
   concern (the token fetch inside `registerWatch` never runs inside a transaction).
3. Call `InvoiceService.create(invoiceUuid, merchantAccountUuid, ..., expiresAt, watchId)` —
   `@Transactional`, now takes `invoiceUuid`/`expiresAt`/`watchId` as explicit parameters instead
   of generating/computing them itself. `Invoice.create(...)`'s own factory signature gains a
   `watchId` parameter directly — **no setter is added to `Invoice`** (resolves finding 4 with a
   narrower fix than the literal recommendation: the entity's existing factory-method-only, no-
   setters discipline from T05 is preserved).
4. Finding 2 (orphan watch if the transactional step fails after a successful `registerWatch`)
   is **still accepted as a documented limitation** — this redesign does not eliminate that
   failure window, it only removes the transaction-held-across-network-call risk. No saga/
   compensation this task.

**Findings 3, 5, 6, 8, 9 — accepted and folded in directly, no redesign needed:**
3. `ResourceServerConfig` gains `@EnableMethodSecurity` and wires `JwtRoleAuthoritiesConverter`
   via `.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))`.
5. `CreateInvoiceRequest` validates `tokenDecimals` (`@Min(0) @Max(255)`) and `reference`
   (`@Size(max = 140)`), in addition to `chain`/`tokenContractAddress`/`amount`/`payToAddress`.
6. `merchantAccountUuid` comes from the JWT `sub` claim, never the request body; response is
   `201 Created`.
8. Added `shouldReturnProblemJsonForInvalidInvoiceRequest` to Required Tests.
9. `InvoiceCreatedPayload` fields specified below (Outputs).

## Task

T06 — Invoice API + watch registration.

## Purpose

The first end-to-end merchant-facing flow: create an invoice over HTTP, register a watch with
Crypto Service, and emit `invoice.created` — all via this service's first inter-service
authenticated HTTP call.

## Scope

**In:**
- `InvoiceController.create(CreateInvoiceRequest, Jwt)` — `POST /api/v1/invoices`, `201`,
  `ROLE_MERCHANT` only. Orchestrates: compute `invoiceUuid`/`expiresAt` →
  `CryptoClient.registerWatch` → `InvoiceService.create(...)`.
- `CreateInvoiceRequest`/`InvoiceResponse` DTOs (bean-validated per Phase 3 finding 5).
- `InvoiceCreatedPayload` — outbox event body:
  `invoiceUuid, merchantAccountUuid, chain, tokenContractAddress, tokenDecimals, amount (decimal
  string), payToAddress, reference, expiresAt`.
- `InvoiceExceptionHandler` — `InvalidInvoiceRequestException` → `400`;
  `CryptoServiceUnavailableException` (new) → `503` (a `registerWatch` failure is an upstream
  dependency failure, not the merchant's fault).
- `CryptoClient.registerWatch(...)` (new, `common` package) + `RegisterWatchRequest`/
  `RegisterWatchResponse` DTOs mirroring `contracts/api/crypto-internal.yaml` exactly
  (`expectedAmount` as a decimal string).
- `CryptoServiceTokenProvider` (new, `common` package) — thread-safe (`volatile` cached token +
  `ReentrantLock` single-flight refresh on expiry), reads 3 new `CryptoClientProperties.Auth`
  fields.
- `JwtRoleAuthoritiesConverter` (new, `common`) — mirrors `auth-service`'s own class verbatim.
- `InvoiceService.computeExpiresAt()` (new, plain method) and `InvoiceService.create(...)`
  (extended signature: `invoiceUuid`, `expiresAt`, `watchId` now passed in, not computed
  internally).
- `Invoice.create(...)` (extended: `watchId` parameter).
- `ResourceServerConfig` — `@EnableMethodSecurity`, `JwtAuthenticationConverter` bean wiring.
- `CryptoClientProperties` — new nested `Auth(tokenUri, clientId, clientSecret)` record.
- `application.properties` — 3 new `themistra.payment.crypto.auth.*` properties.

**Out:**
- `GET /api/v1/invoices`[/{invoiceUuid}] (task 7). `InvoiceExpirySweepJob` (task 8).
- Any `Payment` entity/row (Phase 0/1's confirmed reading — no payment row until `chain.tx.seen`,
  task 11).
- `CryptoClient.attest(...)`/`unregisterWatch(...)`.
- Saga/compensation for the orphan-watch failure mode (Phase 3 finding 2, accepted as-is).

## Business Rules

- **R1** — `MERCHANT`-role JWT → `OPEN` invoice + registered watch + `201` response with
  `invoiceUuid`/`payToAddress`.
- **R2** — invoice insert + `invoice.created` outbox write, same transaction (watch registration
  is explicitly OUTSIDE that transaction — see Phase 3 disposition).
- **R6** — `Invoice.watchId` is set directly at creation from the `registerWatch` response (no
  separate "ack" step exists once this redesign eliminates the two-phase CREATED→WATCHING
  persistence — it is set in the single transactional insert, never null-then-updated).

## Locked Decisions

- **L3** — `CryptoClient` only calls Crypto Service's own REST API.
- **L8** — outbox write in the same transaction as the invoice insert (not the watch call).
- **L10** — `POST /api/v1/invoices` requires `ROLE_MERCHANT`.
- **L12** — `CryptoClient`/`CryptoServiceTokenProvider`/`JwtRoleAuthoritiesConverter` live in
  `common`.

## Dependencies

- `CryptoClientProperties` (T03, extended this task), `OutboxPublisher`/`EventTopics` (T04),
  `Invoice`/`InvoiceService`/`InvalidInvoiceRequestException` (T05).
- `auth-service`'s `/oauth2/token` endpoint (client_credentials), now correctly scoped (commit
  `2fcedd0f`).

## Inputs

- `contracts/api/crypto-internal.yaml` — `RegisterWatchRequest`/`RegisterWatchResponse`.
- `contracts/api/token-claims.md` — `sub`/`roles` claim shapes.
- `auth-service`'s own `JwtRoleAuthoritiesConverter.java` — exact mirror template.

## Outputs

- New: `invoice/InvoiceController.java`, `invoice/InvoiceExceptionHandler.java`,
  `invoice/CryptoServiceUnavailableException.java`, `invoice/dto/{CreateInvoiceRequest,
  InvoiceResponse}.java`, `invoice/event/InvoiceCreatedPayload.java`,
  `common/CryptoClient.java`, `common/CryptoServiceTokenProvider.java`,
  `common/JwtRoleAuthoritiesConverter.java`, `common/crypto/{RegisterWatchRequest,
  RegisterWatchResponse}.java`.
- Modified: `invoice/Invoice.java`, `invoice/InvoiceService.java`,
  `common/ResourceServerConfig.java`, `common/config/CryptoClientProperties.java`,
  `application.properties`.

## State Changes

- `Invoice.watchId` is set at creation time (passed into `Invoice.create`), not updated
  post-insert.

## Files to Create

- `invoice/InvoiceController.java`, `invoice/InvoiceExceptionHandler.java`,
  `invoice/CryptoServiceUnavailableException.java`
- `invoice/dto/CreateInvoiceRequest.java`, `invoice/dto/InvoiceResponse.java`
- `invoice/event/InvoiceCreatedPayload.java`
- `common/CryptoClient.java`, `common/CryptoServiceTokenProvider.java`,
  `common/JwtRoleAuthoritiesConverter.java`
- `common/crypto/RegisterWatchRequest.java`, `common/crypto/RegisterWatchResponse.java`
- Tests (Phase 10): `invoice/InvoiceControllerTest.java`, `common/CryptoClientTest.java`,
  `common/CryptoServiceTokenProviderTest.java`

## Files to Modify

- `invoice/Invoice.java`, `invoice/InvoiceService.java`
- `common/ResourceServerConfig.java`, `common/config/CryptoClientProperties.java`
- `application.properties`
- `invoice/InvoiceServiceTest.java` (T05's existing tests — signature change)

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql` (immutable).
- Anything under `spec/`. `services/auth/**` beyond the already-applied, pre-approved scope fix.

## Acceptance Criteria

- **AC1 (L10)** — non-`MERCHANT`/unauthenticated caller → `401`/`403` before any service logic.
- **AC2 (R1/R6)** — successful call → `Invoice.watchId` set from the real `registerWatch`
  response, in the same insert (never null-then-updated).
- **AC3 (R2)** — invoice insert + outbox write, one transaction; watch registration is OUTSIDE
  it.
- **AC4 (R1)** — `201` response, body has `invoiceUuid` + `payToAddress`.
- **AC5** — `CryptoClient` sends `expectedAmount` as a decimal string.
- **AC6** — a `registerWatch` failure → `503`, nothing persisted (no transaction was ever
  opened).
- **AC7** — `CryptoServiceTokenProvider` is thread-safe; concurrent callers never fetch more than
  one new token per expiry window (single-flight).
- **AC8 (Phase 3 finding 8)** — a `400`/`503` response is `application/problem+json`, no stack
  trace/internal detail.

## Required Tests

- `shouldCreateInvoiceAndRegisterWatch` (R1).
- `shouldEmitInvoiceCreatedInSameTransactionAsInsert` (R2).
- `shouldTransitionCreatedToWatchingOnWatchAck` (R6 — now: watch is set at creation, not a
  two-phase update; the test's own name is kept per `package.md` §8, its assertion content
  reflects the real, single-step shape).
- `shouldRejectNonMerchantCallerWithForbidden` (AC1).
- `shouldReturnServiceUnavailableWhenWatchRegistrationFails` (AC6, renamed from the TIB's
  rollback-framing since there is no longer a transaction to roll back).
- `shouldReturnProblemJsonForInvalidInvoiceRequest` (AC8, Phase 3 finding 8).
- `shouldReuseCachedTokenUntilExpiryAndRefreshOnceUnderConcurrency` (AC7).

## Constraints

- **Transaction:** `InvoiceService.create` is `@Transactional`; `CryptoClient.registerWatch` and
  `CryptoServiceTokenProvider`'s token fetch run strictly before it, never inside it.
- **Thread-safety:** `CryptoServiceTokenProvider`'s cache uses `volatile` + `ReentrantLock`
  single-flight refresh.
- **Security:** `POST /api/v1/invoices` requires `ROLE_MERCHANT` via `@PreAuthorize` (method
  security now enabled) or an equivalent authority-based `requestMatchers` rule — Phase 5 picks
  one.
- **Module boundaries:** `CryptoClient`/`CryptoServiceTokenProvider`/
  `JwtRoleAuthoritiesConverter` are `common`.
- **Null handling:** `CreateInvoiceRequest` is bean-validated; `InvoiceService`'s own T05
  null/blank guards remain as defense-in-depth.

## Open Questions

None. Both Phase 2 architecture questions and all 9 Phase 3 findings are resolved above.
