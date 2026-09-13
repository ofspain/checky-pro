<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T21 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T21 — Attest endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 21):**
> **Attest endpoint.** Implement `AttestController` + `AttestationService` for `POST /internal/v1/attest`: gate on `AGREED` quorum + met finality + `CLEARED` screening, then sign; `BLOCKED` on sanctioned hit (R21); `409` when quorum/finality unmet (R23); persist every outcome to `attestations` (R20). Structure the sign path to be Nitro-Enclave-portable (no host-only assumptions, L11).

**Verification run:** `mvn -pl services/crypto test -Dtest=AttestOutcomeTest,AttestationTest,AttestationRepositoryIntegrationTest,AttestationServiceTest,AttestControllerTest,QuorumDecisionServiceTest,WatchServiceTest,WatchRepositoryIntegrationTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest` — **107/107 pass**. ArchUnit emitted Java 26 bytecode warnings (environment artifact; project targets Java 21). No regressions.

**Verdict:** The suite maps cleanly to the acceptance criteria and named tests, with strong assertions at the service, controller, repository, and architecture levels. No duplicate tests, no flakiness, and no false positives were found. Four small gaps are noted below; the implementation already handles each correctly, so these are regression-lock additions rather than bug fixes.

---

## Gap 1 — `QuorumDecisionService.isAgreed` does not test the `UNKNOWN_TOKEN` outcome

- **Why it matters:** `QuorumOutcome` has four values: `AGREED`, `HELD`, `UNKNOWN_TOKEN`, and (implicitly) absence. The current `isAgreed` tests cover `AGREED` (true), `HELD` (false), and absent (false). `UNKNOWN_TOKEN` is a legitimate persisted outcome. If a future refactor changed `isAgreed` to return true for any non-`HELD` decision, unknown-token facts would incorrectly gate attestation and signing.
- **Suggested test:** In `QuorumDecisionServiceTest`, add `isAgreedReturnsFalseForAnUnknownTokenDecision`. Stub the repository to return a decision created with `QuorumOutcome.UNKNOWN_TOKEN` and assert the result is `false`.

## Gap 2 — `AttestControllerTest` does not explicitly verify malformed `txHash` → `409 REFUSED` (AC9)

- **Why it matters:** AC9 explicitly documents that an unsupported `chain` → `400` but a malformed-but-non-blank `txHash` → `409 REFUSED`. Today the controller test verifies the `400` cases (`unsupportedChain`, `blankTxHash`, `malformedDigest`) but not the malformed `txHash` path. The service test covers refusal indirectly by stubbing `quorumDecisionService.isAgreed(...)` to false, but it does not exercise the controller routing or the generic refusal message format for this specific failure.
- **Suggested test:** Add `malformedTxHashReturns409Refused` to `AttestControllerTest`. Build a valid request except `txHash = "not-a-hash"` and stub the service to throw `AttestationRefusedException`. Assert `409`, `application/problem+json`, title `"Attestation refused"`, and the generic detail string.

## Gap 3 — `AttestControllerTest` does not verify KMS failure → `500` at the HTTP boundary (L-T21b)

- **Why it matters:** L-T21b requires KMS failures to propagate unconverted (surfacing as `500`). The service test `aKmsFailurePropagatesUncaughtAndPersistsNoAttestationRow` proves the exception is not caught inside the service and that no row is persisted. The controller is the contract boundary, however, and a regression in global exception handling could swallow the exception or map it to a different status.
- **Suggested test:** Add `kmsFailureReturns500` to `AttestControllerTest`. Mock `attestationService.attest(...)` to throw a `RuntimeException` and assert the response status is `500` via the imported `ApiExceptionHandler` (or Spring's default error handling).

## Gap 4 — No test locks the precedence between `AttestExceptionHandler` and `WatchExceptionHandler`

- **Why it matters:** Both advice classes are annotated with `@Order(Ordered.HIGHEST_PRECEDENCE)`. They currently handle disjoint exception types, so there is no collision today. If a future shared exception type is introduced, Spring's resolution among equal-precedence beans is undefined. A test is the only way to make the intended precedence an executable contract.
- **Suggested test:** Add an ArchUnit or reflection test asserting that either (a) the two handlers use distinct, ordered precedence values, or (b) no exception class is assignable to both handlers' `@ExceptionHandler` parameters. If the team prefers to keep them co-equal, document the reason and add a comment in both classes rather than a test.

---

## Confirmed coverage (no additional gaps)

- **Named tests / AC1-AC3:** Fully covered by `AttestationServiceTest`.
- **AC4 (fail-closed screening):** Covered by `screeningErrorRefusesRatherThanBlocking` and `aThrownScreeningExceptionRefusesRatherThanPropagating`.
- **AC5 (persist every outcome; no row on KMS failure):** Covered by service persistence assertions and `AttestationRepositoryIntegrationTest` round-trips.
- **AC6 (KmsSigner architecture):** `KmsSignerArchitectureTest` passes unmodified.
- **AC7 (module boundary):** Verified structurally by compilation and package-private repositories.
- **AC8 (multi-cursor / null fromAddress):** Covered by service tests and `WatchRepositoryIntegrationTest`.
- **AC9 (unsupported chain → 400):** Covered by `AttestControllerTest.unsupportedChainReturnsBadRequest`.
- **AC10 (no null fields in JSON):** Covered by `blockedResponseOmitsSigningFieldsEntirely` and SIGNED response assertions.
- **AC11 (generic refusal message):** Covered by `refusalMessageNeverNamesTheSpecificFailedFact`.
- **R27 (scope enforcement):** Covered by `ResourceServerConfigIntegrationTest`.
