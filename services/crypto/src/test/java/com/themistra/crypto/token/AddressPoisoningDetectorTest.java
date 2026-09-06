package com.themistra.crypto.token;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The named test from package.md §8 (`shouldFlagAddressPoisoningOnPrefixSuffixSimilarity`), AC1-AC8.
 * All test vectors are arbitrary, self-consistent strings - no real address library is needed, since
 * this task's own algorithm is chain-agnostic literal string comparison (per Phase 5). */
class AddressPoisoningDetectorTest {

    private final AddressPoisoningDetector detector = new AddressPoisoningDetector();

    @Test
    void shouldFlagAddressPoisoningOnPrefixSuffixSimilarity() {
        String candidate = "ABCDEF0000";
        String previous = "ABCDEF9999";

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).contains(previous);
    }

    @Test
    void doesNotFlagAnUnrelatedCandidate() {
        String candidate = "ABCDEF0000";
        String previous = "999999XXXX";

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).isEmpty();
    }

    @Test
    void doesNotFlagAnExactMatch() {
        String address = "ABCDEF0000";

        assertThat(detector.detectPoisoning(address, List.of(address))).isEmpty();
    }

    @Test
    void returnsEmptyForANullPreviouslySeenAddressesCollection() {
        assertThat(detector.detectPoisoning("ABCDEF0000", null)).isEmpty();
    }

    @Test
    void returnsEmptyForAnEmptyPreviouslySeenAddressesCollection() {
        assertThat(detector.detectPoisoning("ABCDEF0000", List.of())).isEmpty();
    }

    @Test
    void returnsEmptyForANullCandidateAddress() {
        assertThat(detector.detectPoisoning(null, List.of("ABCDEF0000"))).isEmpty();
    }

    @Test
    void skipsANullElementInThePreviouslySeenAddressesCollection() {
        List<String> history = Arrays.asList("999999XXXX", null);

        assertThat(detector.detectPoisoning("ABCDEF0000", history)).isEmpty();
    }

    @Test
    void flagsAPrefixOnlyMatch() {
        String candidate = "ABCDEF0000";
        String previous = "ABCDEF9999"; // shares 6 leading chars, differs entirely at the end

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).contains(previous);
    }

    @Test
    void flagsASuffixOnlyMatch() {
        String candidate = "0000WXYZ";
        String previous = "9999WXYZ"; // shares 4 trailing chars, differs entirely at the start

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).contains(previous);
    }

    @Test
    void flagsWhenBothPrefixAndSuffixMatch() {
        // Phase 9 (Kimi Phase 8 Issue 6): documents that the method does not require both - it
        // trivially flags when both happen to match too.
        String candidate = "ABCDEFGHWXYZ";
        String previous = "ABCDEFGH1234WXYZ";

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).contains(previous);
    }

    @Test
    void flagsACaseDifferingAddressAsResemblingNotExactlyMatching() {
        // AC5: case-sensitive comparison - identical characters in a different case are NOT an exact
        // match, and (sharing every character) are flagged as resembling-but-differing.
        String candidate = "ABCDEF0000";
        String previous = "abcdef0000";

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).contains(previous);
    }

    @Test
    void doesNotFlagAtFiveMatchingLeadingCharactersButFlagsAtSix() {
        assertThat(detector.detectPoisoning("ABCDE00000", List.of("ABCDE99999"))).isEmpty();
        assertThat(detector.detectPoisoning("ABCDEF0000", List.of("ABCDEF9999"))).isPresent();
    }

    @Test
    void doesNotFlagAtThreeMatchingTrailingCharactersButFlagsAtFour() {
        assertThat(detector.detectPoisoning("000WXY", List.of("999WXY"))).isEmpty();
        assertThat(detector.detectPoisoning("0000WXYZ", List.of("9999WXYZ"))).isPresent();
    }

    @Test
    void doesNotFlagOrThrowForAShortCandidateAddress() {
        assertThat(detector.detectPoisoning("AB", List.of("ABCDEF0000"))).isEmpty();
    }

    @Test
    void doesNotFlagOrThrowForAShortPreviouslySeenAddress() {
        assertThat(detector.detectPoisoning("ABCDEF0000", List.of("AB"))).isEmpty();
    }

    @Test
    void doesNotFlagOrThrowForAnEmptyCandidateAddress() {
        // Phase 9 (Kimi Phase 8 Issue 4): the most trivial hostile input.
        assertThat(detector.detectPoisoning("", List.of("ABCDEF0000"))).isEmpty();
    }

    @Test
    void returnsEmptyWhenAllPreviouslySeenAddressesAreTooShortToMatch() {
        // Phase 9 (Kimi Phase 8 Issue 7).
        assertThat(detector.detectPoisoning("ABCDEF0000", List.of("A", "AB", "ABC"))).isEmpty();
    }

    @Test
    void returnsOneOfMultipleMatchingPreviouslySeenAddresses() {
        // Phase 9 (Kimi Phase 8 Issue 5): AC6 - the specific match returned is arbitrary when more
        // than one previously-seen address resembles the candidate.
        String candidate = "ABCDEF0000";
        String previousA = "ABCDEF1111";
        String previousB = "ABCDEF2222";

        assertThat(detector.detectPoisoning(candidate, List.of(previousA, previousB)))
                .isIn(java.util.Optional.of(previousA), java.util.Optional.of(previousB));
    }

    @Test
    void doesNotFlagTwoEvmAddressesSharingOnlyTheZeroXPrefixAndTwoHexDigits() {
        // AC8, the regression guard for the Amendment #1 fix (Phase 3 Kimi Issue 1): "0x" + 2 hex
        // digits = 4 total leading characters, below the raised 6-character threshold.
        String candidate = "0xAB1111111111111111111111111111111111111A";
        String previous = "0xAB2222222222222222222222222222222222222B";

        assertThat(detector.detectPoisoning(candidate, List.of(previous))).isEmpty();
    }
}
