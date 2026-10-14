# payment · T07 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Consumes** | `artifacts/09-review-resolution.md` |

No production code changed in this phase.

## Test manifest

| Test | File | Verifies |
|---|---|---|
| `shouldScopeInvoiceReadsToOwningMerchant` | `InvoiceServiceReadTest.java` | R28 (named) |
| `shouldReturnOnlyCallersOwnInvoicesFromList` / `shouldReturnEmptyListWhenCallerHasNoInvoices` | `InvoiceServiceReadTest.java` | AC1 |
| `shouldReturnInvoiceForOwningMerchant` / `shouldReturn404ForNonOwningMerchant` / `shouldReturn404ForNonexistentInvoiceUuid` | `InvoiceServiceReadTest.java` | AC2 (service layer: same exception type for both causes) |
| 3 null-guard tests | `InvoiceServiceReadTest.java` | defensive parameter guards |
| `shouldReturnOnlyCallersOwnInvoicesFromList` | `InvoiceControllerTest.java` | AC1/AC4 — real JSON shape: full field set, `amount` as a quoted string, no `watchId` anywhere in the body |
| `shouldReturnEmptyListWhenCallerHasNoInvoices` | `InvoiceControllerTest.java` | AC1 boundary |
| `shouldReturnInvoiceForOwningMerchant` | `InvoiceControllerTest.java` | AC2 happy path |
| `shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid` | `InvoiceControllerTest.java` | AC2 (controller/handler layer: byte-for-byte identical body for both causes, normalized for the differing path UUID) |
| `shouldRejectNonMerchantCallerOnReadEndpoints` | `InvoiceControllerTest.java` | AC3 (named) |

Together, `InvoiceServiceReadTest` proves the *same exception type* is thrown for both 404
causes, and `InvoiceControllerTest`'s identical-shape test proves the *handler* produces
byte-identical output for that exception regardless of request path — the two layers jointly
cover the full no-enumeration-oracle guarantee (Phase 8 finding 5).

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='InvoiceServiceReadTest,InvoiceControllerTest'` —
  23/23 passing.
- Mutation check 1: added a `watchId` field back to `InvoiceResponse`/`from(...)`, confirmed
  `shouldReturnOnlyCallersOwnInvoicesFromList` fails (`$[0].watchId` now exists), reverted,
  confirmed clean.
- Mutation check 2: added a non-deterministic `detail` (`"debug: " + System.nanoTime()`) to
  `InvoiceExceptionHandler.onInvoiceNotFound`, confirmed
  `shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid` fails, reverted,
  confirmed clean. (An earlier attempt at this mutation via a malformed constructor-argument
  insertion was caught and corrected before running — noted for completeness, not a real defect.)
- Full non-Docker-dependent suite: **137/137 passing** (up from 123).
