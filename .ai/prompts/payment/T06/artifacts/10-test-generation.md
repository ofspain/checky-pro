# payment · T06 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | `artifacts/09-review-resolution.md` |

One production-code fix was made in this phase (disclosed below) — found while writing the
tests, not before.

## Test manifest

| Test | File | Verifies |
|---|---|---|
| `shouldCreateInvoiceAndRegisterWatch` | `InvoiceControllerTest.java` | R1 (named) |
| `shouldTransitionCreatedToWatchingOnWatchAck` | `InvoiceControllerTest.java` | R6 (named) — proves the same `invoiceUuid` reaches both `registerWatch` and `create` |
| `shouldRejectNonMerchantCallerWithForbidden` | `InvoiceControllerTest.java` | AC1 (named) |
| `shouldRejectUnauthenticatedCallerWithUnauthorized` | `InvoiceControllerTest.java` | AC1 boundary |
| `shouldReturnServiceUnavailableWhenWatchRegistrationFails` | `InvoiceControllerTest.java` | AC6 (named) |
| `shouldReturnProblemJsonForInvalidInvoiceRequest` | `InvoiceControllerTest.java` | AC8 (named) — `InvoiceException Handler`'s own path, via a fractional amount that passes DTO validation but fails `InvoiceService`'s deeper scale check |
| `shouldReturnProblemJsonForBeanValidationFailure` | `InvoiceControllerTest.java` | `ApiExceptionHandler`'s own Phase 7 fix, otherwise untested anywhere |
| `shouldSendExpectedAmountAsDecimalStringNotJsonNumber` | `CryptoClientTest.java` | AC5 (named) |
| `shouldSendBearerTokenFromProvider` | `CryptoClientTest.java` | token wiring |
| `shouldReturnWatchIdAndStatusOnSuccess` | `CryptoClientTest.java` | happy path |
| `shouldThrowWhenResponseStatusIsNotRegistered` | `CryptoClientTest.java` | Phase 9 fix (Kimi Phase 8 finding 8) |
| `shouldThrowWhenWatchIdIsNull` / `shouldThrowOnServerError` / `shouldThrowOnConnectionFailure` / `shouldThrowWhenServerExceedsConfiguredReadTimeout` | `CryptoClientTest.java` | AC6 boundary/timeout cases |
| `shouldFetchTokenWithClientCredentialsGrantAndBasicAuth` | `CryptoServiceTokenProviderTest.java` | token-fetch request shape |
| `shouldReuseCachedTokenUntilExpiry` | `CryptoServiceTokenProviderTest.java` | cache reuse |
| `shouldReuseCachedTokenUntilExpiryAndRefreshOnceUnderConcurrency` | `CryptoServiceTokenProviderTest.java` | AC7 (named) — 20 real threads, single-flight proven by a real request counter, not a theoretical argument |
| `shouldRefreshAfterCachedTokenExpires` | `CryptoServiceTokenProviderTest.java` | expiry-driven refetch |
| `shouldThrowWhenTokenEndpointReturnsNoAccessToken` / `shouldThrowOnServerError` | `CryptoServiceTokenProviderTest.java` | failure mapping |

## Genuinely new discovery this phase, fixed immediately

`InvoiceControllerTest.shouldRejectNonMerchantCallerWithForbidden` initially returned `500`, not
`403`. Root cause: `@PreAuthorize`'s own denial throws `AuthorizationDeniedException` — a
**different** exception type from the filter-chain-level `AccessDeniedException`
`ResourceServerConfig`'s own manual handler covers. No handler existed for it anywhere, so it
fell into `ApiExceptionHandler`'s generic `Exception.class` catch-all. This was previously
**untested anywhere in the codebase**: confirmed `auth-service`'s own `@PreAuthorize`-protected
`AdminAccountController` test explicitly chose a reflection-based check over a live MockMvc 403
test specifically to avoid this exact "bigger infrastructure decision." Added
`@ExceptionHandler(AuthorizationDeniedException.class)` to `ApiExceptionHandler`, mapping to
`403` with the same RFC 9457 shape. This is a real, cross-service-relevant finding (every
sibling service using `@PreAuthorize` with the same mirrored template has the identical latent
bug) — worth raising as a follow-up for `auth-service`'s own `AdminAccountController`, out of
this task's own scope to fix there.

Also used a real local `com.sun.net.httpserver.HttpServer` for both `CryptoClientTest` and
`CryptoServiceTokenProviderTest`, mirroring `auth-service`'s own `BreachCheckClientTest`
precedent exactly — `MockRestServiceServer` would have silently lost to the production
constructors' own `.requestFactory(...)` call (the same documented conflict that precedent
already found and avoided).

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='InvoiceControllerTest,CryptoClientTest,
  CryptoServiceTokenProviderTest'` — 21/21 passing.
- Mutation check 1: commented out the `AuthorizationDeniedException` handler's `@ExceptionHandler`
  annotation, confirmed `shouldRejectNonMerchantCallerWithForbidden` fails with `500`, re-added
  it via `Edit` (not `git checkout`, to avoid discarding the then-still-uncommitted fix),
  confirmed clean.
- Mutation check 2: disabled the `REGISTERED`-status check in `CryptoClient` (`if (false)`),
  confirmed `shouldThrowWhenResponseStatusIsNotRegistered` fails, reverted via `Edit`, confirmed
  clean.
- Full non-Docker-dependent suite: **120/120 passing**, up from 99 before this phase's test
  additions (21 new: 7 `InvoiceControllerTest` + 8 `CryptoClientTest` + 6
  `CryptoServiceTokenProviderTest`).
