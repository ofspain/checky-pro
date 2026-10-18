# payment · T23 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Add a stable RFC 9457 `type` catalogue across every problem-detail emission site in this
service; no stack traces or internal detail on any 4xx (R29).

## Purpose

Every current 4xx/5xx response already satisfies "no stack traces/internal detail" — confirmed,
site by site, by every prior task that added a handler. What is missing is the "stable `type`
catalogue" half of R29: all twelve existing sites hardcode the literal `"about:blank"`, making
every distinct error condition indistinguishable by `type` alone. This task closes that gap by
mirroring `auth-service`'s own already-built `ProblemTypes` precedent.

## Scope

**In:** one new `common/ProblemTypes.java`; updating all twelve existing `setType(...)` call
sites across `ApiExceptionHandler`, `InvoiceExceptionHandler`, `ExportExceptionHandler`, and
`ResourceServerConfig`'s shared `writeProblemJson` helper to use it.

**Out:** any change to `title`/`detail`/`status`/exception-class shape; `contracts/api/payments.yaml`
(does not exist yet, task 24's own job); any new validation/exception/error condition not already
present.

## Business Rules

- **R29.**

## Locked Decisions

- **L10/L12 (continuity)** — see Phase 1.
- **New, resolved directly (low-risk implementation choice, no `AskUserQuestion` needed): the
  exact eleven-constant catalogue, mirroring `auth-service`'s own `ProblemTypes` shape exactly**
  (`final` class, private constructor, one shared `BASE` prefix, kebab-case suffixes, a doc
  comment only where non-obvious):
  - `VALIDATION_ERROR` (`validation-error`) — shared by `MethodArgumentNotValidException` and
    `ConstraintViolationException`, mirroring `auth-service`'s own identical sharing.
  - `MALFORMED_REQUEST` (`malformed-request`) — `HttpMessageNotReadableException`, mirroring
    `auth-service` exactly.
  - `MALFORMED_REQUEST_PARAMETER` (`malformed-request-parameter`) — `MethodArgumentTypeMismatchException`.
    New: `auth-service` has no equivalent handler.
  - `MISSING_REQUIRED_PARAMETER` (`missing-required-parameter`) — `MissingServletRequestParameterException`.
    New: `auth-service` has no equivalent handler (T22's own fix).
  - `FORBIDDEN` (`forbidden`) — shared by `ApiExceptionHandler`'s `AuthorizationDeniedException`
    handler AND `ResourceServerConfig`'s manually-constructed `AccessDeniedHandler` bean — both are
    logically "the caller is authenticated but lacks the required access," just reached via two
    different Spring Security mechanisms (method-security vs. filter-chain).
  - `UNAUTHORIZED` (`unauthorized`) — `ResourceServerConfig`'s `AuthenticationEntryPoint` bean
    (no/invalid JWT).
  - `INTERNAL_ERROR` (`internal-error`) — the `Exception` catch-all, mirroring `auth-service`
    exactly.
  - `INVALID_INVOICE_REQUEST` (`invalid-invoice-request`) — `InvalidInvoiceRequestException`.
  - `CRYPTO_SERVICE_UNAVAILABLE` (`crypto-service-unavailable`) — `CryptoServiceUnavailableException`.
  - `RESOURCE_NOT_FOUND` (`resource-not-found`) — `InvoiceNotFoundException` today; named
    generically (not `INVOICE_NOT_FOUND`) since R28's own text already anticipates a future
    receipt-lookup endpoint sharing the identical "404 either way, no ownership oracle" rule —
    mirrors `auth-service`'s own generic, reusable `NOT_FOUND` constant shared across
    `AuthzExceptionHandler`/`AccountExceptionHandler`, rather than a one-off per-resource name.
  - `INVALID_EXPORT_REQUEST` (`invalid-export-request`) — `InvalidExportRequestException`.

## Dependencies

`java.net.URI` only. No new library/config/contract.

## Inputs

None — a pure constants class plus mechanical call-site updates.

## Outputs

`common/ProblemTypes.java`: eleven `public static final URI` constants, private constructor,
class-level Javadoc mirroring `auth-service`'s own ("Add new types here; never inline URIs at
call sites, and never change a published value.").

## State Changes

None.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/common/ProblemTypes.java`.

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` — 7 sites.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java` — 3 sites.
- `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java` — 1 site.
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java` — 1 site
  (the shared `writeProblemJson` helper now takes the `type` as a parameter rather than
  hardcoding it, since its two callers need different values).
- Test files needing a mechanical update: `InvoiceControllerTest` (6 assertions),
  `ResourceServerConfigTest` (1), `AccessDeniedHandlerTest` (1) — deferred to Phase 10 per the
  standard rule, but listed here since they are a direct, certain consequence of this change,
  not new test-writing.

## Files NOT to Modify

Any exception class's own shape/message; `ExportControllerTest` (no existing `$.type` assertion
to fix, though a new one may be *added* at Phase 10 — that is new test-writing, not a
modification); anything under `spec/`.

## Acceptance Criteria

- **AC1.** `ProblemTypes` defines exactly the eleven constants above, each a distinct `URI`, no
  two sharing a value except where explicitly intended (`VALIDATION_ERROR`, `FORBIDDEN`).
- **AC2.** Every one of the twelve existing `setType("about:blank")` call sites now uses the
  matching `ProblemTypes` constant; zero remaining literal `"about:blank"` string anywhere in
  `services/payment/src/main/java`.
- **AC3.** No 4xx/5xx response body anywhere in this service contains a stack trace, a raw
  exception message from an unexpected failure, or any other internal detail — continuity,
  re-confirmed, not changed.
- **AC4.** `ResourceServerConfig`'s two beans (401, 403) each emit their own correct, distinct
  `type` (`UNAUTHORIZED` vs. `FORBIDDEN`) via the same shared helper, not two separate
  hardcoded literals reintroduced accidentally.

## Required Tests

- **Named test:** `shouldReturnProblemJsonWithNoInternalDetailOn4xx` (R29) — asserts a
  representative 4xx body's full RFC 9457 shape, including a non-`"about:blank"` catalogue
  `type`.
- Mechanical updates to the 8 existing assertion sites identified at Phase 0 (6 in
  `InvoiceControllerTest`, 1 in `ResourceServerConfigTest`, 1 in `AccessDeniedHandlerTest`).
- A new direct test on `ProblemTypes` itself: every constant is a distinct, well-formed `URI`
  except the two intentionally-shared pairs; the class cannot be instantiated.
- A new `$.type` assertion added to `ExportControllerTest`'s existing 4xx tests (currently assert
  only `$.title`) — R29 applies service-wide, not only where a prior task happened to assert it.

## Constraints

- **Performance/thread-safety:** irrelevant — `ProblemTypes` is immutable constants; no new
  runtime behavior anywhere.
- **Security:** continuity of R29's own "no internal detail" half — re-verify, don't weaken.
- **Module boundaries:** `ProblemTypes` lives in `common`, imports nothing from any feature
  module (L12).
- **Null handling:** n/a — no new nullable field anywhere.

## Open Questions

No blockers. Q-A from Phase 1 (future `contracts/api/payments.yaml` referencing these URIs) is
explicitly deferred to task 24, not resolved here.
