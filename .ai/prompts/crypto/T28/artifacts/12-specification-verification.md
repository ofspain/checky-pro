# crypto · T28 · Phase 12 — Specification Verification

## Acceptance Criteria (Phase 1)

### AC1a. Unit + ArchUnit tests confirmed passing for every test cited across rows #1–#6

**PASS.** Every test cited in `SECURITY-THREAT-MODEL.md` rows #1–#6 passes: `QuorumEvaluatorTest`,
`WatcherTest` (all `<3`-provider cases, now including the single-provider case and observation-logging
assertions), `TokenValidatorTest`, `EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`,
`KmsSignerArchitectureTest`, `ObservationLogTest`, `AddressPoisoningDetectorTest`. Re-confirmed directly
this phase via a fresh full-module run, not carried over from memory.

### AC1b. Full `mvn -pl services/crypto -am verify` result recorded honestly

**Recorded honestly — mixed result, most of it outside T28's own scope.** Docker became available in
this environment mid-Phase-11, for the first time this entire session (T23 onward). Final result: 759
tests, 6 failures, 4 errors. Broken down:

- **Directly relevant to a threat-model row:** `EndToEndIntegrationTest` (4 errors) — row #3's own
  cited integration-level test, now confirmed to genuinely fail (`SchemaManagementException: missing
  table [chain.attestations]`), not merely "not yet executed." The citation was corrected to describe
  this accurately (Phase 11).
- **Not cited by any threat-model row, discovered incidentally:** `TokenAllowlistRepositoryIntegrationTest`
  (2 failures), `ProviderHealthRepositoryIntegrationTest` (1 failure), `QuorumDecisionRepositoryIntegrationTest`
  (1 failure), `ObservationRepositoryIntegrationTest` (2 failures) — pre-existing defects from earlier
  tasks (T08–T21-ish, by module), surfaced only because this is the first time Docker has ever been
  available to run them in this session. Not investigated or fixed — well outside a "threat-model
  closure" task's own scope.
- **Genuinely improved by this task's own citations:** row #5's `ChainBaselineMigrationIntegrationTest`
  now confirmed passing (10/10), removing its own former "not yet executed" caveat.

### AC2. `KmsSignerArchitectureTest`'s two rules confirmed passing

**PASS.** `shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild` passes, closing the
task statement's own standalone "no non-attest path can reach `kms:Sign`" check (code-path half; the
IAM/runtime half remains explicitly disclosed as infrastructure-owned, not testable here).

### AC3. `WatcherTest`'s `<3`-provider tests confirmed passing, now with `verifyNoInteractions(txLifecyclePublisher)`

**PASS**, and strengthened twice beyond the original plan: the single-provider case (Phase 9) and the
observation-log-still-records assertions (Phase 11) both closed real Kimi findings, closing the task
statement's own standalone "no single-provider fact is ever emitted" check to a stronger degree than
Phase 5 originally planned.

### AC4. `SECURITY-THREAT-MODEL.md` rows #1–#6 show `Status: closed`, correctly annotated

**PASS.** All six rows read `closed`, each citing real, currently-passing (or, for row #3, currently and
accurately disclosed as failing) named tests. Row #5's annotation correctly narrows to crypto-service's
own portion. Row #3's annotation now correctly describes a genuine, confirmed defect rather than a mere
execution gap. Rows #7–#8 and every other column verified untouched via direct re-read.

## Verdict

**PASS.** All four Phase 1 acceptance criteria satisfied. AC1b surfaced significant new information
(Docker's availability, one real defect directly relevant to a threat-model citation, several more
unrelated to any citation) — handled by correcting citations to be accurate rather than by expanding
this task's own scope to fix pre-existing, unrelated defects.

## Candidates for future tasks (not fixed here, out of T28's own scope)

- **`EndToEndIntegrationTest`'s Spring-context/Flyway-migration failure** (`chain.attestations` table
  not found) — a real, previously-undiscoverable-until-now defect in T26's own test infrastructure.
  Directly affects row #3's integration-level proof. Highest-priority candidate, since it's the one
  failure this task's own citations actually depend on.
- **`TokenAllowlistRepositoryIntegrationTest`, `ProviderHealthRepositoryIntegrationTest`,
  `QuorumDecisionRepositoryIntegrationTest`, `ObservationRepositoryIntegrationTest`** — 6 further
  failures across 4 classes, none cited by any threat-model row, surfaced incidentally by Docker's own
  availability. Not investigated; a real gap in this session's own historical "Docker unavailable, can't
  verify" disclosures across T08–T21-ish, now that it's finally checkable.
- Carried forward from T27/T25/T26: whatever external process was producing the recurring
  wrong-codebase merges/force-pushes needs its local clone re-synced — unconfirmed whether this remains
  an active risk, but not re-tested this task.
- Kimi Phase 11 Findings #4/#5 (classpath-validation tooling for citations; a CI/IAM smoke test for the
  runtime `kms:Sign` property) — both explicitly out of scope, recorded here as candidates only.
