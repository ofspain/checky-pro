# crypto · T21 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R20 — a valid attest request signs via KMS and returns `{signature, kmsKeyId, signedAt, outcome: "SIGNED"}`; every outcome persisted to `attestations` | Yes | `AttestationService.attest` `attest/AttestationService.java:72-106`; `persist` `:137-141` | `AttestationServiceTest.shouldReturnKmsSignatureFromAttestForValidDigest` (named test) + persisted-row assertions; `AttestationRepositoryIntegrationTest` (all 3 outcomes) | No | None. |
| R21 — a sanctioned counterparty returns `{outcome: "BLOCKED", reason}`, no signature | Yes | `attest/AttestationService.java:92-95` | `AttestationServiceTest.shouldReturnBlockedFromAttestOnSanctionedCounterparty` (named test), `.multipleCursorsWithDistinctFromAddressesAreAllScreenedAndAnyBlockedHitBlocksTheWholeRequest` | No | Compliance-queue mechanism deferred (Open Question, human-approved at Phase 4). |
| R23 — an attest request for a transaction that hasn't met quorum and finality refuses and returns an error, never a signature | Yes | `requireAllFactsAgreed` `attest/AttestationService.java:108-114` | `AttestationServiceTest.shouldRejectAttestWhenQuorumOrFinalityNotMet` (named test, parameterized ×4) | No | None. |
| L10 — `/attest` refuses to sign unless quorum + finality (+ screening) passed; screening only reachable after quorum+finality | Yes | Gate order itself: `attest/AttestationService.java:77` (facts) before `:79-99` (cursors/screening) before `:101-105` (sign) | `AttestationServiceTest.blockedIsOnlyReachableAfterQuorumAndFinalityPass` | No | None — this is the Phase 4 Finding #1 correction, verified. |
| L11 — KMS-only signing stays single-path | Yes | `AttestationService` calls only `KmsSigner.sign(...)`, no KMS SDK import | `KmsSignerArchitectureTest` (re-run, unmodified, still passes with `attest`'s new files present) | No | None. |
| L12 — screening gates attestation, fail-closed | Yes | `attest/AttestationService.java:87-98` (`ERROR`/thrown exception → `refuse`) | `AttestationServiceTest.screeningErrorRefusesRatherThanBlocking`, `.aThrownScreeningExceptionRefusesRatherThanPropagating` | No | None. |
| L-T21a (new, Phase 4) — the counterparty is `fromAddress`, never `toAddress`; multiple cursors screened, any hit blocks | Yes | `distinctFromAddresses` `attest/AttestationService.java:127-135` | `AttestationServiceTest.multipleCursorsWithDistinctFromAddressesAreAllScreenedAndAnyBlockedHitBlocksTheWholeRequest`, `.emptyCursorListRefuses`, `.allCursorsWithNullFromAddressRefusesRatherThanNpe`; `WatchRepositoryIntegrationTest.findChainCursorsReturnsEveryCursorSharingTheSameChainAndTxHash` (real multi-row DB proof) | No | None. |
| L-T21b (new, Phase 4) — a KMS/infra failure is a `500`, never converted to `REFUSED` | Yes | `attest/AttestationService.java:101-105` (no try/catch around `kmsSigner.sign`) | `AttestationServiceTest.aKmsFailurePropagatesUncaughtAndPersistsNoAttestationRow`; `AttestControllerTest.kmsFailurePropagatesToAGenericFiveHundredNotSwallowedOrRemapped` (HTTP-boundary proof, Phase 11) | No | None. |
| Named test `shouldReturnKmsSignatureFromAttestForValidDigest` | Yes | — | `AttestationServiceTest.shouldReturnKmsSignatureFromAttestForValidDigest` | No | None. |
| Named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty` | Yes | — | `AttestationServiceTest.shouldReturnBlockedFromAttestOnSanctionedCounterparty` | No | First full, end-to-end ownership of this test (T19 only proved the fail-closed floor it depends on). |
| Named test `shouldRejectAttestWhenQuorumOrFinalityNotMet` | Yes | — | `AttestationServiceTest.shouldRejectAttestWhenQuorumOrFinalityNotMet` (parameterized) | No | None. |
| R27 (unmodified) — `internal.crypto:write` scope required | Yes | Already covered by `ResourceServerConfig`'s `/internal/v1/**` path rule; no change made | `ResourceServerConfigIntegrationTest` (already parameterized over `/internal/v1/attest`) | No | None. |
| Frozen brief AC9 — unsupported `chain` → `400`; malformed-but-non-blank `txHash` → `409` | Yes | `AttestRequest`'s `@Pattern` `attest/AttestRequest.java` | `AttestControllerTest.unsupportedChainReturnsBadRequest`, `.malformedNonBlankTxHashReturns409RefusedNotBadRequest` (Phase 11) | No | None. |
| Frozen brief AC10 — `AttestResponse` JSON never includes a `null` field | Yes | `@JsonInclude(NON_NULL)` `attest/AttestResponse.java` | `AttestControllerTest.blockedResponseOmitsSigningFieldsEntirely` | No | None. |
| Frozen brief AC11 — refusal message never names the specific failed fact | Yes | `AttestationRefusedException`'s fixed, generic message format | `AttestationServiceTest.refusalMessageNeverNamesTheSpecificFailedFact` | No | None. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file the frozen brief (Phase 4) authorized exists and is
wired in: `AttestOutcome`, `Attestation`, `AttestationRepository`, `AttestRequest`, `AttestResponse`,
`AttestationRefusedException`, `AttestExceptionHandler`, `AttestationService`, `AttestController`, the
`V10` index migration, and the two new cross-module seams (`QuorumDecisionService.isAgreed`,
`WatchService.findChainCursors`/`ChainCursorRepository.findByChainAndTxHash`). Phase 3's 15 findings
(14 accepted, 1 rejected), Phase 9's 5 findings (4 accepted, 1 rejected), and Phase 11's 4 gaps (3
accepted, 1 rejected) are all resolved with cited, verified reasoning — no unexplained rejections, and
two rejections (Phase 3 Finding #12, Phase 8/9/11 Finding #4) were each raised and independently
re-affirmed rejected on their own merits at a later phase, not merely carried forward by inertia.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC11 (frozen brief) all have
implementation evidence and test coverage, now including HTTP-boundary proofs (Phase 11) for the two
criteria (AC9's malformed-`txHash` case, L-T21b's KMS-failure case) that previously only had service-
layer coverage.

**(3) Does it violate any LOCKED decision?** No. L10's screening-only-after-quorum-and-finality ordering
is enforced by the method body's own literal statement order, not a runtime flag (`AttestationService
.attest`'s three phases run strictly sequentially, with `refuse(...)` short-circuiting before the next
phase ever begins). L11 is enforced structurally by the pre-existing, negative-proof-tested
`KmsSignerArchitectureTest`, which still passes with `attest`'s tripled file count. L12's fail-closed
contract is honored for every `ScreeningClient` outcome and for a thrown exception alike. The two new
task-scoped Locked Decisions (L-T21a, L-T21b) are both implemented and independently tested at both the
service and controller layers. No frozen file from a prior task (`V1`-`V9` migrations, `screening/`,
`attest/KmsSigner.java`/`SignatureResult.java`/`KmsSignerArchitectureTest.java`, `watch/Watcher.java`,
`quorum/QuorumDecisionService.evaluate`) was modified beyond the two explicitly-authorized new methods.

**(4) Remaining risks?**
- **R21's "compliance queue" is not built** — disclosed, human-approved Open Question at Phase 4; the
  persisted `Attestation(BLOCKED)` + `ScreeningResult(BLOCKED)` rows are the interim audit trail.
- **No idempotency guarantee on `/attest`** — disclosed, human-approved Open Question at Phase 4; an
  identical repeated request signs and persists again.
- **Every real `/attest` request currently resolves to `409 REFUSED`** — not a defect in this task, but
  a direct, verified consequence of composing this task's own fail-closed screening gate with T19's
  `FailClosedScreeningClient` (which unconditionally returns `ERROR` until a real vendor is wired, Q2
  unanswered). Disclosed at Phase 7/8, carried into the Phase 13 PR description as an explicit callout so
  it is not mistaken for a bug during smoke testing or an operational incident.
- **The quorum-decision/cursor-snapshot visibility race** (Phase 3 Finding #5) remains an accepted,
  documented best-effort limitation — a client seeing an immediate `409` right after finality is reached
  should retry, per T17's own separate-transaction design that this task does not revisit.
- **`AttestExceptionHandler`/`WatchExceptionHandler`'s shared `@Order(HIGHEST_PRECEDENCE)`** remains
  unresolved by design (rejected three times across Phase 8, 9, and 11) — genuinely low risk today since
  the two handlers cover disjoint exception types, revisit only if that ever changes.

## Verdict

**PASS** — every in-scope requirement, acceptance criterion, and LOCKED decision (including the two new
ones this task itself introduced) is implemented, tested at both the service and HTTP-boundary layers,
and traced to evidence; all three review phases' findings are fully resolved with human-approved,
precedent-grounded or directly-verified dispositions; the full module regression (704 tests) shows zero
regressions and only the same 6 pre-existing, disclosed, unrelated failing tests.
