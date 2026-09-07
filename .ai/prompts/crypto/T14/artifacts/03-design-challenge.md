# crypto · T14 · Phase 3 — Design Challenge Findings

Consumed: `artifacts/02-task-implementation-brief.md`
References: `spec/crypto-service/agents.md`, `spec/crypto-service/package.md` §8, `spec/crypto-service/requirements.md` (R6/R7), `spec/crypto-service/design.md` (L4), `spec/crypto-service/tasks.md` (T14), existing `services/crypto/src/main/java/com/themistra/crypto/adapter/Chain.java`, `adapter/model/FinalityStatus.java`, and `common/config/FinalityProperties.java`.

---

### 1. `FinalityStatus` carries no chain discriminator, so policies cannot self-verify they are applied to the right chain

- **Severity:** Medium
- **Evidence:** `FinalityStatus` is a record of three `long` block numbers with no `chain` field (`adapter/model/FinalityStatus.java:23-27`). `EthereumFinalityPolicy` and `TronFinalityPolicy` happen to use the identical comparison (`txBlockNumber <= finalizedBlockNumber`), so a caller accidentally passing a Tron status to the Ethereum policy (or vice versa) will still get a boolean answer -- it will not be rejected or detected as a caller error.
- **Recommended brief amendment:** Either add an explicit note that callers are responsible for routing the correct `FinalityStatus` to the correct policy (and that the policy's `chain()` method is only an identifier for future dispatchers), or add a `Chain chain()` field to `FinalityStatus` so policies can assert the match. If the latter, note that it requires modifying `adapter/model/FinalityStatus.java`, which the TIB currently marks as frozen.

---

### 2. No module-boundary test is required for the new `finality/` package

- **Severity:** Low
- **Evidence:** The TIB creates a new top-level feature package `com.themistra.crypto.finality` but does not list any module-boundary test (compare T11's `TokenModuleBoundaryTest` and T10's `ProviderModuleBoundaryTest`). L15 (`design.md`) says module boundaries are enforced by ArchUnit.
- **Recommended brief amendment:** Add a required `FinalityModuleBoundaryTest` (mirroring the existing boundary-test pattern) asserting that `finality/` does not import `observation`, `provider`, `quorum`, `token`, or `events`, and that any import of `adapter/` is limited to `adapter.Chain` and `adapter.model.FinalityStatus`.

---

### 3. The two policy implementations will be identical except for the returned `Chain` constant

- **Severity:** Low
- **Evidence:** Both `EthereumFinalityPolicy` and `TronFinalityPolicy` will implement `isFinal` as the exact same one-line comparison. The only difference is `chain()` returning `ETHEREUM` vs `TRON`. While this correctly follows L4's "per-chain policy object" shape, it creates a maintenance risk: a future developer might "DRY" them into a single class, defeating the per-chain policy intent.
- **Recommended brief amendment:** Add a comment in both classes and in the brief explicitly stating that the code duplication is intentional and required by L4 -- each chain must have its own policy object, even when the current comparison happens to be the same.

---

### 4. No test verifies the `FinalityPolicy` interface contract is satisfied by both implementations

- **Severity:** Low
- **Evidence:** The Required Tests cover `chain()` and `isFinal()` separately for each policy, but there is no test asserting that both classes actually implement the `FinalityPolicy` interface.
- **Recommended brief amendment:** Add a test (or two) asserting `assertThat(new EthereumFinalityPolicy()).isInstanceOf(FinalityPolicy.class)` and the same for `TronFinalityPolicy`.

---

### 5. The policies trust adapters to enforce semantic validity of `FinalityStatus` fields

- **Severity:** Low
- **Evidence:** `isFinal` performs a raw `txBlockNumber <= finalizedBlockNumber` comparison with no guard against impossible states such as `finalizedBlockNumber > currentBlockNumber` or negative block numbers. The adapters already throw on impossible states (T06/T07), but the policy has no defense-in-depth.
- **Recommended brief amendment:** Document in the brief and in class Javadoc that policies assume `FinalityStatus` is a trusted, adapter-produced value and do not re-validate it. Optionally add a note that if a future task ever constructs `FinalityStatus` outside an adapter, validation should be added.

---

### 6. `FinalityProperties` enabled-chains list is not linked to the policies

- **Severity:** Low
- **Evidence:** `FinalityProperties` (`common/config/FinalityProperties.java`) lists which chains have finality checking enabled, but the TIB explicitly does not create a dispatcher or registry mapping `Chain` -> `FinalityPolicy`. The two pieces exist in isolation.
- **Recommended brief amendment:** Add an Open Question or dependency note reminding the author that a future task (most plausibly the watcher/attest layer) must wire `FinalityProperties.enabledChains` together with the available `FinalityPolicy` beans; otherwise the enabled-chains config has no runtime effect.

---

### 7. No test exercises the policies with `txBlockNumber` and `finalizedBlockNumber` at the extreme ends of the `long` range

- **Severity:** Low
- **Evidence:** The Required Tests include equality and equality+1 boundaries, but not near `Long.MAX_VALUE` or `Long.MIN_VALUE`. While block numbers will never approach these values, the comparison is a signed `long` comparison and could theoretically behave unexpectedly if fabricated test data were used.
- **Recommended brief amendment:** Add a low-value test using very large block numbers to confirm the comparison is signed and correct, or explicitly document that such values are out of scope.
