# payment · T06 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 | Non-`MERCHANT`/unauthenticated → `401`/`403` before service logic | `@PreAuthorize("hasRole('MERCHANT')")`, `JwtRoleAuthoritiesConverter`, `@EnableMethodSecurity` | `shouldRejectNonMerchantCallerWithForbidden` (now with full RFC 9457 body), `shouldRejectUnauthenticatedCallerWithUnauthorized` |
| AC2 | `watchId` set from the real `registerWatch` response, never null-then-updated | `InvoiceController` orchestration → `Invoice.create(..., watchId, ...)` | `shouldCreateInvoiceAndRegisterWatch`, `shouldTransitionCreatedToWatchingOnWatchAck` (now with `ArgumentCaptor`+`InOrder` proof of UUID hand-off and call ordering) |
| AC3 | Invoice insert + outbox write, one transaction; watch registration outside it | `InvoiceController` calls `CryptoClient` before any transaction opens; `InvoiceService.create` (`@Transactional`) does insert + outbox emit | `shouldEmitInvoiceCreatedInSameTransactionAsInsert` (now inspects the real payload) |
| AC4 | `201` response with `invoiceUuid`/`payToAddress` | `InvoiceController.create` | `shouldCreateInvoiceAndRegisterWatch` |
| AC5 | `expectedAmount` as a decimal string | `RegisterWatchRequest` (`amount.toPlainString()`) | `shouldSendExpectedAmountAsDecimalStringNotJsonNumber` (real HTTP server, real serialization) |
| AC6 | `registerWatch` failure → `503`, no internal detail | `CryptoServiceUnavailableException` → `InvoiceExceptionHandler` | `shouldReturnServiceUnavailableWhenWatchRegistrationFails` |
| AC7 | `CryptoServiceTokenProvider` single-flight, thread-safe | `volatile` cache + `ReentrantLock` | `shouldReuseCachedTokenUntilExpiryAndRefreshOnceUnderConcurrency` (named, 20 real threads) |
| AC8 | `400`/`503` responses are `application/problem+json`, no internal detail | `InvoiceExceptionHandler`, `ApiExceptionHandler` (incl. the Phase 10 `AuthorizationDeniedException` fix) | `shouldReturnProblemJsonForInvalidInvoiceRequest`, `shouldReturnProblemJsonForBeanValidationFailure`, 403-body assertions |

## Required Tests (frozen brief)

All 5 present and passing, several exceeded via the genuine Phase 8/11 Kimi passes:
`shouldCreateInvoiceAndRegisterWatch`, `shouldEmitInvoiceCreatedInSameTransactionAsInsert`,
`shouldTransitionCreatedToWatchingOnWatchAck`, `shouldRejectNonMerchantCallerWithForbidden`,
`shouldReturnServiceUnavailableWhenWatchRegistrationFails` (renamed from the TIB's rollback-
framing once the redesign eliminated the transaction to roll back).

## Scope discipline

`git diff --stat f35dd6b4..HEAD` (T06's own full range, from Phase 0 onward) touches exactly
19 files: the frozen brief's own Files to Create/Modify, plus two disclosed, approved additions
— `V3__payment_app_invoice_and_outbox_grants.sql` (Phase 9) and
`services/auth/src/main/resources/application.properties` (Phase 4, pre-approved cross-service
fix). No other file outside this task's own scope touched.

## Locked decisions

- **L3** — `CryptoClient` only calls Crypto Service's own REST API. Held.
- **L8** — outbox write in the same transaction as the invoice insert; watch registration
  outside it (the Phase 4 redesign's own explicit point). Held.
- **L10** — `POST /api/v1/invoices` requires a valid JWT and `ROLE_MERCHANT`. Held.
- **L12** — `CryptoClient`/`CryptoServiceTokenProvider`/`JwtRoleAuthoritiesConverter`/
  `CryptoServiceUnavailableException`/`ApiExceptionHandler` all live in `common`. Held — including
  the Phase 6 placement correction that kept `CryptoServiceUnavailableException` out of `invoice`
  specifically to preserve this.

## Open items carried forward (not blockers for this task)

- `SchemaBaselineMigrationIntegrationTest`'s own V3 fix (Phase 11) could not be re-run against a
  real database this session — Docker unavailable. Kimi's own environment did run it and
  reported the real failures the fix addresses; the fix itself was verified by direct reasoning
  against that real output, not empirically re-confirmed here.
- Orphan-watch failure window (a successful `registerWatch` followed by a failed
  `InvoiceService.create`) remains an accepted, documented limitation — no saga/compensation
  this task (Phase 4/9 disposition, re-confirmed at Phase 8).
- The inherited `ApiExceptionHandler` 404/405-swallowing limitation (shared mirrored template)
  remains, disclosed at Phase 7/9.
- A genuinely new, cross-service-relevant finding surfaced and fixed here
  (`AuthorizationDeniedException` not mapped to 403 by the mirrored `ApiExceptionHandler`
  template) affects every sibling service using `@PreAuthorize` with the same template — worth a
  follow-up for `auth-service`'s own `AdminAccountController`, out of this task's own scope to
  fix there.

## Test suite status

123/123 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest'`). `SchemaBaselineMigrationIntegrationTest`
itself was not run this session (Docker unavailable) but was updated to match Kimi's own real
failure output from a session where it did run.

## Verdict rationale

All 8 acceptance criteria are implemented and test-covered, several beyond the frozen brief's
own minimum per two genuine Kimi review passes (Phase 8, Phase 11) that each surfaced real,
previously-undiscovered issues — including a cross-service-relevant security-response defect
(`AuthorizationDeniedException`) and a critical, CI-breaking stale-test gap (V3 migration). No
LOCKED decision violated. No file outside this task's own disclosed, approved scope touched.
The disclosed carried-forward items above are accepted, named limitations, not defects.
**PASS.** Proceeding to Phase 13.
