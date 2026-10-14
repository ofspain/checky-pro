# payment · T06 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R1** — a merchant (valid JWT, `MERCHANT` role) `POST /api/v1/invoices` with amount, token
  contract address, chain, pay-to address → create invoice `OPEN`, register a watch with the
  Crypto Service, return the invoice with `invoiceUuid` and pay-to address.
- **R2** — the invoice row and its `invoice.created` outbox event are written in the same DB
  transaction.
- **R6** — once the Crypto Service acknowledges the watch registration, tracking moves `CREATED`
  → `WATCHING`.

## Locked Decisions

- **L8** — outbox for every publish, same transaction as the state change.
- **L10** — zero trust at the edge; every non-internal endpoint requires a valid JWT.
- **L12** — module boundaries; this task's new `CryptoClient` is shared plumbing, not feature-
  module-owned (candidate for `common`, pending Phase 2 decision).
- **L3** — this service never touches a blockchain or signing key; `CryptoClient` only calls
  Crypto Service's own REST endpoints.
- **L14** — one transaction per invoice (not exercised by this task's own creation flow directly,
  but the watch registration this task performs is what L14's own unregister-on-`SEEN` rule,
  R7a, later acts on — not this task's scope to implement that unregister call).

## Files involved

**New:**
- `invoice/InvoiceController.java` — `POST /api/v1/invoices`.
- `invoice/dto/{CreateInvoiceRequest,InvoiceResponse}.java`.
- `invoice/event/InvoiceCreatedPayload.java` — the `invoice.created` event body.
- `invoice/InvoiceExceptionHandler.java` — maps `InvalidInvoiceRequestException` (T05) to `400`.
- A `CryptoClient` (package TBD, Phase 2) + its own request/response DTOs mirroring
  `contracts/api/crypto-internal.yaml`'s `RegisterWatchRequest`/`RegisterWatchResponse`.
- `common/JwtRoleAuthoritiesConverter.java` + a `JwtAuthenticationConverter` bean in
  `ResourceServerConfig` — **not literally named by task 6's text**, but a necessary implied
  consequence: R1 requires enforcing the `MERCHANT` role, and no code anywhere in this service
  maps the token's `roles` claim to a Spring Security authority yet (confirmed, Phase 0). Mirrors
  `auth-service`'s own identical `JwtRoleAuthoritiesConverter` verbatim.

**Existing, extended:**
- `InvoiceService.create(...)` (T05) — gains the watch-registration call and the outbox emit.
- `ResourceServerConfig` (T03) — gains its first authority-restricted path/method (closing T03's
  own disclosed "no authority-restricted path exists yet" gap, Phase 3 finding 1 there).

**Existing, reused as-is:** `OutboxPublisher`, `EventTopics` (`"invoice"` mapping), `CryptoClientProperties`.

**Not to modify:** `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql`, anything
under `spec/`.

## Dependencies

- `CryptoClientProperties` (T03) — base URL, `/watches` path, timeouts.
- `OutboxPublisher` (T04).
- `Invoice`/`InvoiceService` (T05).
- **Cross-service dependency, confirmed real (Phase 0 item 2):** `payment-service`'s own OAuth2
  client registration in `auth-service`'s `application.properties` has scope
  `internal.accounts:read` only, not `internal.crypto:write`. `CryptoClient` cannot obtain a
  usable token against the real contract until this is added.

## Acceptance Criteria

- **AC1 (R1)** — a `MERCHANT`-role JWT can successfully `POST /api/v1/invoices`; any other
  caller (no token, wrong role) is rejected before `InvoiceService.create` is ever reached.
- **AC2 (R1/R6)** — on success, `CryptoClient.registerWatch` is called with the invoice's own
  `chain`/`payToAddress`/`tokenContractAddress`/`amount`/`expiresAt`; the returned `watchId` is
  stored on the invoice (`CREATED` → `WATCHING`, per Phase 0 item 3's reading — no `Payment` row
  exists at this point).
- **AC3 (R2)** — the invoice insert and the `invoice.created` outbox write happen in the same
  transaction.
- **AC4** — the response body carries `invoiceUuid` and `payToAddress` (R1's own explicit text).
- **AC5** — a watch-registration failure does not leave a half-created invoice (exact rollback
  behavior is a Phase 2 decision, flagged at Phase 0 item 4).

## Tests required

- `shouldCreateInvoiceAndRegisterWatch` (R1) — named, `package.md` §8.
- `shouldEmitInvoiceCreatedInSameTransactionAsInsert` (R2) — named.
- `shouldTransitionCreatedToWatchingOnWatchAck` (R6) — named.
- Implied: a `403`/`401` case for a non-`MERCHANT` or unauthenticated caller (AC1); a watch-
  registration-failure case (AC5); request-validation rejection cases reusing T05's
  `InvalidInvoiceRequestException` through the new `InvoiceExceptionHandler`.

## Open Questions

1. **(Phase 0 item 1/2, real architecture gap)** How does `CryptoClient` obtain an
   `internal.crypto:write`-scoped token, and does `auth-service`'s own client registration need
   to be extended? No precedent exists anywhere in this codebase for an authenticated inter-
   service HTTP caller. **This is a genuine candidate for a Phase 4 Human Approval decision**,
   not a silent Phase 2 call — the options (build a full OAuth2 client-credentials token-
   acquisition component now vs. a simpler manual token-fetch-and-cache bean vs. deferring the
   whole cross-service auth concern and stubbing `CryptoClient` behind an interface for a later
   hardening task) have materially different cost/risk, and the `auth-service` config change is
   outside `payment-service`'s own repo boundary in spirit even though it's the same monorepo.
2. **(Phase 0 item 3)** Confirmed reading: no `Payment` entity/row exists before `chain.tx.seen`
   (task 11); `CREATED`/`WATCHING` tracking is `Invoice.watchId` (null/set). Carried forward as
   the working assumption, to be explicitly challenged at Phase 3 rather than silently built on.
3. **(Phase 0 item 4)** Transaction boundary across the synchronous `CryptoClient` HTTP call.
   Carried forward, not resolved here.
