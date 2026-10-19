# payment · T23 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the actual updated tests against the frozen brief's acceptance criteria (AC1–AC5).

---

## Gap 1 — The 404 test does not assert the `type`

- **Why it matters:** `shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid` verifies the identical body for both 404 causes, but it only asserts `$.title` and compares the two bodies. R29's service-wide stable-type requirement means `$.type` should also be pinned to `ProblemTypes.NOT_FOUND`.
- **Suggested test:** Add `.andExpect(jsonPath("$.type").value(ProblemTypes.NOT_FOUND.toString()))` to both the non-owning and nonexistent requests, or include it in the string comparison after normalizing UUIDs.

---

## Gap 2 — The malformed path-variable test does not assert the `type`

- **Why it matters:** `shouldReturn400ForMalformedInvoiceUuidPathVariable` exercises the `MALFORMED_REQUEST_PARAMETER` path, but it only asserts `$.title`. A regression that changed the type would not be caught.
- **Suggested test:** Add `.andExpect(jsonPath("$.type").value(ProblemTypes.MALFORMED_REQUEST_PARAMETER.toString()))`.

---

## Gap 3 — The unauthenticated export test does not assert the `type`

- **Why it matters:** `shouldRejectUnauthenticatedCallerWithUnauthorized` in `ExportControllerTest` only asserts `isUnauthorized()`. The 401 handler emits `ProblemTypes.UNAUTHORIZED`, which is already tested in `ResourceServerConfigTest`, but the export endpoint's own path is not verified to produce the same shape.
- **Suggested test:** Add `.andExpect(jsonPath("$.type").value(ProblemTypes.UNAUTHORIZED.toString()))` and `.andExpect(jsonPath("$.title").value("Unauthorized"))` to the unauthenticated export test.

---

## Gap 4 — The named R29 test only exercises one 4xx type

- **Why it matters:** `shouldReturnProblemJsonWithNoInternalDetailOn4xx` uses a validation failure (`VALIDATION_ERROR`). R29 applies to every 4xx path in the service. A bug that leaked internal detail only in, say, a 404 or 503 response would not be caught by this test.
- **Suggested test:** Extend the named test to exercise at least one other status/type (e.g., trigger `InvalidInvoiceRequestException` for `INVALID_INVOICE_REQUEST`, or `InvoiceNotFoundException` for `NOT_FOUND`) and assert the same "no exception/stacktrace/.java" invariants, or add separate `shouldReturnProblemJsonWithNoInternalDetailOn<Status>` tests for each type.

---

## Gap 5 — No test exercises the `MALFORMED_REQUEST` type

- **Why it matters:** `MALFORMED_REQUEST` is assigned to `HttpMessageNotReadableException` (invalid JSON body). No test sends syntactically invalid JSON to any endpoint and asserts the resulting `type`.
- **Suggested test:** In `InvoiceControllerTest`, POST a request body such as `{"chain":` (truncated/invalid JSON) with a merchant JWT and assert 400 with `$.type` equal to `ProblemTypes.MALFORMED_REQUEST`.

---

## Gap 6 — No test exercises the `INTERNAL_ERROR` type or the 500 "no internal detail" rule

- **Why it matters:** The catch-all handler must return `INTERNAL_ERROR` with no stack trace or exception class in the body. This is only indirectly covered today; a regression that leaked a raw exception message would not be caught.
- **Suggested test:** Add a controller test with a mock service that throws a raw `RuntimeException("sensitive internal detail")` and assert 500 with `$.type` equal to `ProblemTypes.INTERNAL_ERROR` and a body that does not contain the raw message, any exception class name, or `.java:`.

---

## Gap 7 — `ProblemTypesTest` does not verify the intentionally-shared constants are used by exactly two call sites

- **Why it matters:** `VALIDATION_ERROR` and `FORBIDDEN` are each shared by two handlers. A future change that accidentally made a third handler reuse `VALIDATION_ERROR` (instead of adding a new type) would not fail `everyConstantIsADistinctAbsoluteUri`.
- **Suggested test:** Add a reflection/ArchUnit-based test that counts references to each `ProblemTypes` field in production classes and asserts `VALIDATION_ERROR` and `FORBIDDEN` have exactly two usages each while all others have exactly one.

---

## Gap 8 — `shouldNotInlineProblemTypeUris` only bans `URI.create`, not arbitrary non-catalogue `URI` references

- **Why it matters:** The rule prevents inline `URI.create(...)`, but it does not prevent someone from introducing another `public static final URI` constant elsewhere and passing it to `ProblemDetail.setType`. That would violate the spirit of the stable catalogue without calling `URI.create` outside `ProblemTypes`.
- **Suggested test:** Add an ArchUnit rule that every call to `ProblemDetail.setType(URI)` inside the service must reference a field declared in `ProblemTypes`, with a `RogueInlineProblemTypeUser`-style canary to prove it fails.

---

## Summary

The Phase 10 suite is comprehensive for the main catalogue mechanics: constants are distinct, the base is uniform, call sites use the catalogue, existing assertions are updated, and ExportController 4xx responses now assert type. The most important additions before closing T23 are Gap 1 (404 type), Gap 2 (malformed path-variable type), Gap 4 (R29 test covering more than validation), and Gap 5 (`MALFORMED_REQUEST` type). Gaps 6–8 are lower-priority but would make the R29 coverage truly exhaustive.
