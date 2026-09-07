# crypto · T14 · Phase 8 — Independent Code Review Findings

Reviewed: `FinalityPolicy.java`, `EthereumFinalityPolicy.java`, `TronFinalityPolicy.java`,
`artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md`,
`artifacts/06-implementation-notes.md`, `FinalityStatus.java`, and the relevant adapter methods.

No correctness, thread-safety, module-boundary, or spec-deviation defects found in the runtime code.
The findings below are documentation/test-coverage gaps and residual logical risks.

---

### 1. `FinalityPolicy` Javadoc overstates the adapter guarantees

**Issue:** The class-level Javadoc claims both real adapters "already throw before ever constructing an
inconsistent `FinalityStatus`" (`FinalityPolicy.java:29-30`). `TronAdapter.getFinalityStatus` does guard
both `finalizedBlockNumber > currentBlockNumber` and `txBlockNumber > currentBlockNumber`
(`TronAdapter.java:190-205`), but `EthereumAdapter.getFinalityStatus` only guards
`finalizedBlockNumber > currentBlockNumber` (`EthereumAdapter.java:155-163`) and does **not** guard a
transaction block ahead of the reported current head. The documentation therefore claims a broader
invariant than actually holds on Ethereum.

**Evidence:** `FinalityPolicy.java:27-30`; `EthereumAdapter.java:145-166` vs.
`TronAdapter.java:179-207`.

**Recommendation:** Tighten the Javadoc to say adapters guard the invariant that matters for the
comparison (`finalizedBlockNumber <= currentBlockNumber`) and that `txBlockNumber` is known-mined,
rather than the blanket "inconsistent" claim. Alternatively, note the chain-specific difference
explicitly.

**Confidence:** High.

---

### 2. Wrong-chain status routing remains a silent logical risk

**Issue:** `FinalityStatus` carries no chain discriminator, and neither policy verifies that the given
status came from its own chain's adapter. The risk was accepted as documentation-only in the frozen
brief (Finding 1), but a future dispatcher could still route an Ethereum-derived status to
`TronFinalityPolicy` and obtain a silently-wrong boolean. No test locks in the documented "no
self-check" contract.

**Evidence:** `FinalityPolicy.java:21-25`; `EthereumFinalityPolicy.java:31-33`;
`TronFinalityPolicy.java:40-42`.

**Recommendation:** Add a Phase 10 test (e.g., `doesNotSelfValidateChainWhenGivenAWrongChainStatus`)
that passes an Ethereum-shaped `FinalityStatus` to `TronFinalityPolicy` and asserts the policy applies
the raw comparison anyway. This documents the accepted risk in executable form and prevents a future
refactor from silently adding (or removing) a chain self-check.

**Confidence:** High.

---

### 3. No test exercises a `txBlockNumber` of zero (genesis / borderline finality)

**Issue:** The required tests cover equality (`txBlockNumber == finalizedBlockNumber`) and one-above
(`txBlockNumber == finalizedBlockNumber + 1`), but they do not cover the zero boundary, which is a
realistic value for a transaction mined in the chain's first block. A zero `txBlockNumber` with a
non-negative `finalizedBlockNumber` must be final; the policy is trivially correct, but the absence of
a test leaves the behavior implicit.

**Evidence:** `artifacts/04-frozen-task-brief.md` Required Tests section (`:139-150`);
`EthereumFinalityPolicy.java:31-33`; `TronFinalityPolicy.java:40-42`.

**Recommendation:** Add a zero-boundary case to both `EthereumFinalityPolicyTest` and
`TronFinalityPolicyTest`: `txBlockNumber == 0`, `finalizedBlockNumber == 0` → `true`; and `txBlockNumber
== 0`, `finalizedBlockNumber == -1` → `false` if negative values are ever considered in test vectors.

**Confidence:** Medium.

---

### 4. No test verifies that `currentBlockNumber` far behind `txBlockNumber` is ignored

**Issue:** The planned "scripted heads" test only uses `currentBlockNumber` far ahead of both other
fields. It does not exercise the inverse case (`currentBlockNumber` less than `txBlockNumber` and/or
`finalizedBlockNumber`). Since the policy intentionally ignores `currentBlockNumber`, both directions
should be covered to lock in the independence.

**Evidence:** `artifacts/05-implementation-plan.md` lines 60-75; `FinalityStatus.java:23-27`.

**Recommendation:** Add a scripted-heads variant where `currentBlockNumber` is far behind
`txBlockNumber` and `finalizedBlockNumber`, and assert the finality decision still depends only on
`txBlockNumber <= finalizedBlockNumber`.

**Confidence:** Medium.

---

### 5. Null-handling test does not assert the exception message

**Issue:** The frozen brief requires `isFinal(null)` to throw `NullPointerException`. The implementation
uses `Objects.requireNonNull(status, "status")`, which does throw `NullPointerException` with message
`"status"`. A test that only asserts `assertThatNullPointerException().isThrownBy(...)` would pass even
if the message were accidentally changed to something unhelpful or removed.

**Evidence:** `EthereumFinalityPolicy.java:32`; `TronFinalityPolicy.java:41`;
`artifacts/04-frozen-task-brief.md:149`.

**Recommendation:** In both policy tests, assert the exception message is non-null and contains
"status" (or the expected text), e.g.
`assertThatExceptionOfType(NullPointerException.class).isThrownBy(...).withMessageContaining("status")`.

**Confidence:** Low.

---

### 6. No test prevents accidental collapse of the two policy classes

**Issue:** The frozen brief and Javadoc explicitly require two separate classes because L4 mandates a
per-chain policy object. The current code satisfies that, but there is no automated guard (other than
source inspection) preventing a future refactor from replacing the two `@Component` classes with a
single generic `FinalityPolicy` bean keyed by `Chain`. Compilation alone would not catch such a change.

**Evidence:** `EthereumFinalityPolicy.java:16-20`; `TronFinalityPolicy.java:25-29`;
`artifacts/04-frozen-task-brief.md:74-75`.

**Recommendation:** Add a small reflection/source-scan test in `FinalityModuleBoundaryTest` (or a
separate shape test) that asserts `EthereumFinalityPolicy` and `TronFinalityPolicy` are two distinct
concrete classes, each directly implementing `FinalityPolicy`, and that neither is an inner class or a
parameterized generic of a shared base.

**Confidence:** Low.
