# payment · T23 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/common/ProblemTypes.java`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java`
2. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java`
3. `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java`
4. `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java`

## Public types/methods (signatures)

**`ProblemTypes`** (frozen, Phase 4):
```java
public final class ProblemTypes {
    private static final String BASE = "https://checky.pro/problems/";
    public static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    public static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    public static final URI MALFORMED_REQUEST_PARAMETER = URI.create(BASE + "malformed-request-parameter");
    public static final URI MISSING_REQUIRED_PARAMETER = URI.create(BASE + "missing-required-parameter");
    public static final URI FORBIDDEN = URI.create(BASE + "forbidden");
    public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");
    public static final URI INVALID_INVOICE_REQUEST = URI.create(BASE + "invalid-invoice-request");
    public static final URI CRYPTO_SERVICE_UNAVAILABLE = URI.create(BASE + "crypto-service-unavailable");
    public static final URI NOT_FOUND = URI.create(BASE + "not-found");
    public static final URI INVALID_EXPORT_REQUEST = URI.create(BASE + "invalid-export-request");
    private ProblemTypes() { }
}
```

## Private methods

- `ResourceServerConfig.writeProblemJson` — frozen new signature (Phase 4 Finding 6):
  `private static void writeProblemJson(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, URI type, String title, String detail)`.
  Its two callers (`problemJsonAuthenticationEntryPoint`, `problemJsonAccessDeniedHandler`) each
  pass their own `ProblemTypes` constant (`UNAUTHORIZED`, `FORBIDDEN`), and the body-building
  line changes from `body.put("type", "about:blank")` to `body.put("type", type.toString())`.

## Exact call-site mapping (every one of the 12 existing sites)

| File | Handler | Old | New |
|---|---|---|---|
| `ApiExceptionHandler` | `onValidationFailure` | `about:blank` | `ProblemTypes.VALIDATION_ERROR` |
| `ApiExceptionHandler` | `onConstraintViolation` | `about:blank` | `ProblemTypes.VALIDATION_ERROR` |
| `ApiExceptionHandler` | `onUnreadableBody` | `about:blank` | `ProblemTypes.MALFORMED_REQUEST` |
| `ApiExceptionHandler` | `onAuthorizationDenied` | `about:blank` | `ProblemTypes.FORBIDDEN` |
| `ApiExceptionHandler` | `onMissingParameter` | `about:blank` | `ProblemTypes.MISSING_REQUIRED_PARAMETER` |
| `ApiExceptionHandler` | `onTypeMismatch` | `about:blank` | `ProblemTypes.MALFORMED_REQUEST_PARAMETER` |
| `ApiExceptionHandler` | `onUnexpected` | `about:blank` | `ProblemTypes.INTERNAL_ERROR` |
| `InvoiceExceptionHandler` | `onInvalidRequest` | `about:blank` | `ProblemTypes.INVALID_INVOICE_REQUEST` |
| `InvoiceExceptionHandler` | `onCryptoServiceUnavailable` | `about:blank` | `ProblemTypes.CRYPTO_SERVICE_UNAVAILABLE` |
| `InvoiceExceptionHandler` | `onInvoiceNotFound` | `about:blank` | `ProblemTypes.NOT_FOUND` |
| `ExportExceptionHandler` | `onInvalidRequest` | `about:blank` | `ProblemTypes.INVALID_EXPORT_REQUEST` |
| `ResourceServerConfig` | `problemJsonAuthenticationEntryPoint` (401) | `about:blank` | `ProblemTypes.UNAUTHORIZED` |
| `ResourceServerConfig` | `problemJsonAccessDeniedHandler` (403) | `about:blank` | `ProblemTypes.FORBIDDEN` |

(13 rows — `ApiExceptionHandler.onValidationFailure`/`onConstraintViolation` are the two call
sites sharing one Phase-0-counted "site," consistent with the frozen brief's own "12 sites"
figure, which counted distinct `setType` statements, not distinct handler methods.)

## Entities used

None — zero persistence involvement.

## Repositories/Services used

None.

## Unit/integration tests required

Deferred to Phase 10 per the standard rule (this task is not test-only), but the exact required
changes are already fully enumerated by the frozen brief:
- **`ProblemTypesTest`** (new): every constant is a distinct `URI` except the two intentionally-
  shared pairs (`VALIDATION_ERROR`, `FORBIDDEN`); class cannot be instantiated.
- **`InvoiceControllerTest`** (modify 6 assertions across 5 named methods — Phase 4's own table).
- **`ResourceServerConfigTest`** (modify 1 assertion).
- **`AccessDeniedHandlerTest`** (modify 1 assertion).
- **`ExportControllerTest`** (add new `$.type` assertions to its existing 4xx tests — new
  test-writing, not a modification of an existing assertion).
- Named test `shouldReturnProblemJsonWithNoInternalDetailOn4xx` (R29) — likely satisfied by
  strengthening one existing representative test (e.g. a validation-failure test) rather than a
  wholly new method; exact placement decided at Phase 10.

## Execution order

1. `ProblemTypes` — no dependency.
2. `ApiExceptionHandler`, `InvoiceExceptionHandler`, `ExportExceptionHandler` — each depends only
   on 1, independent of each other.
3. `ResourceServerConfig` — depends on 1 (signature change is self-contained, independent of 2).
4. All test files (Phase 10).

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create"/"Files to Modify"
lists (Phase 4). No file is added that the brief does not authorize.
