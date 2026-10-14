# payment · T06 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Consumes** | `artifacts/06-implementation-notes.md` |

Findings only — no fixes applied here (Phase 9).

## Finding 1 — `CryptoClient`/`CryptoServiceTokenProvider` never apply `CryptoClientProperties`'s own connect/read timeouts

- **Severity:** High — a real robustness gap, not just a style nit.
- **Evidence:** `common/CryptoClient.java` and `common/CryptoServiceTokenProvider.java` both
  build their `RestClient` via `restClientBuilder.build()` with no `ClientHttpRequestFactory`
  configured. `CryptoClientProperties.connectTimeoutMs`/`readTimeoutMs` (T03, already stubbed)
  are never read anywhere in this task's own new code. Without a timeout, a hung TCP connection
  or a slow/unresponsive `crypto-service`/`auth-service` would block the calling thread
  indefinitely — exactly the kind of risk Kimi's Phase 3 finding 1 was raised against, except
  now with no bound at all rather than just "too generous a bound." `auth-service`'s own
  `BreachCheckClient` is the established, correct template: `SimpleClientHttpRequestFactory` with
  `setConnectTimeout`/`setReadTimeout`, wired via `.requestFactory(...)`.
- **Recommendation:** Wire `connectTimeoutMs`/`readTimeoutMs` into both `RestClient` instances
  via `SimpleClientHttpRequestFactory`, mirroring `BreachCheckClient` exactly.

## Finding 2 — No framework-level `ApiExceptionHandler`; `@Valid` failures on `CreateInvoiceRequest` won't get the RFC 9457 shape AC8 requires

- **Severity:** High — AC8 is explicitly named in the frozen brief, and this gap means it is
  not actually satisfied for the most common rejection path (DTO-level bean validation, not
  `InvoiceService`'s own internal validation).
- **Evidence:** `InvoiceController.create` validates `CreateInvoiceRequest` with `@Valid`. A
  failure throws `MethodArgumentNotValidException`, a *different* exception type from
  `InvalidInvoiceRequestException` — `InvoiceExceptionHandler` has no handler for it, so it falls
  through to Spring Boot's own default error response, not the agents.md-required
  `application/problem+json` shape. `auth-service`'s and `crypto-service`'s own
  `common.ApiExceptionHandler` (both built "alongside its first controller," per
  `crypto-service`'s own Javadoc) is the exact, confirmed template — `payment-service` has never
  needed one before T06, since no prior task had a controller. `InvoiceExceptionHandler`'s own
  Javadoc already anticipated this: "a domain-specific advice needs [`@Order(HIGHEST_PRECEDENCE)`]
  to reliably outrank any global fallback handler" — written before this gap was found, correctly
  foreshadowing it.
- **Recommendation:** Add `common/ApiExceptionHandler.java`, mirroring `crypto-service`'s own
  class: `MethodArgumentNotValidException`, `HttpMessageNotReadableException`,
  `MethodArgumentTypeMismatchException`, and a catch-all `Exception.class` handler, all at
  `@Order(LOWEST_PRECEDENCE)`.

## Note — inherited, already-disclosed limitation in the mirrored `ApiExceptionHandler` template

Applying Finding 2's fix (mirroring `crypto-service`'s own `ApiExceptionHandler` verbatim)
inherits a known, pre-existing defect in that same template: `crypto-service`'s own
`VerificationKeysControllerTest` explicitly documents (and locks in via tests asserting the
actual, deficient behavior) that `NoResourceFoundException`/`HttpRequestMethodNotSupportedException`
for an unmapped path or disallowed method get swallowed by the generic `Exception.class`
catch-all into a `500`, not the correct `404`/`405` — confirmed there as "a defect in shared
code... not something [that task] introduced or is scoped to fix." Observed here too (surfaced
as `ERROR` log lines during `PublicEndpointsTest`, which still passes — its own assertions only
require not-401/403, already written with this exact possibility in mind). Not fixed in this
task either, for the same reason: it is a limitation of the shared template being mirrored, not
something T06 introduced, and `payment-service`'s own `ApiExceptionHandler` is a verbatim mirror
by design, not a divergence point. Disclosed here rather than silently carried.

## Positive confirmations

- `CryptoServiceTokenProvider`'s single-flight double-checked-locking pattern traced through
  correctly: lock-free fast path on a `volatile` read, re-checked under the lock before
  fetching, so concurrent callers past the cache's expiry never issue more than one real token
  request per refresh window.
- The amount-precision validation logic (`validateAmount`, T05) is untouched by this task and
  still correct by inspection (re-confirmed, not re-derived).
- `InvoiceController`'s orchestration genuinely never holds a DB transaction open during
  `CryptoClient.registerWatch` — traced the call sequence directly: `computeExpiresAt()` (no
  transaction), `registerWatch(...)` (no transaction), `InvoiceService.create(...)` (opens its
  own transaction only here, via the Spring proxy on a separate bean — no self-invocation).
- `Invoice.create`'s new `watchId` parameter is set directly in the factory; no setter was
  added, keeping the entity's established factory-only, no-setters discipline from T05.

## Verdict

Two real, High-severity findings (timeout wiring, missing global exception handler) — both
fixed immediately in this same phase rather than deferred to Phase 9: shipping a known-
incomplete diff (AC6/AC8 explicitly unmet) to Kimi's own independent review would waste that
review cycle reviewing code already known to need a fix. Phase 9's own "findings deferred to
resolution" convention applies to *external* (Kimi) findings needing a human disposition
decision — these are self-review findings with an obvious, uncontroversial, already-precedented
fix, so no such decision is needed.
