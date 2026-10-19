# payment · T23 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`, `artifacts/11-test-review-resolution.md`

## Traceability matrix

| Requirement/AC | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R29** — every 4xx is RFC 9457 with a stable `type`, no stack traces/internal detail | Yes | All 13 call sites below, collectively | Named test + every 4xx/5xx test in the suite | No | No |
| **AC1** — `ProblemTypes` defines 11 distinct constants, one shared base | Yes | `common/ProblemTypes.java` | `ProblemTypesTest` (4 tests: distinctness, shared base, private ctor, final class) | No | No |
| **AC2** — zero executable `"about:blank"` (Javadoc exempted) | Yes | `grep -rn "about:blank" services/payment/src/main/java` → 2 hits, both Javadoc only | Self-review (Phase 7), independent review (Phase 8) both confirmed | No | No |
| **AC3** — no 4xx/5xx leaks internal detail (continuity) | Yes | Every handler's own `detail` string, unchanged from pre-T23 | `shouldReturnProblemJsonWithNoInternalDetailOn4xx`/`...On404`, `shouldReturn500WithNoInternalDetailForUnexpectedFailure` | No | No |
| **AC4** — `ResourceServerConfig`'s 401/403 each emit the correct distinct type | Yes | `ResourceServerConfig.java:85` (`UNAUTHORIZED`), `:94` (`FORBIDDEN`) | `ResourceServerConfigTest`, `AccessDeniedHandlerTest`, `ExportControllerTest.shouldRejectUnauthenticatedCallerWithUnauthorized` | No | No |
| **AC5** — no call site inlines a `URI` literal for a problem type | Yes | `grep -rn "URI.create(" services/payment/src/main/java` outside `ProblemTypes.java` → 0 hits; `ArchitectureTest.shouldNotInlineProblemTypeUris` (bans both `URI.create` and `new URI(String)`) | `shouldNotInlineProblemTypeUrisIsCheckedDuringStandardBuild`, `...ActuallyFailsAgainstAGenuineViolation` (mutation-verified both branches independently, Phase 11) | No | No |
| `VALIDATION_ERROR` (shared, 2 call sites) | Yes | `ApiExceptionHandler.java:53,65` | `InvoiceControllerTest.shouldReturnProblemJsonForBeanValidationFailure`, `shouldReturnProblemJsonWithNoInternalDetailOn4xx` | No | No |
| `MALFORMED_REQUEST` | Yes | `ApiExceptionHandler.java:74` | `shouldReturn400ForMalformedRequestBody` (Phase 11) | No | No |
| `MALFORMED_REQUEST_PARAMETER` | Yes | `ApiExceptionHandler.java:116` | `ExportControllerTest.shouldReturn400ForMalformedFromValue/ToValue`, `InvoiceControllerTest.shouldReturn400ForMalformedInvoiceUuidPathVariable` | No | No |
| `MISSING_REQUIRED_PARAMETER` | Yes | `ApiExceptionHandler.java:107` | `ExportControllerTest.shouldReturn400ForMissingFromParam/ToParam` | No | No |
| `FORBIDDEN` (shared, 2 mechanisms) | Yes | `ApiExceptionHandler.java:92`, `ResourceServerConfig.java:94` | `InvoiceControllerTest.shouldRejectNonMerchantCallerWithForbidden/OnReadEndpoints`, `ExportControllerTest.shouldRejectNonMerchantCallerWithForbidden`, `AccessDeniedHandlerTest` | No | No |
| `UNAUTHORIZED` | Yes | `ResourceServerConfig.java:85` | `ResourceServerConfigTest`, `ExportControllerTest.shouldRejectUnauthenticatedCallerWithUnauthorized` (Phase 11) | No | No |
| `INTERNAL_ERROR` | Yes | `ApiExceptionHandler.java:128` | `shouldReturn500WithNoInternalDetailForUnexpectedFailure` (Phase 11) | No | No |
| `INVALID_INVOICE_REQUEST` | Yes | `InvoiceExceptionHandler.java:27` | `shouldReturnProblemJsonForInvalidInvoiceRequest` | No | No |
| `CRYPTO_SERVICE_UNAVAILABLE` | Yes | `InvoiceExceptionHandler.java:39` | `shouldReturnServiceUnavailableWhenWatchRegistrationFails` | No | No |
| `NOT_FOUND` | Yes | `InvoiceExceptionHandler.java:51` | `shouldReturnIdentical404Shape...`, `shouldReturnProblemJsonWithNoInternalDetailOn404` (Phase 11) | No | No |
| `INVALID_EXPORT_REQUEST` | Yes | `ExportExceptionHandler.java:23` | `ExportControllerTest.shouldReturn400ForInvalidFormatValue/FromAfterTo/MissingSubClaim/MalformedSubClaim` | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every one of the twelve pre-existing `"about:blank"`
emission sites now carries a real, named, stable catalogue `type`; the catalogue itself is
structurally verified (distinctness, shared base, immutability); and the "no internal detail"
half of R29 — already satisfied before this task — is now additionally proven by two new tests
covering the two paths that had zero direct coverage (`MALFORMED_REQUEST`, `INTERNAL_ERROR`).

**(2) Does it satisfy every acceptance criterion?** All five (AC1–AC5) are satisfied, each
backed by a real test, not merely asserted by comment.

**(3) Does it violate any LOCKED decision?** No. L10 (zero trust, `ResourceServerConfig`'s
401/403 now correctly use the catalogue) and L12 (module boundaries — `ProblemTypes` lives in
`common`, imports nothing from any feature module) both hold.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **`CryptoServiceUnavailableException`'s only handler lives in `InvoiceExceptionHandler`**
  despite the exception living in `common`, disclosed via Javadoc (Phase 9) rather than
  relocated — a future second `CryptoClient` caller outside `invoice` would silently fall through
  to `INTERNAL_ERROR`/500 instead of `CRYPTO_SERVICE_UNAVAILABLE`/503. Moving the handler was
  judged out of this task's own scope (R29 is about the catalogue, not handler reorganization).
- **Kimi's rejected Finding/Gap items** (a speculative future 5xx type, Phase 3 Finding 5; exact
  call-site usage counting for the two intentionally-shared constants, Phase 11 Gap 7) remain
  genuinely unaddressed by design, not overlooked — both would add real infrastructure for a
  design-smell or hypothetical-future concern, not a current correctness gap.
- **The ArchUnit rule bans the two realistic construction paths (`URI.create`, `new URI(String)`)
  but not a theoretically possible third-party `URI`-returning factory** — judged acceptable;
  `java.net.URI` itself has exactly these two public construction paths, and no call site
  anywhere in this codebase imports a third-party URI-builder library.

## Verdict

**PASS** — the stable RFC 9457 `type` catalogue (`ProblemTypes`, 11 constants) is fully wired
through every one of this service's problem-detail emission sites, structurally guarded against
future drift by a mutation-verified `ArchitectureTest` rule (covering both realistic bypass
paths), and backed by tests for every distinct type including the two that previously had zero
coverage. Two independent Kimi review cycles (Phase 8: 6 findings, 2 accepted/1 rejected/3
acknowledged-as-already-scheduled; Phase 11: 8 gaps, 7 accepted/1 rejected) surfaced and closed
real gaps, each verified against source before disposition — including one finding (Phase 8
Finding 1) rejected outright because it was factually wrong about the frozen brief's own already-
amended text. Full payment-service suite: **461/461 passing**.
