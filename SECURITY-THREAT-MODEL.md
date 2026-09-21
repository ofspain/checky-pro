# Themistra — Security Threat Model

> Status: **updated for crypto-service T28 — rows #1–#6 verified and closed (2026-09-21).**
> See ARCHITECTURE.md §6.7. Every feature PR either updates this document or states why no update is needed.

## Threats to enumerate (minimum set)

| # | Threat | Mitigation (ARCHITECTURE.md ref) | Status | Implementing task |
|---|---|---|---|---|
| 1 | Attacker controls one RPC provider | Multi-provider 2-of-3 quorum (§6.1) | closed — `QuorumEvaluatorTest.shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree`; `WatcherTest.doesNotEvaluateWithOnlyOneOfThreeProvidersAnswering` / `.doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering` / `.laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers` | crypto T09 (quorum evaluator) |
| 2 | Attacker deploys fake USDT contract | Contract-address allowlist (§6.3) | closed — `TokenValidatorTest.shouldIdentifyTokenByContractAddressNotSymbol` / `.shouldSurfaceUnknownTokenForNonAllowlistedContract` | crypto T11 (token allowlist) |
| 3 | Chain reorg after user sees "confirmed" | Per-chain finality policy, reorg-aware state machine (§6.2) | closed (unit-level) — `EthereumFinalityPolicyTest.shouldRequireBeaconFinalizedCheckpointForEthereumFinality`, `TronFinalityPolicyTest.shouldRequireSolidifiedBlockForTronFinality`, `WatcherTest.shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg`; integration-level proof (`EndToEndIntegrationTest.reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor`) attempted with Docker now available in this environment (T28 Phase 11) but currently fails on Spring context startup (`SchemaManagementException: missing table [chain.attestations]`) — a pre-existing `EndToEndIntegrationTest` infrastructure defect unrelated to T28, flagged as a new follow-up task, not fixed here | crypto T14 (finality policies) / T18 (reorg detector) |
| 4 | Stolen application server credentials | Keys unexfiltratable — KMS-only signing (§6.4) | closed (code-path only) — `KmsSignerArchitectureTest.shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild` (R22/L11); IAM/runtime half (EKS IAM roles, KMS key policy) is an infrastructure control, not tested here | crypto T20 (KMS signer) |
| 5 | Insider alters a historical verification | Hash-chain ledger + S3 Object Lock + on-chain anchor (§6.5) | closed (crypto-service portion) — `ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert`; append-only enforced by DB grants and verified by `ChainBaselineMigrationIntegrationTest.cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables` (confirmed passing — Docker became available in this environment during T28 Phase 11, 10/10 tests green) against `V2__crypto_app_role_and_grants.sql`'s `crypto_app` INSERT+SELECT-only grant on `chain.observations`; S3 Object Lock/WORM is an infrastructure control, not Java-testable; hash-chain ledger + on-chain anchor are Payment-Service-owned (`ARCHITECTURE.md` §6.5), out of this service's scope | crypto T08 (observation log) / T09 (quorum decision persistence) |
| 6 | Address-poisoning of a repeat customer | Prefix/suffix similarity flagging (§6.3) | closed — `AddressPoisoningDetectorTest.shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` | crypto T13 (address-poisoning detector) |
| 7 | Merchant webhook spoofing | HMAC-signed webhook payloads (§3.5) | designed | — |
| 8 | Account takeover of a merchant | MFA from day one (§3.2) | designed | — |

Threats #1–#6 are owned by `crypto-service` (`spec/crypto-service/tasks.md`); `closed` means the row's
Implementing task has landed and a named, currently-passing test (cited in the Status column) verifies
its mitigation — crypto-service T28 verified all six directly against the real test suite, not assumed.
Two different kinds of caveat appear in individual Status cells, not interchangeably: row #3's is a
**pre-existing defect** — its integration-level proof was attempted once Docker became available in
this environment (T28 Phase 11) and genuinely fails on Spring context startup, a real
`EndToEndIntegrationTest` infrastructure bug unrelated to T28 and out of this task's own scope to fix;
row #4's is an **ownership split** — part of the mitigation is infrastructure-controlled (IAM) rather
than crypto-service-owned. Row #5 was itself a mix of both until Docker became available: its DB-grant
claim is now fully verified (`ChainBaselineMigrationIntegrationTest`, 10/10 passing), while its S3
Object Lock/WORM guarantee remains infrastructure-controlled and its hash-chain ledger/on-chain anchor
remain owned by the Payment Service (not yet built) — `closed` for row #5 certifies only the
crypto-service-owned, now-fully-tested portion. Threats #7–#8 are auth-service/payments concerns, out
of this table's
crypto-service update scope.
