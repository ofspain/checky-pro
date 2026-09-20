<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) -->

# crypto · T21 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | crypto-service |
| **Task** | T21 — Attest endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | artifacts/07-self-review.md |
| **Produces** | artifacts/08-independent-review.md |

**Verdict:** The implementation matches the frozen brief and the standing rules in agents.md. The
three named tests are covered, every acceptance criterion has at least one passing test, and the
KmsSignerArchitectureTest regression passes with the new files present. No logic bugs, security
defects, or LOCKED-decision deviations were found. Five findings are noted below: one medium
visibility item (all real attest requests currently refuse), two low code-style issues already
flagged by self-review, and two additional low findings from this independent pass.

A targeted run (`mvn -pl services/crypto test
-Dtest=AttestationServiceTest,AttestControllerTest,AttestationTest,AttestOutcomeTest,AttestationRepositoryIntegrationTest,KmsSignerArchitectureTest,KmsSignerTest,KmsSignerSpringWiringTest`)
passed 53/53. ArchUnit emitted its usual Java 26 bytecode warnings (environment artifact; project
targets Java 21).

---

## 1. Every real `/attest` request currently resolves to `REFUSED`

- **Issue:** The only `ScreeningClient` implementation that exists, `FailClosedScreeningClient`,
  unconditionally returns `ScreeningOutcome.ERROR` for every call. `AttestationService` converts any
  screening `ERROR` into `AttestationRefusedException` (`409`). Therefore, in the current deployed
  system (with no real vendor wired), no request can reach `200 SIGNED` or `200 BLOCKED`. This is
  the correct, intended consequence of composing L12 fail-closed with T19's fail-closed stub, but
  it is easy to mistake for a bug during smoke testing.
- **Severity:** Medium (operational visibility, not correctness)
- **Evidence:** `screening/FailClosedScreeningClient.java:39-49`; `attest/AttestationService.java:85-101`.
- **Recommendation:** No code change. Add a one-line callout in the Phase 13 PR description and any
  runbook noting that `/attest` returns `409` until Q2 is answered and a real screening vendor is
  wired, exactly as T19's own stub documents.
- **Confidence:** High.

## 2. `AttestationRefusedException` is public, unlike the precedent it mirrors

- **Issue:** `watch.WatchNotFoundException` and `watch.InvalidWatchRequestException` are package-
  private, which is the direct precedent this task's exception/handler pair mirrors. `Attestation
  RefusedException` is declared `public`, even though it is only thrown by `AttestationService` and
  caught by `AttestExceptionHandler`, both in the same `attest` package.
- **Severity:** Low
- **Evidence:** `attest/AttestationRefusedException.java:15`; contrast with
  `watch/WatchNotFoundException.java:9` and `watch/InvalidWatchRequestException.java:9`.
- **Recommendation:** Narrow the class to package-private (`class AttestationRefusedException` with
  no `public` modifier) to match the established convention.
- **Confidence:** High.

## 3. The "persist `REFUSED`, then throw" pattern is duplicated

- **Issue:** `AttestationService` contains four near-identical two-line sequences of
  `persist(..., REFUSED, null, null)` immediately followed by `throw new AttestationRefusedException
  (...)`. A future edit to one site (for example, adding context to the persisted row or changing
  the exception message) could update two or three of the four and miss the rest.
- **Severity:** Low
- **Evidence:** `attest/AttestationService.java:81-82, 90-91, 97-99, 114-115`.
- **Recommendation:** Extract a private `refuse(chain, txHash, receiptDigest)` helper that persists
  the `REFUSED` row and throws. This also makes the intent explicit at every call site.
- **Confidence:** High.

## 4. Two `@RestControllerAdvice` beans both claim `HIGHEST_PRECEDENCE`

- **Issue:** `AttestExceptionHandler` and `watch.WatchExceptionHandler` are both annotated with
  `@Order(Ordered.HIGHEST_PRECEDENCE)`. They currently handle disjoint exception types, so no
  collision occurs at runtime. If a future exception type ever overlaps both handlers, Spring's
  resolution order among beans with the same `@Order` value is not explicit and could become
  surprising.
- **Severity:** Low
- **Evidence:** `attest/AttestExceptionHandler.java:15`; `watch/WatchExceptionHandler.java:15`.
- **Recommendation:** Either assign explicit, distinct precedence values (e.g., a module-specific
  ordering within `HIGHEST_PRECEDENCE`) or document that the two advice classes are intentionally
  co-equal because their exception types never overlap. No functional change is required today.
- **Confidence:** Medium.

## 5. `AttestationRepositoryIntegrationTest` does not round-trip the `BLOCKED` outcome

- **Issue:** The integration test exercises `SIGNED` and `REFUSED` round-trips and verifies null
  `kmsKeyId`/`signedAt` for `REFUSED`. `BLOCKED` also has null `kmsKeyId`/`signedAt` but a different
  `outcome` value; its converter/database round-trip is not directly exercised at the repository
  layer.
- **Severity:** Low
- **Evidence:** `attest/AttestationRepositoryIntegrationTest.java:74-96`.
- **Recommendation:** Add a test that saves and reloads an `AttestOutcome.BLOCKED` row, asserting
  the outcome round-trips correctly. This is a one-line addition to existing test infrastructure.
- **Confidence:** High.

---

## Confirmed coverage (no gaps)

- **AC1 / named test `shouldReturnKmsSignatureFromAttestForValidDigest`:** Covered by
  `AttestationServiceTest.shouldReturnKmsSignatureFromAttestForValidDigest`.
- **AC2 / named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty`:** Covered by
  `AttestationServiceTest.shouldReturnBlockedFromAttestOnSanctionedCounterparty` and
  `AttestationServiceTest.blockedIsOnlyReachableAfterQuorumAndFinalityPass`.
- **AC3 / named test `shouldRejectAttestWhenQuorumOrFinalityNotMet`:** Covered by the parameterized
  `AttestationServiceTest.shouldRejectAttestWhenQuorumOrFinalityNotMet`.
- **AC4 (screening ERROR/exception fail-closed):** Covered by `screeningErrorRefusesRatherThanBlocking`
  and `aThrownScreeningExceptionRefusesRatherThanPropagating`.
- **AC5 (one row per outcome, no row on KMS failure):** Covered by multiple tests plus
  `AttestationRepositoryIntegrationTest` and `aKmsFailurePropagatesUncaughtAndPersistsNoAttestationRow`.
- **AC6 (`KmsSignerArchitectureTest` still passes):** Verified by direct test execution.
- **AC7 (module boundary / repositories stay package-private):** Verified by compilation and the
  public seams on `QuorumDecisionService`/`WatchService` only.
- **AC8 (multi-cursor / null fromAddress handling):** Covered by `emptyCursorListRefuses`,
  `allCursorsWithNullFromAddressRefusesRatherThanNpe`, and
  `multipleCursorsWithDistinctFromAddressesAreAllScreenedAndAnyBlockedHitBlocksTheWholeRequest`.
- **AC9 (unsupported chain → 400; blank txHash → 400):** Covered by `AttestControllerTest`.
- **AC10 (no null fields in JSON response):** Covered by `AttestControllerTest.blockedResponseOmits
  SigningFieldsEntirely` and the SIGNED response assertions.
- **AC11 (refusal message never names the failed fact):** Covered by
  `refusalMessageNeverNamesTheSpecificFailedFact`.
- **R27 / internal.crypto:write scope enforcement:** Covered by
  `common.ResourceServerConfigIntegrationTest`.
