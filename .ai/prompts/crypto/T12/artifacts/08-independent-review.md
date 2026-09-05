# crypto · T12 · Phase 8 — Independent Code Review Findings

Reviewed: `AddressValidator.java`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md`, `artifacts/06-implementation-notes.md`, and the existing test tree.

---

### 1. `isValidTronAddress` throws `NegativeArraySizeException` for short input, violating AC9

- **Issue:** `isValidTronAddress` (`AddressValidator.java:57-67`) catches only `IllegalArgumentException` around `Base58Check.base58ToBytes(address)`. For any input whose decoded raw Base58 byte length is less than 4 — concretely the empty string and strings of 1-4 characters — `Base58Check.base58ToBytes` throws `java.lang.NegativeArraySizeException` (it internally calls `Arrays.copyOf(raw, raw.length - 4)`). That exception is not caught, so the method leaks it instead of returning `false`.
- **Evidence:** `AddressValidator.java:64-66`. The self-review (`artifacts/07-self-review.md` Finding 1) independently confirmed this by direct execution against the pinned `trident` library: `len=0 -> NegativeArraySizeException: -4`, through `len=4 -> NegativeArraySizeException: -1`.
- **Recommendation:** Broaden the catch to `RuntimeException` (defense-in-depth against any other unenumerated exception from the third-party library), and/or add an explicit minimum-length guard so the "too short to be valid" path is self-documenting. Add required tests for the empty string and very-short Tron inputs.
- **Confidence:** High.

---

### 2. `AddressValidatorTest` is missing from the working tree

- **Issue:** The frozen brief lists nine required tests and the implementation plan explicitly creates `services/crypto/src/test/java/com/themistra/crypto/token/AddressValidatorTest.java`. That file does not exist; the `token/` test directory contains only the five T11 test files.
- **Evidence:** `find services/crypto/src/test/java/com/themistra/crypto/token -name '*AddressValidator*'` returns nothing; `git status` is clean.
- **Recommendation:** Create `AddressValidatorTest.java` with the test cases from the frozen brief's Required Tests section (and the additional short/empty Tron vectors from Finding 4 below).
- **Confidence:** High.

---

### 3. No minimum-length guard for Tron addresses

- **Issue:** `MAX_TRON_ADDRESS_LENGTH` (`AddressValidator.java:46`) makes the "too long" path explicit and self-documenting, but there is no symmetrical `MIN_TRON_ADDRESS_LENGTH` guard for the "too short" path.
- **Evidence:** `AddressValidator.java:43-46` defines only the maximum constant; the method jumps straight into `Base58Check` decoding for any non-null string up to 64 characters.
- **Recommendation:** Add a `MIN_TRON_ADDRESS_LENGTH` constant (real Tron addresses are 34 characters; a bound such as 25 or 30 safely excludes all malformed short inputs while staying well below valid lengths) and reject inputs below it before calling `Base58Check`.
- **Confidence:** High.

---

### 4. Test vectors and required tests omit short/empty Tron inputs

- **Issue:** Neither the frozen brief's Required Tests nor the implementation plan's Verified Test Vectors table includes an empty string or a 1-4 character Tron input. Because those are exactly the inputs that trigger the uncaught `NegativeArraySizeException`, the gap in test coverage is what allowed the bug to survive Phase 6.
- **Evidence:** `artifacts/04-frozen-task-brief.md` Required Tests (lines 143-156) lists `null`, illegal character, wrong checksum, wrong shape, oversized, but not empty/short; `artifacts/05-implementation-plan.md` Verified Test Vectors table (lines 89-98) likewise has no short vectors.
- **Recommendation:** Add `rejectsAnEmptyTronAddress`, `rejectsAOneCharacterTronAddress`, and `rejectsAFourCharacterTronAddress` to `AddressValidatorTest` to lock in the fix for Finding 1.
- **Confidence:** High.

---

### 5. Implementation notes falsely claim AC9 is fully satisfied

- **Issue:** `artifacts/06-implementation-notes.md` states that AC9 (null-safety) is satisfied by the leading `null` checks in both methods. This ignores the short-Tron-input exception path identified in Finding 1, so the claim is incorrect.
- **Evidence:** `artifacts/06-implementation-notes.md` lines 24-25; contrast with `AddressValidator.java:57-67` and the self-review's execution trace.
- **Recommendation:** Update the implementation notes after fixing Finding 1 to note that AC9 also relies on the broadened catch block and/or minimum-length guard, not only the `null` check.
- **Confidence:** High.

---

### 6. No explicit verification that `TokenModuleBoundaryTest` still passes with `AddressValidator`

- **Issue:** `AddressValidator` lives in `com.themistra.crypto.token`, so the existing `TokenModuleBoundaryTest` (T11) will scan it for forbidden imports. `AddressValidator` only imports `org.web3j.crypto.Keys` and `org.tron.trident.utils.Base58Check`, both external libraries, so it should pass. However, the Phase 6 notes assert this without evidence, and no T12-specific boundary verification is listed.
- **Evidence:** `AddressValidator.java:3-5`; `TokenModuleBoundaryTest.java` scans all `.java` files under `src/main/java/com/themistra/crypto/token`.
- **Recommendation:** Add a one-line note to the implementation notes confirming `TokenModuleBoundaryTest` was executed and passed after `AddressValidator.java` was added, or include it in the test run command.
- **Confidence:** Medium.

---

### 7. Strict EIP-55 policy rejects the all-zero EVM address

- **Issue:** `isValidEvmAddress` rejects any all-lowercase address, including `0x0000000000000000000000000000000000000000`. The frozen brief explicitly chose this strict interpretation (Amendment #1), but the implementation plan's test vectors do not include the zero address, so the policy is not locked in by a concrete test.
- **Evidence:** `AddressValidator.java:48-55`; `artifacts/04-frozen-task-brief.md` Amendment #1.
- **Recommendation:** Add an explicit test asserting the zero address returns `false` (if the strict policy is intended to cover it) or document that the zero address is intentionally rejected, to avoid future confusion.
- **Confidence:** Low.
