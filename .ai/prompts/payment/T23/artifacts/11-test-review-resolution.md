# payment · T23 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi)

Approved by the human via `AskUserQuestion`, 2026-10-19 ("Approve as proposed").

## Disposition of Kimi's 8 gaps

| # | Gap | Disposition | Reason |
|---|---|---|---|
| 1 | 404 test doesn't assert `$.type` | **ACCEPTED** | Added `$.type` → `ProblemTypes.NOT_FOUND` to `shouldReturnIdentical404ShapeForNonOwningMerchantAndNonexistentInvoiceUuid`. |
| 2 | Malformed path-variable test doesn't assert `$.type` | **ACCEPTED** | Added `$.type` → `ProblemTypes.MALFORMED_REQUEST_PARAMETER` to `shouldReturn400ForMalformedInvoiceUuidPathVariable`. |
| 3 | Export's unauthenticated test doesn't assert `$.type` | **ACCEPTED** | Added `$.type`/`$.title` assertions to `ExportControllerTest.shouldRejectUnauthenticatedCallerWithUnauthorized`. |
| 4 | Named R29 test only exercises `VALIDATION_ERROR` | **ACCEPTED** | Added `shouldReturnProblemJsonWithNoInternalDetailOn404`, factored the shared leak-check logic into a private `assertNoInternalDetailLeak` helper both tests now call. |
| 5 | No test exercises `MALFORMED_REQUEST` | **ACCEPTED** | New `shouldReturn400ForMalformedRequestBody` (truncated JSON POST body). |
| 6 | No test exercises `INTERNAL_ERROR`/the 500 no-leak rule | **ACCEPTED** | New `shouldReturn500WithNoInternalDetailForUnexpectedFailure` — stubs a raw `RuntimeException` carrying a deliberately sensitive-looking message, asserts the response doesn't contain it, the exception class name, or a `.java:` stack-frame fragment. |
| 7 | No test counts exact call-site usages per constant | **REJECTED** | A design-smell concern (a future accidental third handler reusing `VALIDATION_ERROR`), not a functional/correctness bug — the counting mechanism itself (reflection or ArchUnit call-graph analysis) would be disproportionately complex and brittle to build reliably for that marginal protection. |
| 8 | `shouldNotInlineProblemTypeUris` only bans `URI.create`, not `new URI(String)` | **ACCEPTED, narrowed from Kimi's own broader ask** | `java.net.URI` has a public constructor with an identical effect the original rule didn't cover — a real, demonstrated bypass. Extended the rule with `.orShould().callConstructor(URI.class, String.class)`. Kimi's broader suggestion (ban any `ProblemDetail.setType(URI)` call whose argument isn't literally a `ProblemTypes` field reference) would require ArchUnit to trace argument data-flow, which its declarative API isn't built for — not pursued as disproportionate to the realistic risk the narrower fix already closes. |

## Verification performed this phase

- `mvn -o -pl services/payment -am test-compile`: clean.
- `mvn -o -pl services/payment -am test -Dtest=InvoiceControllerTest,ExportControllerTest,ArchitectureTest`:
  **51/51 passing**.
- **Mutation-tested the ArchUnit hardening specifically**, not just the fixture as a whole:
  temporarily removed `inlineUriViaCreate()` from `RogueInlineProblemTypeUser`, leaving only the
  constructor-based violation — `shouldNotInlineProblemTypeUrisActuallyFailsAgainstAGenuineViolation`
  still correctly failed the rule, proving the new `orShould().callConstructor(...)` branch is
  independently load-bearing, not redundant with the existing `create` branch. Restored the
  fixture to both methods; re-confirmed green.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **461/461 passing**
  (458 prior + 3 new test methods).
