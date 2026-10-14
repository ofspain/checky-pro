# payment · T06 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T06: invoice API + watch registration (first inter-service HTTP call)`

## Commit message

```
payment-service T06: invoice API + watch registration (first inter-service HTTP call)

Adds the service's first end-to-end merchant-facing flow: InvoiceController
(POST /api/v1/invoices, ROLE_MERCHANT-only via a new JwtRoleAuthoritiesConverter
mirroring auth-service's own identical class, @EnableMethodSecurity now wired
into ResourceServerConfig), CryptoClient (registerWatch, this service's first
inter-service authenticated HTTP call anywhere in the codebase), and
CryptoServiceTokenProvider (client_credentials against auth-service's own
/oauth2/token, thread-safe single-flight token cache).

Real architecture decisions resolved via AskUserQuestion: a minimal explicit
token provider over adding spring-security-oauth2-client; and a disclosed,
pre-approved fix to auth-service's own client registration (payment-service
had only internal.accounts:read, not the internal.crypto:write the real
contract requires). A structural redesign - confirmed via a second
AskUserQuestion, not just inferred - moved CryptoClient.registerWatch
entirely outside InvoiceService.create's own @Transactional boundary:
InvoiceController orchestrates the flow directly, computing invoiceUuid/
expiresAt once and calling two separate beans in sequence (never a
transactional method calling another transactional method on `this`, which
would silently bypass Spring's AOP proxy). R2's own atomicity requirement -
invoice row + outbox event, same transaction - never required the external
watch-registration call to be inside that transaction too; this eliminates
the held-transaction-across-a-network-call risk entirely rather than just
documenting it, at the accepted cost of a disclosed orphan-watch failure
window if the transactional step fails after a successful registerWatch.

Two Kimi review passes (Phase 8, Phase 11) each surfaced genuinely new,
previously-undiscovered issues: a missing V3 grant migration (payment_app
had no access to invoices/outbox - T04's own OutboxPublisher/OutboxRelay had
compiled and passed their mocked-repository unit tests since T04 without
ever having a real grant); and, while writing Phase 10's own controller
test, a real bug where Spring's @PreAuthorize denial (AuthorizationDeniedException)
was not mapped by the mirrored ApiExceptionHandler template, falling through
to a generic 500 instead of 403 - previously untested anywhere in this
codebase (confirmed: auth-service's own @PreAuthorize test explicitly chose
a reflection-based check over a live MockMvc 403 test for this exact reason).
Both fixed; the @PreAuthorize gap is cross-service-relevant and worth a
follow-up for auth-service's own AdminAccountController.

One finding empirically disproved rather than argued: built and ran Spring
Boot's own autoconfigured ObjectMapper standalone to confirm Instant already
serializes as an ISO-8601 string by default, not the numeric-timestamp risk
a Phase 8 finding described.

Spec: spec/payment-service/tasks.md task 6. Requirements: R1, R2, R6. Locked
decisions: L3, L8, L10, L12.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/dto/CreateInvoiceRequest.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/dto/InvoiceResponse.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/event/InvoiceCreatedPayload.java`
- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`
- `services/payment/src/main/java/com/themistra/payment/common/CryptoServiceTokenProvider.java`
- `services/payment/src/main/java/com/themistra/payment/common/CryptoServiceUnavailableException.java`
- `services/payment/src/main/java/com/themistra/payment/common/JwtRoleAuthoritiesConverter.java`
- `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java`
- `services/payment/src/main/java/com/themistra/payment/common/crypto/RegisterWatchRequest.java`
- `services/payment/src/main/java/com/themistra/payment/common/crypto/RegisterWatchResponse.java`
- `services/payment/src/main/resources/db/migration/V3__payment_app_invoice_and_outbox_grants.sql`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/CryptoServiceTokenProviderTest.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/event/InvoiceCreatedPayloadSerializationTest.java`

**Modified**
- `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java` (`watchId` factory parameter)
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java` (`computeExpiresAt`, extended `create` signature, outbox emit)
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java` (`@EnableMethodSecurity`, converter wiring)
- `services/payment/src/main/java/com/themistra/payment/common/config/CryptoClientProperties.java` (`Auth` record)
- `services/payment/src/main/resources/application.properties` (3 new `crypto.auth.*` properties)
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java`
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`
- `services/auth/src/main/resources/application.properties` (disclosed, pre-approved cross-service scope fix)

**Process artifacts (not application code)**
- `.ai/prompts/payment/T06/artifacts/00-*.md` through `12-*.md`.

## Summary

`payment-service` now has its first real, callable HTTP endpoint and its first inter-service
authenticated HTTP call. A merchant can create an invoice; it registers a watch with Crypto
Service and emits `invoice.created`. No `GET` endpoint exists yet (task 7); no `Payment` entity
exists yet (task 11, per Phase 0/1's own confirmed reading — `CREATED`/`WATCHING` tracking is
`Invoice.watchId` only).

## Testing performed

- `mvn -pl services/payment -am compile` — clean throughout.
- 33 new tests added this task (7 `InvoiceControllerTest`→9 after Phase 11, 8 `CryptoClientTest`,
  6 `CryptoServiceTokenProviderTest`, 1 `InvoiceCreatedPayloadSerializationTest`, plus
  strengthened existing `InvoiceServiceTest`/`SchemaBaselineMigrationIntegrationTest` assertions).
- **123/123 non-Docker-dependent tests passing**, up from 99 before this task.
  `SchemaBaselineMigrationIntegrationTest` was updated for V3 based on Kimi's own real failure
  output (its environment has Docker) but could not be re-run here directly.
- Mutation tests across Phases 7, 10, and 11 on: the `AuthorizationDeniedException` handler, the
  `registerWatch` status check, the outbox-payload `amount` serialization, the ArchUnit-adjacent
  security-slice wiring, and the CryptoClient timeout configuration — each caught its introduced
  defect, each reverted clean (via `Edit`, not `git checkout`, for anything not yet committed).

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 6 — "Invoice API + watch registration."
- **Requirement IDs:** R1, R2, R6.
- **LOCKED decisions:** L3, L8, L10, L12.

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T06.**
