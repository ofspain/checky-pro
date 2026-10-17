# payment · T22 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi)

Approved by the human via `AskUserQuestion`, 2026-10-17 ("Approve as proposed").

## Disposition of Kimi's 8 gaps

| # | Gap | Disposition | Reason |
|---|---|---|---|
| 1 | Empty CSV (header-only) not tested | **ACCEPTED** | New `shouldReturnCsvHeaderOnlyWhenNoMatchingRows` (Controller). |
| 2 | Malformed (non-UUID) `sub` claim not tested | **ACCEPTED** | New `shouldReturn400ForMalformedSubClaim` (Controller), mirrors `InvoiceControllerTest`'s identical precedent. |
| 3 | `from == to` single-instant boundary not tested | **ACCEPTED** | New `shouldIncludePaymentWhenFromEqualsTo` (Integration). |
| 4 | Tie-break ordering when `finalizedAt` is equal not tested | **ACCEPTED, with a real weakness found and reported honestly — see note below** | New `shouldTieBreakByPaymentUuidWhenFinalizedAtIsEqual` (Integration). |
| 5 | Malformed `to` not tested (only `from`) | **ACCEPTED** | New `shouldReturn400ForMalformedToValue` (Controller). |
| 6 | CSV structural integrity only spot-checked via substring | **ACCEPTED** | `shouldEscapeCsvFieldsContainingSpecialCharacters` now parses the body with a minimal test-only RFC 4180 parser and asserts exactly 12 columns and an exact round-trip of the original unescaped value, not just that the escaped substring appears somewhere. |
| 7 | No full-stack (real DB → controller → CSV) test | **REJECTED** | The two existing test layers — a real-Postgres query-correctness suite and a stubbed-row controller-rendering suite — already independently exercise every actual code path this would re-exercise. Building the combined real-DB + MockMvc + security harness this gap implies would be new, heavier test infrastructure with no precedent anywhere in this codebase, for coverage that is already indirectly guaranteed by `TransactionExportRow`'s own type contract between the two layers. |
| 8 | `Content-Disposition` filename only loosely checked | **ACCEPTED, with Kimi's own suggested fix corrected** | Kimi's suggested exact-match string assumed the raw ISO instant form (`...2026-01-01T00:00:00Z-...`); the real `ExportController` deliberately uses `Instant.getEpochSecond()`, not `Instant.toString()`, specifically to avoid the colons an ISO instant contains, which Windows forbids in filenames (see the controller's own Phase 9 comment). The corrected test asserts the exact header using the two test instants' real epoch-second values. |

## A real test weakness found and disclosed while applying Gap 4 — not papered over

The first version of the tie-break test (mutation-tested by removing the `payment_uuid` tie-break
entirely from the `ORDER BY`) **still passed** — Postgres's own unspecified ordering for two rows
with an identical `finalized_at` happened to coincide with insertion order for this tiny seeded
table, so "no tie-break clause at all" was not reliably caught. Re-tested by reversing the
direction instead (`payment_uuid DESC`), which failed red deterministically (confirmed, not
assumed, by running it). Both mutations were reverted after confirming the relevant one failed.

**Disclosed, not hidden:** this test's real, confirmed guarantee is narrower than its name alone
suggests — it reliably catches the tie-break column being wrong or reversed, but does not
reliably catch the tie-break clause being dropped entirely, since Postgres's behavior in that
case is implementation-defined and happened to coincide with the correct order in this
environment. This is a known limitation of testing "undefined order" negatively; strengthening it
further (e.g., a much larger seeded set to make coincidental agreement statistically unlikely)
was judged disproportionate to the risk for this task and not pursued.

## Verification performed this phase

- `mvn -o -pl services/payment -am test-compile`: clean (one missing static import for
  `org.assertj.core.api.Assertions.assertThat` found and fixed along the way — the file had never
  needed the unqualified form before).
- `mvn -o -pl services/payment -am test -Dtest=ExportControllerTest,TransactionExportServiceIntegrationTest`:
  **31/31 passing** (21 + 10, up from 18 + 8).
- Two mutation-tests this phase: the tie-break reversal (confirmed red, reverted — see above) and
  an earlier no-tie-break-at-all attempt (confirmed it does NOT reliably fail, disclosed above
  rather than discarded silently).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **451/451 passing**
  (446 prior + 5 new).
