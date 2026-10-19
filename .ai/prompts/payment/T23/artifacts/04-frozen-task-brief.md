# payment · T23 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`.
Approved by the human via `AskUserQuestion`, 2026-10-19 ("Approve as proposed").

## Disposition of Kimi's 7 Phase 3 findings

| # | Finding | Disposition | Reason |
|---|---|---|---|
| 1 | `BASE` URI not frozen | **ACCEPTED** | Pinned: `private static final String BASE = "https://checky.pro/problems/";` — identical to `auth-service`'s own, confirmed at `auth/common/ProblemTypes.java:11`. |
| 2 | `RESOURCE_NOT_FOUND` contradicts the brief's own stated intent to mirror `auth-service`'s `NOT_FOUND` | **ACCEPTED** | A real self-contradiction in my own Phase 2 TIB, confirmed against `auth/common/ProblemTypes.java:15`. Renamed `RESOURCE_NOT_FOUND`/`resource-not-found` → `NOT_FOUND`/`not-found`, matching `auth-service` exactly — no real reason for payment-service to diverge here. |
| 3 | AC2's "zero `about:blank`" rule is ambiguous about Javadoc comments | **ACCEPTED, scoped** | Confirmed two Javadoc sites (`InvoiceExceptionHandler.java:17`, `ApiExceptionHandler.java:29`) narrate a past phase's decision using the literal string. AC2 now reads "zero remaining literal `\"about:blank\"` in any executable `setType(...)`/`writeProblemJson(...)` call" — the historical Javadoc prose is left untouched, per this session's own "disclose forward, don't rewrite history" convention; it remains a true historical statement about what Phase 9 did, not a claim about current behavior. |
| 4 | No governance rule for future problem types | **ACCEPTED** | New Constraint: "Every new problem type is added to `ProblemTypes`, never inlined at a call site; referenced from `contracts/api/payments.yaml` once task 24 authors it; a published URI is never changed — add a new constant and deprecate the old one instead." |
| 5 | Catalogue may be incomplete for a future 5xx surface | **REJECTED** | Speculative — Kimi's own finding calls it so. No current call site needs it; adding a type for a hypothetical future dependency failure is exactly the "don't design for hypothetical future requirements" case this session consistently declines. |
| 6 | `writeProblemJson` helper signature not frozen | **ACCEPTED** | Frozen exactly: `private static void writeProblemJson(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, URI type, String title, String detail)` — taking `URI`, not `String`, so a caller cannot pass an arbitrary uncatalogued literal. |
| 7 | The 6 `InvoiceControllerTest` sites aren't named | **ACCEPTED** | Confirmed by direct grep: `shouldRejectNonMerchantCallerWithForbidden`, `shouldReturnServiceUnavailableWhenWatchRegistrationFails`, `shouldReturnProblemJsonForInvalidInvoiceRequest`, `shouldReturnProblemJsonForBeanValidationFailure`, `shouldRejectNonMerchantCallerOnReadEndpoints` (2 assertions in this one method — asserts the same 403 body shape twice, once per endpoint). |

## Task

Add a stable RFC 9457 `type` catalogue (`common/ProblemTypes.java`) and update all twelve
existing `setType("about:blank")` call sites to use it (R29), as detailed in Phase 2's TIB,
amended per the table above.

## Scope

Unchanged from Phase 2, plus the governance constraint (Finding 4) and the frozen helper
signature (Finding 6).

## Business Rules

- **R29.**

## Locked Decisions

All ten remaining catalogue constants (Phase 2, minus the renamed one) stand:

`VALIDATION_ERROR` (`validation-error`), `MALFORMED_REQUEST` (`malformed-request`),
`MALFORMED_REQUEST_PARAMETER` (`malformed-request-parameter`),
`MISSING_REQUIRED_PARAMETER` (`missing-required-parameter`), `FORBIDDEN` (`forbidden`),
`UNAUTHORIZED` (`unauthorized`), `INTERNAL_ERROR` (`internal-error`),
`INVALID_INVOICE_REQUEST` (`invalid-invoice-request`),
`CRYPTO_SERVICE_UNAVAILABLE` (`crypto-service-unavailable`),
`INVALID_EXPORT_REQUEST` (`invalid-export-request`), and now **`NOT_FOUND` (`not-found`)**
(renamed from `RESOURCE_NOT_FOUND`, Finding 2) — eleven total, base URI frozen at
`https://checky.pro/problems/` (Finding 1).

## Dependencies

Unchanged — `java.net.URI` only.

## Inputs / Outputs / State Changes

Unchanged from Phase 2, except the `writeProblemJson` helper's signature is now frozen exactly
(Finding 6).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/common/ProblemTypes.java`.

## Files to Modify

Unchanged list from Phase 2 (`ApiExceptionHandler`, `InvoiceExceptionHandler`,
`ExportExceptionHandler`, `ResourceServerConfig`), plus the exact five `InvoiceControllerTest`
methods named above (Finding 7), `ResourceServerConfigTest` (1 site), `AccessDeniedHandlerTest`
(1 site) — all test changes deferred to Phase 10 per the standard rule, listed here only because
they are a certain, enumerated consequence.

## Files NOT to Modify

Unchanged from Phase 2: no exception class's shape/message changes; `ExportControllerTest` (no
existing `$.type` assertion to fix — a new one is new test-writing, Phase 10's job); the two
historical Javadoc comments naming `"about:blank"` (Finding 3 — left as accurate history);
anything under `spec/`.

## Acceptance Criteria

- **AC1.** `ProblemTypes` defines exactly eleven constants (updated list above), each a distinct
  `URI` except the two intentionally-shared pairs (`VALIDATION_ERROR`, `FORBIDDEN`), base
  `https://checky.pro/problems/` (Finding 1).
- **AC2 (scoped, Finding 3).** Zero remaining literal `"about:blank"` in any executable
  `setType(...)`/`writeProblemJson(...)` call anywhere in `services/payment/src/main/java` — the
  two historical Javadoc mentions are explicitly out of scope.
- **AC3.** Continuity — no 4xx/5xx body contains a stack trace or internal detail.
- **AC4.** `ResourceServerConfig`'s 401/403 beans each emit their own correct `type`
  (`UNAUTHORIZED`/`FORBIDDEN`) via the frozen `writeProblemJson` signature (Finding 6).
- **AC5 (Finding 4, new).** No call site anywhere inlines a `URI.create(...)` literal for a
  problem type — every one resolves through a `ProblemTypes` constant.

## Required Tests

Unchanged from Phase 2, plus: the exact five named `InvoiceControllerTest` methods (Finding 7)
plus `ResourceServerConfigTest`/`AccessDeniedHandlerTest`'s own single sites each.

## Constraints

Unchanged from Phase 2, plus the new governance constraint (Finding 4): every new problem type
is added to `ProblemTypes`, never inlined; referenced from `contracts/api/payments.yaml` once
task 24 authors it; a published URI is never changed in place — a new constant is added and the
old one deprecated instead.

## Open Questions

No blockers. Finding 5 (speculative future 5xx type) rejected — not resolved, declined.
