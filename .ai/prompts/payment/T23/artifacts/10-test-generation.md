# payment · T23 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase (aside from the mutation-test revert, confirmed reverted).

## Test files modified

1. **`InvoiceControllerTest.java`** — updated 6 existing `$.type` assertions (5 methods, one with
   two assertion sites) from the literal `"about:blank"` to the matching `ProblemTypes` constant
   (`FORBIDDEN` ×3, `CRYPTO_SERVICE_UNAVAILABLE`, `INVALID_INVOICE_REQUEST`, `VALIDATION_ERROR`).
   Added the named test `shouldReturnProblemJsonWithNoInternalDetailOn4xx` (R29).
2. **`ResourceServerConfigTest.java`** — updated 1 assertion (`UNAUTHORIZED`).
3. **`AccessDeniedHandlerTest.java`** — updated 1 raw-string assertion (`FORBIDDEN`).
4. **`ExportControllerTest.java`** — added new `$.type` assertions to 7 existing 4xx tests that
   previously asserted only `$.title` (`INVALID_EXPORT_REQUEST` ×4, `MISSING_REQUIRED_PARAMETER`
   ×2, `MALFORMED_REQUEST_PARAMETER` ×2, `FORBIDDEN` ×1 — some tests map to more than one
   constant across the full list).

## Test files created

1. **`ProblemTypesTest.java`** (4 tests): every constant is a distinct, non-null, absolute `URI`
   (reflection-based, no duplicate catalogue values); every constant shares the one base
   (`https://checky.pro/problems/`); the class has a private no-arg constructor; the class is
   `final`.

## Traceability matrix

| Test | File | Maps to |
|---|---|---|
| `shouldReturnProblemJsonWithNoInternalDetailOn4xx` | `InvoiceControllerTest` | Named test (R29) |
| `everyConstantIsADistinctAbsoluteUri` | `ProblemTypesTest` | AC1 |
| `everyConstantSharesTheSameBase` | `ProblemTypesTest` | AC1 (Phase 3 Kimi Finding 1) |
| `hasAPrivateNoArgConstructor` / `isAFinalClass` | `ProblemTypesTest` | AC1 (mirrors `auth-service`'s own shape) |
| 6 updated `InvoiceControllerTest` assertions | `InvoiceControllerTest` | AC2 |
| 1 updated `ResourceServerConfigTest` assertion | `ResourceServerConfigTest` | AC2, AC4 |
| 1 updated `AccessDeniedHandlerTest` assertion | `AccessDeniedHandlerTest` | AC2, AC4 |
| 7 new `ExportControllerTest` `$.type` assertions | `ExportControllerTest` | AC2 (service-wide, not only where a prior task happened to assert it) |
| `shouldNotInlineProblemTypeUrisIsCheckedDuringStandardBuild` / `...ActuallyFailsAgainstAGenuineViolation` | `ArchitectureTest` (Phase 9) | AC5 |

## Mutation-testing performed this phase

Edited `ProblemTypes.NOT_FOUND` to deliberately collide with `INTERNAL_ERROR`'s own URI value;
`ProblemTypesTest.everyConstantIsADistinctAbsoluteUri` failed red as expected. Reverted; full
suite confirmed green again. (The `ArchitectureTest` rule's own mutation-test was already
performed and disclosed at Phase 9.)

## Verification performed this phase

- `mvn -o -pl services/payment -am test-compile`: clean.
- `mvn -o -pl services/payment -am test -Dtest=InvoiceControllerTest,ExportControllerTest,ProblemTypesTest,ResourceServerConfigTest,AccessDeniedHandlerTest`:
  **47/47 passing**.
- One mutation-test (above): confirmed red, reverted, confirmed green.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **458/458 passing** — the
  7 pre-identified failures from Phase 6 are now fixed; everything else remains green.
