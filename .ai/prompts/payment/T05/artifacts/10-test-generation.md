# payment · T05 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | `artifacts/09-review-resolution.md` |

No production code changed in this phase.

## Test manifest

| Test | File | Verifies |
|---|---|---|
| `shouldRejectFloatingPointMoneyTypesAtArchUnit` | `ArchitectureTest.java` | L6/AC3 — named test; this service's first ArchUnit rule, using the plain-`ArchRule`-field + `@Test`-canary shape (never `@ArchTest` — Surefire silently reports 0 tests otherwise) |
| `shouldRejectFloatingPointMoneyTypesAtArchUnitActuallyFailsAgainstAGenuineViolation` | `ArchitectureTest.java` | Proves the rule can actually fail (Kimi Phase 8 recommendation) against `RogueFloatingPointMoneyUser` — a field, a method return type, and a method parameter violation all in one fixture |
| `shouldCreateInvoiceWithOpenStateAndComputedExpiry` | `InvoiceServiceTest.java` | AC4 — the happy path (Phase 3 finding 3 / Phase 8 finding 1) |
| `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` | `InvoiceServiceTest.java` | R3/AC1 — named test; unrecognized chain, null chain, null/blank tokenContractAddress |
| `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` | `InvoiceServiceTest.java` | R4/AC2 — named test; null, zero, negative, non-integral scale, >78-digit amount |
| `shouldAcceptWholeNumberAmountWithTrailingZerosAndRejectByTrueDigitCountNotReducedPrecision` | `InvoiceServiceTest.java` | R4/AC2 boundary — Phase 7 finding 2 made permanent: a trailing-zero whole number (`100.00`) is accepted; an 81-digit whole number with trailing zeros is still rejected by true digit count |
| `shouldThrowNullPointerExceptionForNullMerchantAccountUuid` / `...PayToAddress` | `InvoiceServiceTest.java` | The frozen brief's own defensive-null-guard distinction (Phase 8 finding 4 — accepted as written) |

## Fixture added

- `archtestfixtures/RogueFloatingPointMoneyUser.java` — deliberately violates L6 three ways
  (field, method return, method parameter) in one class, mirroring `notification-service`'s own
  `RogueHttpClientUser`/`RogueChannelEntityReferencer` precedent. Excluded from the real canary's
  own scan (`ImportOption.DoNotIncludeTests`), imported directly only by the negative test.

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='ArchitectureTest,InvoiceServiceTest'` — 8/8 passing.
- Mutation check 1: added a real `double` field to `InvoiceService`, confirmed
  `shouldRejectFloatingPointMoneyTypesAtArchUnit` fails with the correct field identified,
  reverted, confirmed clean.
- Mutation check 2: disabled the amount-scale check in `validateAmount`, confirmed
  `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` fails, reverted, confirmed clean.
- Mutation check 3: changed `Duration.ofMinutes` to `Duration.ofHours` in the `expiresAt`
  computation, confirmed `shouldCreateInvoiceWithOpenStateAndComputedExpiry` fails, reverted,
  confirmed clean.
- Full non-Docker-dependent suite: **93/93 passing**, up from 85 before this task's own test
  additions (8 new: 2 `ArchitectureTest` + 6 `InvoiceServiceTest`).
