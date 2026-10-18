# payment · T23 · Phase 0 — Repository Understanding

Consumes: — (entry phase)

## 1. Architecture summary

Unchanged from every prior task's own grounding — Spring Boot 3.5.4, Java 21, one Postgres
schema (`payments`), Flyway, package-by-feature under `com.themistra.payment`, OAuth2 resource
server against the Auth Service JWKS, outbox-only Kafka publishing. This task touches only the
cross-cutting error-handling layer, not any domain module's own persistence/business logic.

## 2. Existing code this task touches

**Already exists — every current RFC 9457 emission site in this service, confirmed by direct
grep, not assumed:**
- `common/ApiExceptionHandler.java` — the framework-level, `@Order(LOWEST_PRECEDENCE)` catch-all
  advice (T03, extended T06/T22). Seven `@ExceptionHandler` methods today:
  `MethodArgumentNotValidException`/`ConstraintViolationException` (both "Validation failed"),
  `HttpMessageNotReadableException` ("Malformed request body"), `AuthorizationDeniedException`
  ("Forbidden", T06 fix for `@PreAuthorize` denial), `MissingServletRequestParameterException`
  ("Missing request parameter", T22 fix), `MethodArgumentTypeMismatchException` ("Malformed
  request parameter"), and `Exception` ("Internal error", 500, trace_id-correlated).
- `invoice/InvoiceExceptionHandler.java` — `@Order(HIGHEST_PRECEDENCE)` domain advice (T06/T07).
  Three handlers: `InvalidInvoiceRequestException` (400), `CryptoServiceUnavailableException`
  (503), `InvoiceNotFoundException` (404).
- `export/ExportExceptionHandler.java` — same shape (T22). One handler:
  `InvalidExportRequestException` (400).
- `common/ResourceServerConfig.java` — two manually-constructed `AccessDeniedHandler`/
  `AuthenticationEntryPoint` beans (T03), each calling a shared private `writeProblemJson`
  helper, for the filter-chain-level 401 ("Unauthorized") and 403 ("Forbidden") cases —
  distinct from `ApiExceptionHandler`'s own `AuthorizationDeniedException` handler, which fires
  for a method-security (`@PreAuthorize`) denial *inside* the filter chain, not at its edge.

**The concrete, confirmed gap this task exists to close**: every single one of the twelve
`setType(...)` call sites across these four classes currently hardcodes the literal string
`"about:blank"` — there is no catalogue at all today, despite `agents.md`'s own standing rule
(line 47) explicitly saying "a stable `type` catalogue," worded identically to this task's own
text. `"about:blank"` is technically RFC 9457-valid (it is the spec's own designated default for
"no further information"), but it is definitionally not a catalogue — every distinct error
condition is indistinguishable by `type` alone today.

**New for this task:** nothing per `design.md`'s own package map was found naming a
`ProblemTypes`-equivalent class for this service — confirmed by grep across the full package map
(`design.md` lines 418-431). Phase 2 will need to decide its exact shape and package.

## 3. Established patterns to follow

**A real, already-built cross-service precedent exists and should be mirrored, not invented
from scratch**: `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — a
`final` class, private constructor, `public static final URI` constants built from one shared
`BASE = "https://checky.pro/problems/"` prefix, kebab-case suffixes
(`validation-error`, `malformed-request`, `not-found`, `session-not-found`, ...), with a short
Javadoc only on the non-obvious/enumeration-sensitive entries (mirrors this session's own
"comment only when the why is non-obvious" convention already in force). Its own class Javadoc:
"Add new types here; never inline URIs at call sites, and never change a published value." Its
own doc comment also calls it "part of the public API contract (`contracts/api/auth.yaml`)" —
relevant since `contracts/api/payments.yaml` does not exist yet (task 24, not yet built); this
task's own `type` values will need to be referenced from that contract once it is authored, not
retrofitted.

**A real, confirmed divergence from the auth-service precedent, not an oversight to fix**:
`crypto-service` and `notification-service` have **no** `ProblemTypes` class at all (confirmed
by `find`) — only `auth-service` has built this so far. `auth-service`'s own `ApiExceptionHandler`
is also narrower than payment's own (3 handlers: validation x2, unreadable-body, unexpected) —
it has no `AuthorizationDeniedException`, `MissingServletRequestParameterException`, or
`MethodArgumentTypeMismatchException` handler at all, since it never needed them. Payment's own
catalogue will need at least two genuinely new type entries auth's own precedent has no
equivalent for.

- Config: flat `application.properties`, validated `@ConfigurationProperties`.
- Error handling (agents.md, verbatim): "Errors are RFC 9457 `application/problem+json` with a
  stable `type` catalogue — no stack traces, no internal detail, no enumeration hints."
- Every current 4xx `detail` string was independently confirmed, task by task, to already avoid
  leaking internal detail (e.g., T22's own confirmation that `onTypeMismatch`'s `detail` string
  contains only the parameter *name*, never the rejected value). No new leak was found in this
  task's own re-read of all twelve sites.

## 4. Testing conventions

- `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors.jwt()` for controller-level RFC 9457 body
  assertions (every controller test this session, e.g. `InvoiceControllerTest`,
  `ExportControllerTest`).
- Plain unit tests are sufficient for a pure catalogue class (`ProblemTypes` itself needs no
  Spring context — mirrors `auth-service`'s own, which likewise has no dedicated unit test file
  of its own, confirmed by `find`).
- Named test: `shouldReturnProblemJsonWithNoInternalDetailOn4xx` (`package.md` §8) → R29.

## 5. Known gaps / unknowns

- Confirmed by direct grep (not assumed): exactly **7** existing `jsonPath("$.type")` assertions
  currently expect `"about:blank"` literally — 6 in `InvoiceControllerTest`, 1 in
  `ResourceServerConfigTest` — plus one raw-string equivalent in `AccessDeniedHandlerTest`
  (`.contains("\"type\":\"about:blank\"")`, not a `jsonPath` assertion). `ExportControllerTest`
  asserts `$.title` on its error paths but never `$.type`, so it needs no change. All 8 sites
  will need updating to the new catalogue values — exact mapping to be enumerated in Phase 2/5.
- I do not know whether `contracts/api/payments.yaml` (task 24, not yet authored) is expected to
  reference these exact `type` URIs, or whether that linkage is deferred entirely to that later
  task. Logged as an open question for Phase 1/2 — not a blocker for this task's own scope.
