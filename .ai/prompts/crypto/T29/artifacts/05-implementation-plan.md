# crypto · T29 · Phase 5 — Implementation Plan

## Files to modify

- `spec/crypto-service/package.md` — header, §9, §11.
- `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java` — one new
  regression-guard method (Finding #8).

## Files to create

None.

## Note on Kimi's own item numbering

Kimi's Finding #2 refers to "item 14" for the Docker-build clause. The real §9 text has that clause
sharing **item 13** with `mvn verify` ("`mvn -pl services/crypto verify` passes (unit + integration with
fake providers); Docker image builds." is one bulleted line), and item 14 is actually the separate
contracts-coverage item. The finding's substance is correct regardless; this plan uses the real,
verified numbering.

## Exact edits

### 1. `package.md` header (lines 6, 9)

```
| Version | `0.1` |
```
→
```
| Version | `0.2` |
```
```
| Status | `DRAFT` |
```
→
```
| Status | `READY FOR IMPL` |
```

### 2. `package.md` §9 — all 14 items, each with a typed evidence citation

Every item ticked `[x]` unless noted. Exact final text per item:

1. `[x]` All §3 acceptance criteria have a passing named test from §8. *(unit test — 27/27 named tests verified present and passing, T29 Phase 2; 3 exhibit cosmetic naming drift from their real method names, disclosed, not functional gaps.)*
2. `[x]` Every §4a LOCKED decision implemented as written (no silent deviation). *(spot-verified across L1–L15 throughout T01–T28; no silent deviation found.)*
3. `[x]` Every §4c VERBATIM artifact copied exactly. *(contract tests — `CryptoInternalOpenApiContractTest`, per-event payload contract tests.)*
4. `[x]` No single-provider answer ever leaves the service as fact (L1). *(unit test — `QuorumEvaluatorTest`, `WatcherTest`'s `<3`-provider cases.)*
5. `[x]` `kms:Sign` reachable only from the attest path (L11, R22). *(ArchUnit — `KmsSignerArchitectureTest`, code-path half only; IAM/runtime half is a CI/IAM process concern, not code-testable — T28 Phase 9 disclosure, unchanged.)*
6. `[x]` Every provider response persisted verbatim before the quorum decision (L3). *(unit test — `ObservationLogTest`.)*
7. `[x]` Finality decided by the per-chain policy object (L4). *(unit test — `EthereumFinalityPolicyTest`, `TronFinalityPolicyTest`.)*
8. `[x]` Reorg walks the cursor backward and emits `chain.tx.reorged` (L6). *(unit test — `WatcherTest.shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg`; integration-level proof blocked — see item 13.)*
9. `[x]` Tokens matched only by `<chain, contractAddress>` (L7). *(unit test — `TokenValidatorTest`.)*
10. `[x]` Every emitted event carries the deterministic key `chain:txhash:eventtype` (L5). *(unit test — `TxLifecyclePublisherTest`, `ReorgDetectorTest`'s idempotency-key tests.)*
11. `[x]` Attest refuses to sign unless quorum + finality (+ screening) passed (L10, L12). *(unit test — `AttestationServiceTest.shouldRejectAttestWhenQuorumOrFinalityNotMet` / `.shouldReturnBlockedFromAttestOnSanctionedCounterparty`.)*
12. `[x]` No secret, provider API key, or KMS key ARN is committed (L13). *(code review — `application.properties` carries no secret values, `KmsProperties`/`@Validated` fails startup on missing config; CI/gitleaks process, not directly re-verified this task.)*
13. `[ ]` `mvn -pl services/crypto verify` passes; Docker image builds. **Partially true, disclosed honestly:** unit + ArchUnit portion passes (698+ tests, 0 failures in that subset). Integration portion (Testcontainers/Docker): last full run 759 tests / 6 failures / 4 errors — pre-existing defects unrelated to this spec's own §3/§8 scope, surfaced only because Docker became available during T28 (T28 Phase 12, flagged as follow-up). **Docker image build: attempted for real this task (T29 Phase 3) and genuinely fails** — Maven reactor validation error, `Child module /workspace/services/auth of /workspace/pom.xml does not exist`; `services/auth/Dockerfile` has the identical structural gap. Flagged as a new follow-up task, not fixed here.
14. `[x]` `contracts/api/crypto-internal.yaml` and `contracts/events/chain/*` cover every internal endpoint and event. *(contract tests — `CryptoInternalOpenApiContractTest`, `SeenPayloadContractTest`, `ConfirmedPayloadContractTest`, `FinalizedPayloadContractTest`, `ReorgedPayloadContractTest`, `ProviderDegradedPayloadContractTest`.)*

### 3. `package.md` §11 — Q1, Q2, Q3, Q7

Each existing line gets a resolution appended, mirroring Q8's own precedent style exactly.

**Q1** (append after existing text):
> **Resolved (2026-09-21, engineering half):** N is fixed at 3 with 2-of-3 (L1, not a tunable);
> `ProviderProperties` validates `providers.size() >= quorumThreshold` per chain. **Open follow-up:**
> commercial independence of the configured providers (shared infrastructure, ownership, jurisdiction)
> is an operational/procurement guardrail, not a runtime assertion — the code cannot and does not verify
> it; which 3 commercial vendors to actually contract with remains a deployment-time decision, per this
> question's own original scoping ("blocker for real deployment, not for fake-provider tests").

**Q2** (append after existing text):
> **Resolved (2026-09-21, engineering half):** `ScreeningClient` interface + `FailClosedScreeningClient`
> stub implemented and tested (`FailClosedScreeningClientTest`); R21
> (`AttestationServiceTest.shouldReturnBlockedFromAttestOnSanctionedCounterparty`) passes against it.
> **Open follow-up:** until a real vendor is wired, `FailClosedScreeningClient` returns `ERROR` for every
> call, so a real (non-test) deployment without a chosen vendor will have `/attest` refuse every request
> (fail-closed by design, L12) — this is intentional, not a defect, but worth stating explicitly for
> whoever provisions a non-test environment. No test-double gap exists: unit tests mock `ScreeningClient`
> directly, and `EndToEndIntegrationTest` (T26) already `@MockBean`s it.

**Q3** (append after existing text):
> **Resolved (2026-09-21):** `design.md` §4a-L12's fail-closed default is implemented exactly as
> specified — `FailClosedScreeningClient` returns `ERROR`, and `AttestationService` refuses to sign
> (no signature) on `ERROR` or any screening exception, tested directly
> (`FailClosedScreeningClientTest`, `AttestationServiceTest`).

**Q7** (append after existing text):
> **Resolved (2026-09-21, engineering half):** SHA-256 digest + ECDSA P-256 signing via KMS
> (`KmsSigner.SIGNING_ALGORITHM = SigningAlgorithmSpec.ECDSA_SHA_256`), verified end-to-end against a
> compatible key (`KmsSignerTest`, `KmsSignerLocalStackIntegrationTest`). **Open follow-up:** this is a
> single named code constant, not a runtime-configurable value — changing the provisioned key's
> algorithm would require a code change and redeploy, not just new config. Make `signingAlgorithm`
> configurable via `KmsProperties` if the platform ever provisions a different key spec (e.g. secp256k1,
> RSA-PSS).

### 4. `T01SkeletonRegressionTest.java` — new regression-guard method (Finding #8)

New `@Test` method, added alongside the existing threat-model guard, reading
`spec/crypto-service/package.md` directly:

```java
private static final Path CRYPTO_PACKAGE_SPEC = Path.of("../../spec/crypto-service/package.md");

@Test
void packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo() throws IOException {
    String spec = Files.readString(CRYPTO_PACKAGE_SPEC);

    assertThat(spec).as("Version must be bumped to 0.2").contains("| Version | `0.2` |");
    assertThat(spec).as("Status must be READY FOR IMPL").contains("| Status | `READY FOR IMPL` |");
}
```

Deliberately narrow: asserts only the two header fields this task actually changes, not a blanket "every
§9 item is `[x]`" assertion — item 13 is genuinely, honestly partial, and a broader assertion would
either be wrong today or would need constant updating as the Docker/CI follow-ups land. A future,
separate follow-up task can strengthen this once item 13 is fully closed.

## Execution order

1. Apply the four `package.md` edits (header, §9, §11) exactly as pinned above.
2. Add the new `T01SkeletonRegressionTest` method.
3. Run `mvn -pl services/crypto -am test -Dtest=T01SkeletonRegressionTest` to confirm the new method
   passes.
4. Run the full `mvn -pl services/crypto -am verify` and record the real result for Phase 6's own notes.
5. Re-read `package.md` in full to confirm no other line was touched (rows #7–#8-equivalent sections:
   §1–§8, §10, and every part of §9/§11 not explicitly listed above).
