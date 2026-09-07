# crypto · T14 · Phase 11 — Test Review Findings

Reviewed: `EthereumFinalityPolicyTest.java`, `TronFinalityPolicyTest.java`,
`FinalityModuleBoundaryTest.java`, `artifacts/10-test-generation.md`, and the frozen brief's Required
Tests. All 18 tests pass, named tests are present, and the acceptance-criteria coverage is good. The
gaps below are strengthening opportunities rather than missing AC coverage.

---

### 1. Null-handling tests do not assert the exception message

**Gap:** Both `EthereumFinalityPolicyTest.isFinalThrowsOnNullStatus` and
`TronFinalityPolicyTest.isFinalThrowsOnNullStatus` use `assertThatNullPointerException().isThrownBy(...)`
without checking the message. The production code deliberately supplies the message `"status"` via
`Objects.requireNonNull`; a regression that removed or changed that message would still leave these
tests green.

**Why it matters:** The brief's null-handling contract is not just "throw NPE" but "fail fast with a
clear caller bug signal". Asserting the message locks in the intent and makes the test self-documenting.

**Suggested test:** Change both assertions to
`assertThatNullPointerException().isThrownBy(() -> policy.isFinal(null)).withMessageContaining("status")`.

---

### 2. No source-level guard that Tron's "~19 confirmations" never becomes a code literal

**Gap:** AC2/R7 explicitly requires that `~19` never appears as a literal threshold in the policy code.
The current tests verify behavior (comparing against the solidified block), but none scan the production
source for a forbidden numeric literal or confirmation-count constant.

**Why it matters:** A future refactor could introduce a constant such as `MIN_CONFIRMATIONS = 19` and the
existing functional tests would still pass if the comparison happened to align. A source scan is the only
way to enforce the "no literal threshold" rule mechanically.

**Suggested test:** Add a source-scan test (possibly inside `FinalityModuleBoundaryTest`) that reads
`EthereumFinalityPolicy.java` and `TronFinalityPolicy.java` as text and asserts neither contains the
digit sequence `19` in any numeric literal, constant name, or comment that implies a confirmation-count
threshold. Alternatively, forbid any integer literal other than `0`/`1` in the `isFinal` method body.

---

### 3. No non-boundary "not final" case

**Gap:** The negative path is covered only by the one-above boundary (`txBlockNumber ==
finalizedBlockNumber + 1`). There is no test where the transaction block is far above the finalized/
solidified block, e.g. `txBlockNumber = 1_000L`, `finalizedBlockNumber = 100L`.

**Why it matters:** The boundary test is sufficient for correctness, but a non-boundary negative case
more clearly demonstrates that the policy is not accidentally returning `true` for any status where the
transaction block is simply newer than the finality checkpoint.

**Suggested test:** Add `isNotFinalWhenTxBlockIsWellAboveTheFinalizedCheckpoint` to both policy tests
with a large gap between `txBlockNumber` and `finalizedBlockNumber`.

---

### 4. Cross-policy "no self-check" test only exercises the `true` path

**Gap:** `EthereumFinalityPolicyTest.appliesTheSameRawComparisonAsTronFinalityPolicyForTheSameStatus`
uses a status where both policies return `true` (`txBlockNumber == finalizedBlockNumber`). It does not
verify that the two policies also agree when the comparison returns `false`.

**Why it matters:** The test's purpose is to lock in that neither policy applies a chain-specific
self-check. Exercising only one output value leaves room for a subtle divergence in the `false` branch
that the assertion would miss.

**Suggested test:** Either add a second assertion in the existing test using a not-final status, or
parameterize the cross-policy test over one final and one not-final `FinalityStatus`.

---

### 5. No automated guard that the two policies remain separate concrete classes

**Gap:** L4 requires a per-chain policy object, and the Javadoc explicitly warns against collapsing the
two classes. The tests do not verify that `EthereumFinalityPolicy` and `TronFinalityPolicy` are still
two distinct top-level classes directly implementing `FinalityPolicy`.

**Why it matters:** A future "cleanup" could replace them with a single generic implementation plus a
factory or `Chain` parameter, and every existing functional test would continue to pass because the
factory would still produce objects that behave identically.

**Suggested test:** Add a reflection test (e.g., in `FinalityModuleBoundaryTest` or a new
`FinalityPolicyShapeTest`) asserting that `EthereumFinalityPolicy` and `TronFinalityPolicy` are not
abstract, are not inner classes, each directly `implements FinalityPolicy`, and that no other
`FinalityPolicy` implementation exists in the `finality` package.

---

### 6. Module-boundary scan does not catch fully-qualified inline references or static imports

**Gap:** `FinalityModuleBoundaryTest` scans only lines that start with `import `. It would miss a
fully-qualified reference such as `com.themistra.crypto.provider.SomeUtil.doWork()` that appears
without an import, and it would miss a forbidden static import because those lines start with
`import static `.

**Why it matters:** The production code does not currently do either, but the scan's contract is
"nothing in `finality/` depends on forbidden packages", not "no regular import statements". A future
addition could sneak in via an inline reference or static import.

**Suggested test:** Extend the scan to read the entire file content (not just `import` lines) and assert
no occurrence of any fully-forbidden package prefix, while still allow-listing the two permitted
`adapter` imports/references.
