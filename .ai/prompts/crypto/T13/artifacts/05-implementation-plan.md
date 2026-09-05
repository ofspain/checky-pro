# crypto · T13 · Phase 5 — Implementation Plan

## Files to create

One file, tracing directly to the frozen brief's "Files to Create" list:

1. `services/crypto/src/main/java/com/themistra/crypto/token/AddressPoisoningDetector.java`

Test file:
2. `services/crypto/src/test/java/com/themistra/crypto/token/AddressPoisoningDetectorTest.java`

## Files to modify

None.

## Public methods (signatures)

```java
@Component
public class AddressPoisoningDetector {

    public Optional<String> detectPoisoning(String candidateAddress, Collection<String> previouslySeenAddresses);
}
```

## Private methods

- `private boolean hasMatchingPrefix(String candidate, String previous)` —
  `candidate.regionMatches(0, previous, 0, PREFIX_MATCH_LENGTH)`.
- `private boolean hasMatchingSuffix(String candidate, String previous)` —
  `candidate.regionMatches(candidate.length() - SUFFIX_MATCH_LENGTH, previous,
  previous.length() - SUFFIX_MATCH_LENGTH, SUFFIX_MATCH_LENGTH)`.

**Verified by direct execution in this phase (not assumed from memory, mirroring this session's own
established discipline — especially given T12's own `Base58Check` surprise):** `String.regionMatches`
returns `false` (never throws) for a negative `toffset`/`ooffset`, or when `toffset+len`/`ooffset+len`
exceeds either string's length:

```
negative toffset: false
negative ooffset: false
toffset+len > length: false
ooffset+len > other.length: false
```

This is exactly the safety property Amendment #3 (Kimi Issue 3) requires — a candidate or previously-
seen address shorter than `SUFFIX_MATCH_LENGTH` produces a negative `toffset`/`ooffset` in the suffix
check above, which `regionMatches` itself handles cleanly, with no manual bounds-checking needed in
`AddressPoisoningDetector`'s own code.

## Constants

```java
// Amendment #1 (Kimi Issue 1): every EVM address begins with the literal "0x" and every Tron mainnet
// address begins with "T" - a flat 4-character prefix threshold would only require 2 (EVM) or 3 (Tron)
// additional matching characters beyond that universal prefix. 6 restores 4 real matching hex digits
// for EVM (0x + 4 more) without making this detector chain-aware.
private static final int PREFIX_MATCH_LENGTH = 6;

// Suffix carries no chain-mandated shared prefix, so the noise concern above never applied here -
// kept at the original wallet-truncation-derived value.
private static final int SUFFIX_MATCH_LENGTH = 4;
```

## Entities used

None — no persistence.

## Repositories used

None.

## Services used

None — no dependency, no injected collaborator.

## Verified test vectors

**EVM-prefix-noise regression (AC8, the specific fix for Amendment #1):** two syntactically-EVM-shaped
addresses sharing only `0x` plus 2 hex digits:
- Candidate: `0xAB1111111111111111111111111111111111111A`
- Previous: `0xAB2222222222222222222222222222222222222B`
- Shared leading characters: `0xAB` (4 total) — below the raised 6-character threshold, so **not**
  flagged (confirms the fix; under the original 4-character threshold this pair would have wrongly
  flagged).

**General prefix/suffix vectors** (arbitrary, self-consistent strings — no real address library needed,
since this task's own algorithm is chain-agnostic literal string comparison):
- Prefix match (≥6 shared leading, different trailing): `"ABCDEF0000"` vs. `"ABCDEF9999"`.
- Suffix match (≥4 shared trailing, different leading): `"0000WXYZ"` vs. `"9999WXYZ"`.
- No resemblance: `"ABCDEF0000"` vs. `"999999XXXX"`.
- Exact match: `"ABCDEF0000"` vs. `"ABCDEF0000"` (same string).
- Case-differing "same" address: `"ABCDEF0000"` (candidate) vs. `"abcdef0000"` (previous) — not an
  exact match (case-sensitive), and shares enough characters to be flagged.
- Boundary (prefix): exactly 5 shared leading chars (`"ABCDE00000"` vs `"ABCDE99999"`) → not flagged;
  exactly 6 (`"ABCDEF0000"` vs `"ABCDEF9999"`) → flagged.
- Boundary (suffix): exactly 3 shared trailing chars (`"000WXY"` vs `"999WXY"`) → not flagged; exactly 4
  (`"0000WXYZ"` vs `"9999WXYZ"`) → flagged.
- Short candidate (shorter than both thresholds): candidate `"AB"` against a normal-length previous
  address — never flagged, never throws.
- Short previous address in the history collection: a normal-length candidate against a history
  containing `"AB"` — never flagged (for that entry), never throws.

## Unit tests required

**`AddressPoisoningDetectorTest`** (plain JUnit, no mocks — pure logic):

- `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` (named) — AC1.
- `doesNotFlagAnUnrelatedCandidate` (AC2).
- `doesNotFlagAnExactMatch` (AC3).
- `returnsEmptyForANullPreviouslySeenAddressesCollection` (AC4).
- `returnsEmptyForAnEmptyPreviouslySeenAddressesCollection` (AC4).
- `returnsEmptyForANullCandidateAddress` (AC4).
- `skipsANullElementInThePreviouslySeenAddressesCollection` (AC4).
- `flagsAPrefixOnlyMatch` (AC1).
- `flagsASuffixOnlyMatch` (AC1).
- `flagsACaseDifferingAddressAsResemblingNotExactlyMatching` (AC5).
- `doesNotFlagAtFiveMatchingLeadingCharactersButFlagsAtSix` (AC1 boundary).
- `doesNotFlagAtThreeMatchingTrailingCharactersButFlagsAtFour` (AC1 boundary).
- `doesNotFlagOrThrowForAShortCandidateAddress` (AC7).
- `doesNotFlagOrThrowForAShortPreviouslySeenAddress` (AC7).
- `doesNotFlagTwoEvmAddressesSharingOnlyTheZeroXPrefixAndTwoHexDigits` (AC8 — the Amendment #1
  regression guard).

No integration test — no persistence, no Docker dependency, consistent with `AddressValidator`'s (T12)
own precedent.

## Execution order

1. `AddressPoisoningDetector.java` (+ `AddressPoisoningDetectorTest.java`) — a single, self-contained
   file with no dependency on anything else new in this task.
2. `mvn -pl services/crypto test-compile` then targeted `mvn -pl services/crypto test -Dtest=AddressPoisoningDetectorTest`,
   then a full `mvn -pl services/crypto -am test` regression pass.
