STATUS: FROZEN

# crypto · T28 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings independently verified directly against real source before disposition.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Row #5's S3 Object Lock/WORM isn't Java-unit-testable | **ACCEPTED** | Verified: `ObservationLogTest`'s own Javadoc states `ObservationSnapshotStore`/`ObservationRepository` are Mockito mocks, no real S3/Postgres. Row #5's closure note will scope crypto-service's tested coverage to "S3 write attempted before the Postgres insert" (`recordAttemptsTheS3WriteBeforeThePostgresInsert`) and disclose Object Lock/WORM/legal-hold as an infrastructure/deployment control, not asserted by any Java test. |
| 2 | Row #4's IAM/runtime key-protection isn't tested | **ACCEPTED** | `KmsSignerArchitectureTest` is static/ArchUnit analysis only. Row #4's closure note splits the mitigation: code-path (closed, cites `KmsSignerArchitectureTest`) vs. IAM/runtime (not closed by this test suite, infrastructure-owned). |
| 3 | "No single-provider fact" lacks a direct assertion on `txLifecyclePublisher` | **ACCEPTED, narrow fix** | Traced the code: `txLifecyclePublisher.seen(...)`/`.confirmed(...)` are only reachable from methods gated on an already-AGREED `QuorumDecision` — the existing `verifyNoInteractions(quorumDecisionService)` in `WatcherTest.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` and `.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` already transitively proves this. Adding a direct `verifyNoInteractions(txLifecyclePublisher)` to both (not a new test) makes it explicit rather than implicit. |
| 4 | Full `mvn verify` can't pass in this environment (14 Docker errors) | **ACCEPTED** | AC1 split explicitly: (a) unit + ArchUnit tests — closeable now, in this environment; (b) full `verify` including Testcontainers-based `*IT`/integration tests — requires re-running in a Docker-available CI environment before task 29 bumps the spec. Not silently claimed passing. |
| 5 | Row #3's reorg-after-confirmed isn't proven end-to-end here | **ACCEPTED** | Row #3's closure note cites the unit tests (`EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`, `ReorgDetectorTest`) as partial coverage, and explicitly names `EndToEndIntegrationTest.reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor` as the intended integration-level proof, disclosed as not yet executed in this environment. |
| 6 | `AddressValidatorTest` incorrectly cited under Threat #2 | **ACCEPTED — real Phase 1 error, confirmed** | `AddressValidatorTest` tests R15/R16 (EIP-55/Base58 checksum validity, L8) — a distinct concern from Threat #2's R13/R14 (contract-address-only token identity, L7). Removed from row #2's citation. |
| 7 | Observation log's append-only property isn't tested at the repository layer | **ACCEPTED, disclose not fix** | Verified directly: `V2__crypto_app_role_and_grants.sql:34` grants `crypto_app` only `INSERT, SELECT` on `chain.observations` (and `attestations`, `quorum_decisions`) — no `UPDATE`/`DELETE` at all. Row #5's closure note discloses this is enforced by DB grants (verified via migration DDL) rather than a Java test; `ChainBaselineMigrationIntegrationTest` is the test that would verify the grants themselves, itself part of the Docker-blocked set. |
| 8 | Document's "stub" header contradicts completed code | **ACCEPTED** | Header text updated alongside the `Status` column changes (Phase 6). |

## Open Questions — resolved

1. Should row #5 be marked `closed` given its S3 Object Lock/WORM guarantee isn't unit-testable? — **Resolved**: `closed` for crypto-service's own tested portion (verbatim + ordering), with the Object Lock/WORM/legal-hold and Payment-Service-owned hash-chain/anchor portions explicitly annotated as infrastructure/cross-service, not silently absorbed into "closed."
2. Should AC1 accept `mvn test` as sufficient here? — **Resolved**: yes, for the unit+ArchUnit portion, with a hard, explicit note that full `verify` (Testcontainers) must be re-run in CI before task 29.
3. Does `AddressValidatorTest` exist, and how should it be cited? — **Resolved**: exists, tests R15/R16, not cited under any of the six threat rows (removed from row #2).

## Task

Unchanged from Phase 2: verify each `SECURITY-THREAT-MODEL.md` row (#1–#6), confirm the two standalone
checks (no non-attest path to `kms:Sign`; no single-provider fact emitted), update the document's
`Status` column and header wording.

## Scope

**In (unchanged from Phase 2, plus the above):**
- The one narrow test strengthening from Finding #3 (two `verifyNoInteractions(txLifecyclePublisher)`
  lines added to two already-existing `WatcherTest` methods — not a new test file).
- `SECURITY-THREAT-MODEL.md`: `Status` column (rows #1–#6) + header wording update.

**Out:** Unchanged from Phase 2, plus: no new IAM/CI/IaC tooling (Findings #1/#2's infra-owned halves
are disclosed, not built here).

## Acceptance Criteria

1. **AC1a.** Unit + ArchUnit tests (`mvn -pl services/crypto -am test -Dtest='!*IntegrationTest,!*IT'` or
   equivalent scoping) confirmed passing in this environment for every test cited across rows #1–#6.
2. **AC1b.** Full `mvn -pl services/crypto -am verify` result recorded honestly (same 14 disclosed
   Docker-only errors expected), with an explicit note that this portion needs Docker-available CI
   before task 29.
3. **AC2.** `KmsSignerArchitectureTest`'s two rules confirmed passing (R22/L11, code-path half of Threat
   #4 and the task's own standalone `kms:Sign` check).
4. **AC3.** `WatcherTest`'s two `<3`-provider tests confirmed passing, now with the added
   `verifyNoInteractions(txLifecyclePublisher)` assertions.
5. **AC4.** `SECURITY-THREAT-MODEL.md` rows #1–#6 show `Status: closed` (row #5 annotated per Finding
   #1/#7's disclosure), header wording updated, no change to rows #7–#8 or the mitigation/threat text.

## Required Tests

None new, except the two-line strengthening to existing `WatcherTest` methods (Finding #3).

## Constraints

Unchanged from Phase 2, plus: the `WatcherTest` change is additive-only (two `verify` lines), no
restructuring of the existing test methods.

## Open Questions

No blockers.
