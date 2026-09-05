# 7. Tasks — ordered execution plan

Execute in order. Each task leaves the module buildable and the test suite green. All tests use scripted fake `ChainAdapter`s — real providers are never called in CI. **`SECURITY-THREAT-MODEL.md` must be completed/updated before Task 1** (`ARCHITECTURE.md` §6.7).

## Foundation

1. **Threat-model + skeleton & POM.** Update `SECURITY-THREAT-MODEL.md` for threats #1–#6. Add `services/crypto` to the root `<modules>`. Create `pom.xml` mirroring `services/auth` plus web3j (EVM), the Tron gRPC client, and the AWS KMS SDK (**KMS only** — scoped to the attest module). Enable Java 21 virtual threads.
2. **Schema V1.** Add `V1__chain_baseline.sql` (design §4c); run `mvn -pl services/crypto flyway:migrate` against local Docker Compose Postgres. Grant the service DB role INSERT+SELECT-only on `observations`, `attestations`, `quorum_decisions`.
3. **Config & resource server.** Validated `@ConfigurationProperties` for providers, finality, screening, KMS, and S3 snapshot keys (design §4c). Wire service-to-service JWT validation requiring `internal.crypto:write` on internal endpoints (R27); `PublicEndpoints` allows only actuator + the verification-keys well-known path.
4. **Outbox & EventTopics.** Add `OutboxPublisher` with the deterministic idempotency key (L5) and `EventTopics` (design §4c). Unit-test routing (R26).

## Adapters, providers, quorum

5. **Adapter interface + fakes.** Define `ChainAdapter` (design §4c) and `Chain`. Build a scripted `FakeChainAdapter` for tests that can agree, disagree, lag, and reorg.
6. **Ethereum adapter.** Implement `EthereumAdapter` (web3j): `getTx`, `getTokenInfo`, `subscribeAddress`, `getFinalityStatus`. Provider credentials via config (O1/Q1).
7. **Tron adapter.** Implement `TronAdapter` (TronGrid / java-tron gRPC) against the same interface.
8. **Observation log first.** Implement `Observation` (append-only) + `ObservationSnapshotStore` (S3). Every provider response is persisted verbatim **before** any quorum decision (L3, R4). Test ordering.
9. **Quorum evaluator.** Implement `QuorumEvaluator` (pure 2-of-3 logic): `AGREED` needs ≥2 matching; disagreement → `HELD` + `HeldFactAlerter` + persisted `QuorumDecision`; never auto-resolve (L1, L2, R1–R3). Unit-test the agreement matrix exhaustively.
10. **Provider health + degraded.** Track `ProviderHealth`; emit `chain.provider.degraded` when a provider is unhealthy or repeatedly disagrees, continuing if quorum is still achievable (R5).

## Token, address, finality

11. **Token allowlist + validator.** Seed the signed, versioned allowlist (per-chain official USDT/USDC contracts) via migration/config. Implement `TokenValidator` — identity by `<chain, contractAddress>` only; non-allowlisted → `UNKNOWN_TOKEN` surfaced loudly (L7, R13/R14).
12. **Address validation.** Implement `AddressValidator` (EIP-55 for EVM, Base58Check for Tron) at the boundary (L8, R15/R16).
13. **Address-poisoning detector.** Implement prefix/suffix similarity flagging against previously seen counterparties for the same watch (L9, R17). Propagate the flag onto observations/events.
14. **Finality policies.** Implement `EthereumFinalityPolicy` (beacon `finalized` checkpoint) and `TronFinalityPolicy` (solidified block), both behind `FinalityPolicy` (L4, R6/R7). Unit-test each against scripted chain heads. Confirm Tron confirmation-count basis (Q4).

## Watchers, reorg, events

15. **Watch registration API.** Implement `WatchService` + `WatchController` for `POST/DELETE /internal/v1/watches` (R18/R19). Persist `Watch` and its `ChainCursor`.
16. **Watcher layer.** Implement `Watcher` on virtual threads driving subscriptions/polling per provider (O2/Q5), feeding observations into the quorum pipeline. Implement `WatcherRegistry` for multi-replica assignment (O5 — author approval required before finalizing).
17. **Seen/confirmed/finalized emission.** On quorum-agreed first sighting emit `chain.tx.seen` (R8); on confirmations gained emit `chain.tx.confirmed` with the count (R9); at finality emit `chain.tx.finalized` and never before (R10). Every event carries `chain:txhash:eventtype` (L5, R12).
18. **Reorg detector.** Implement `ReorgDetector`: on an invalidating reorg, walk the cursor backward and emit `chain.tx.reorged` (L6, R11). Test a scripted reorg after `seen` and after `confirmed`.

## Screening, attestation, key custody

19. **Screening client.** Add `ScreeningClient` interface with a **fail-closed** stub and `ScreeningResult` persistence (L12, R21). Wire the real vendor once Q2 is answered.
20. **KMS signer — single path.** Implement `KmsSigner` as the **sole** `kms:Sign` caller (L11, R22). Add the ArchUnit rule that no package outside `attest` may reference `KmsSigner` or the KMS SDK signing API.
21. **Attest endpoint.** Implement `AttestController` + `AttestationService` for `POST /internal/v1/attest`: gate on `AGREED` quorum + met finality + `CLEARED` screening, then sign; `BLOCKED` on sanctioned hit (R21); `409` when quorum/finality unmet (R23); persist every outcome to `attestations` (R20). Structure the sign path to be Nitro-Enclave-portable (no host-only assumptions, L11).
22. **Verification keys endpoint.** Implement `VerificationKeysController` publishing the public keys at the well-known URL (R24; alg per Q7).

## Contracts, sidecar contract, hardening

23. **Contracts.** Author `contracts/api/crypto-internal.yaml` and the five `contracts/events/chain/*.v1.schema.json` (amounts as decimal strings). Add contract tests mirroring the auth `UserLifecycleEventPayloadContractTest` pattern (R28).
24. **Sidecar-as-provider test.** Add a fake sidecar observation source and assert it is treated as just another provider answer subject to quorum, with no signing/state access (L14, R25).
25. **ArchUnit/module boundaries.** Enforce no cross-module entity imports (L15), the KMS-signer package ban (L11/R22), and the internal-scope requirement on watch/attest endpoints (R27).

## Final verification

26. **End-to-end integration test.** Testcontainers Postgres + Kafka + fake providers: register watch → seen (quorum) → confirmed → finalized (per-chain policy) → attest returns a signature; a separate flow: two-provider disagreement holds the fact and emits nothing; a third: reorg after confirmed emits `chain.tx.reorged`; a fourth: sanctioned counterparty → attest `BLOCKED`.
27. **Run full suite.** `mvn -pl services/crypto verify` must pass; Docker image builds from repo root.
28. **Threat-model closure.** Verify each `SECURITY-THREAT-MODEL.md` row (#1–#6) has a corresponding passing test; confirm no non-attest path can reach `kms:Sign` and no single-provider fact is ever emitted.
29. **Bump spec status.** Once §11 questions (esp. Q1, Q2, Q3, Q7) are closed and tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`.
