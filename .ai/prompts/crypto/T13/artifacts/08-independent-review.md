# crypto · T13 · Phase 8 — Independent Code Review Findings

Reviewed: `AddressPoisoningDetector.java`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md`, `artifacts/06-implementation-notes.md`, and the existing test tree.

---

### 1. `AddressPoisoningDetectorTest` is missing from the working tree

- **Issue:** The frozen brief lists thirteen required tests and the implementation plan explicitly creates `services/crypto/src/test/java/com/themistra/crypto/token/AddressPoisoningDetectorTest.java`. That file does not exist; the `token/` test directory contains only T11/T12 tests.
- **Evidence:** `find services/crypto/src/test/java/com/themistra/crypto/token -name '*AddressPoisoning*'` returns nothing; `git status` is clean.
- **Recommendation:** Create `AddressPoisoningDetectorTest.java` with the test cases from the frozen brief's Required Tests section before marking the task complete.
- **Confidence:** High.

---

### 2. `detectPoisoning` lacks method-level Javadoc

- **Issue:** The class-level Javadoc thoroughly documents null-safety, case-sensitivity, arbitrary-match behavior, scope, and caller responsibilities, but the public `detectPoisoning` method itself has no Javadoc. A caller inspecting only the method signature in an IDE will not see these contracts.
- **Evidence:** `AddressPoisoningDetector.java:58` has no Javadoc; the relevant contracts are at lines 8-41 only.
- **Recommendation:** Move or duplicate a condensed version of the caller-relevant contracts (null-safety, case-sensitivity, arbitrary match, no validation) onto the method's own Javadoc.
- **Confidence:** High.

---

### 3. Uniform prefix threshold makes Tron detection stricter than the original intent

- **Issue:** `PREFIX_MATCH_LENGTH = 6` correctly restores 4 real matching hex digits for EVM beyond the universal `0x`, but Tron mainnet addresses share only a single universal character (`T`). A uniform 6-character prefix threshold therefore requires 5 real matching Base58 characters beyond `T`, whereas the original 4-character threshold would have required only 3. This makes Tron detection more conservative.
- **Evidence:** `AddressPoisoningDetector.java:45-51` and the frozen brief Amendment #1 both acknowledge this tradeoff explicitly.
- **Recommendation:** No code change needed — the tradeoff is deliberate and documented. Worth revisiting only if real Tron traffic later shows under-detection; consider making the threshold configurable per chain in that future task.
- **Confidence:** High.

---

### 4. Empty-string candidate is not explicitly tested

- **Issue:** The implementation safely handles an empty-string candidate (`hasMatchingPrefix`/`hasMatchingSuffix` both return `false` via `regionMatches`), but the planned tests only use a 2-character short candidate (`"AB"`).
- **Evidence:** Implementation plan Verified Test Vectors (lines 99-102) lists `"AB"` for the short-candidate case; no empty-string vector.
- **Recommendation:** Add `doesNotFlagOrThrowForAnEmptyCandidateAddress` to lock in the safe behavior for the most trivial hostile input.
- **Confidence:** Medium.

---

### 5. No test verifies the arbitrary-match-among-multiples behavior

- **Gap:** AC6 says any one of several resembling previously-seen addresses may be returned. There is no test with two or more matching previous addresses asserting that the result is one of them.
- **Why it matters:** Without such a test, a future refactor that accidentally changed iteration order or short-circuiting could return a non-matching address or fail to return any match, and the existing tests would not catch it.
- **Suggested test:** Add `returnsOneOfMultipleMatchingPreviouslySeenAddresses` with a history containing two different addresses that both resemble the candidate, and assert the returned value is one of those two.
- **Confidence:** Medium.

---

### 6. No test covers a candidate that matches both prefix and suffix simultaneously

- **Gap:** The existing plan tests prefix-only and suffix-only matches separately, but not a candidate that shares both a ≥6-character prefix and a ≥4-character suffix with a previous address while differing in the middle.
- **Why it matters:** The implementation uses `||`, so either match returns the previous address. A combined-match test documents that the method does not require both conditions.
- **Suggested test:** Add `flagsWhenBothPrefixAndSuffixMatch` using candidate `"ABCDEFGHWXYZ"` vs. previous `"ABCDEFGH1234WXYZ"` (or any pair sharing both regions).
- **Confidence:** Low.

---

### 7. No test exercises a history with only short addresses

- **Gap:** The plan tests a normal candidate against a history containing one short address, but does not test a normal candidate against a history consisting entirely of short addresses.
- **Why it matters:** It confirms the loop completes safely and returns empty when no entry is long enough to match.
- **Suggested test:** Add `returnsEmptyWhenAllPreviouslySeenAddressesAreTooShortToMatch`.
- **Confidence:** Low.

---

### 8. Generics erasure allows non-`String` elements to reach the loop

- **Issue:** The method signature accepts `Collection<String>`, but at runtime a caller could pass a raw `Collection` containing non-`String` objects. The enhanced for-loop `for (String previous : previouslySeenAddresses)` would then throw `ClassCastException`, violating the class's "never throws" intent.
- **Evidence:** `AddressPoisoningDetector.java:62`; Java generics are erased at runtime, so the JVM cannot enforce element type.
- **Recommendation:** This is a caller-discipline issue, not a normal-use bug. If hardening is desired, iterate over `Collection<?>` and skip elements that are not instances of `String`. Add a test with a raw collection containing a non-string element if this hardening is implemented.
- **Confidence:** Low.
