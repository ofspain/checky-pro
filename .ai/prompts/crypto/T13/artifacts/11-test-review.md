# crypto · T13 · Phase 11 — Test Review Findings

Reviewed: `AddressPoisoningDetectorTest.java` and the `AddressPoisoningDetector.java` it exercises.

---

### 1. The named test `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` only exercises a prefix match

- **Gap:** The test vector (`"ABCDEF0000"` vs. `"ABCDEF9999"`) shares 6 leading characters and zero trailing characters, so it is a pure prefix-match test. The method name implies the general prefix-or-suffix capability, but the named test does not demonstrate a suffix match.
- **Why it matters:** A regression that accidentally broke suffix detection (e.g., an off-by-one in `hasMatchingSuffix`) would not be caught by the package.md-named test, even though the name suggests it covers both directions.
- **Suggested test:** Either rename the test to `shouldFlagAddressPoisoningOnPrefixSimilarity` and add a separate named-style test for suffix, or change the vector to one where the candidate and previous share both a prefix and a suffix (e.g., `"ABCDEF00GH"` vs. `"ABCDEF99GH"`).

---

### 2. No test with a mixed history of matching and non-matching addresses

- **Gap:** Every "flags" test uses a history with exactly one previous address, and that address matches. There is no test where the history contains one unrelated address and one matching address.
- **Why it matters:** It verifies that the loop continues past non-matches and correctly returns the matching entry, rather than returning empty on the first non-match or returning the wrong entry.
- **Suggested test:** Add `flagsWhenOnlyOneAddressInHistoryMatches` with history `List.of("999999XXXX", "ABCDEF9999")` and candidate `"ABCDEF0000"`, asserting the result contains `"ABCDEF9999"`.

---

### 3. No test for a candidate whose length is exactly the suffix threshold

- **Gap:** `doesNotFlagOrThrowForAShortCandidateAddress` uses a 2-character candidate, well below the 4-character suffix threshold. There is no test for a 4-character candidate that shares its entire value as the suffix of a longer previous address.
- **Why it matters:** It exercises the boundary where `candidate.length() - SUFFIX_MATCH_LENGTH` is zero (a non-negative offset) and confirms the suffix comparison works when the candidate is the minimum possible length.
- **Suggested test:** Add `flagsWhenCandidateIsExactlyTheSuffixLengthAndMatchesTheSuffixOfAPreviousAddress` with candidate `"WXYZ"` and previous `"ABCDEFWXYZ"`.

---

### 4. No test for a previously-seen address whose length is between the suffix and prefix thresholds

- **Gap:** The short-previous test uses `"AB"` (length 2), below both thresholds. There is no test for a previous address of length 5, which is long enough for a suffix match but too short for a prefix match.
- **Why it matters:** It verifies that `hasMatchingPrefix` correctly rejects a previous address shorter than 6 while `hasMatchingSuffix` can still legitimately match its last 4 characters against a longer candidate.
- **Suggested test:** Add `flagsViaSuffixWhenPreviousAddressIsTooShortForPrefixMatch` with candidate `"ABCDEFWXYZ"` and previous `"ABCWXYZ"` (length 7: prefix too short, suffix matches).

---

### 5. No EVM-shaped suffix-match test

- **Gap:** The EVM-shaped vector in the test file (`doesNotFlagTwoEvmAddressesSharingOnlyTheZeroXPrefixAndTwoHexDigits`) only covers the negative prefix case. There is no EVM-shaped string test for a suffix match.
- **Why it matters:** The algorithm is chain-agnostic, but a realistic EVM-shaped suffix test documents that the suffix check works on addresses with the same structural properties as real inputs.
- **Suggested test:** Add `flagsAnEvmShapedAddressWithASuffixMatch` using candidates like `"0x1111111111111111111111111111111111ABCD"` and `"0x2222222222222222222222222222222222ABCD"`.

---

### 6. No test for candidate and previous addresses of different lengths

- **Gap:** All test vectors use candidate and previous addresses of the same length. The algorithm is string-length-agnostic, but no test confirms that a longer candidate can match a shorter previous (or vice versa) on prefix or suffix.
- **Why it matters:** Real addresses within a chain have fixed lengths, but cross-chain or malformed inputs may not; the test suite should lock in that length differences do not prevent a valid prefix/suffix match.
- **Suggested test:** Add `flagsAPrefixMatchRegardlessOfLengthDifference` with candidate `"ABCDEF00000000"` and previous `"ABCDEF9999"`, and a corresponding suffix test.

---

### 7. No test for exact-match short-circuit when the same address appears alongside a look-alike

- **Gap:** There is no test where the candidate exactly equals one previous address while also resembling a different previous address. The loop's short-circuit behavior (exact match skipped, then look-alike flagged) is not exercised.
- **Why it matters:** It documents and protects the precedence rule that an exact match is not poisoning, even when another entry in the same history would otherwise be flagged.
- **Suggested test:** Add `doesNotFlagAnExactMatchEvenWhenAnotherHistoryEntryResemblesIt` with candidate `"ABCDEF0000"` and history `List.of("ABCDEF0000", "ABCDEF1111")`, asserting empty.

---

### 8. No test for both parameters being `null` simultaneously

- **Gap:** `null` candidate and `null` history are tested separately, but not together.
- **Why it matters:** It is a trivial boundary confirming the leading `||` check returns empty regardless of which parameter is null.
- **Suggested test:** Add `returnsEmptyWhenBothParametersAreNull`.
