package com.themistra.crypto.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The named tests from package.md §8 (`shouldValidateEip55ChecksumForEvmAddresses`,
 * `shouldValidateBase58ChecksumForTronAddresses`), AC1-AC9. All test vectors were computed and
 * verified by direct execution against the real, pinned libraries (web3j 6.0.0 / trident 1.0.0) in
 * Phase 5 - not hand-typed from memory. */
class AddressValidatorTest {

    private final AddressValidator validator = new AddressValidator();

    // --- EVM / EIP-55 ---

    private static final String CHECKSUMMED = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";
    private static final String ALL_LOWERCASE = "0x5aeda56215b167893e80b4fe645ba6d5bab767de";
    private static final String ALL_UPPERCASE = "0x5AEDA56215B167893E80B4FE645BA6D5BAB767DE";
    private static final String CORRUPTED_CHECKSUM = "0x5aEDA56215b167893e80B4fE645BA6d5Bab767DE";

    @Test
    void shouldValidateEip55ChecksumForEvmAddresses() {
        assertThat(validator.isValidEvmAddress(CHECKSUMMED)).isTrue();
    }

    @Test
    void rejectsAnAllLowercaseEvmAddress() {
        // AC3, Amendment #1: strict EIP-55 - an unchecksummed address is not accepted as a fallback.
        assertThat(validator.isValidEvmAddress(ALL_LOWERCASE)).isFalse();
    }

    @Test
    void rejectsAnAllUppercaseEvmAddress() {
        assertThat(validator.isValidEvmAddress(ALL_UPPERCASE)).isFalse();
    }

    @Test
    void rejectsAMixedCaseEvmAddressWithACorruptedChecksum() {
        // AC4: the single most important EIP-55 negative case - a structurally valid, mixed-case
        // address whose checksum bits are wrong.
        assertThat(validator.isValidEvmAddress(CORRUPTED_CHECKSUM)).isFalse();
    }

    @Test
    void rejectsAnUppercaseZeroXPrefix() {
        // Amendment #4: literal lowercase "0x" only - "0X..." is rejected outright by the regex,
        // never reaching the checksum comparison.
        String uppercasePrefix = "0X" + CHECKSUMMED.substring(2);
        assertThat(validator.isValidEvmAddress(uppercasePrefix)).isFalse();
    }

    @Test
    void rejectsAWhitespacePaddedEvmAddress() {
        // Amendment #7: the fully-anchored regex never trims.
        assertThat(validator.isValidEvmAddress(" " + CHECKSUMMED)).isFalse();
        assertThat(validator.isValidEvmAddress(CHECKSUMMED + " ")).isFalse();
    }

    @Test
    void rejectsAnEvmAddressOfTheWrongLength() {
        String tooShort = CHECKSUMMED.substring(0, CHECKSUMMED.length() - 1);
        assertThat(validator.isValidEvmAddress(tooShort)).isFalse();
    }

    @Test
    void rejectsAnEvmAddressWithANonHexCharacter() {
        String nonHex = CHECKSUMMED.substring(0, CHECKSUMMED.length() - 1) + "G";
        assertThat(validator.isValidEvmAddress(nonHex)).isFalse();
    }

    @Test
    void rejectsAnEvmAddressWithAVisuallyConfusableNonHexCharacter() {
        // Phase 11 Gap 6: O, I, l are common human transcription errors and are outside the hex
        // alphabet - more realistic than an arbitrary non-hex character like G.
        String withO = CHECKSUMMED.substring(0, CHECKSUMMED.length() - 1) + "O";
        String withCapitalI = CHECKSUMMED.substring(0, CHECKSUMMED.length() - 1) + "I";
        String withLowercaseL = CHECKSUMMED.substring(0, CHECKSUMMED.length() - 1) + "l";
        assertThat(validator.isValidEvmAddress(withO)).isFalse();
        assertThat(validator.isValidEvmAddress(withCapitalI)).isFalse();
        assertThat(validator.isValidEvmAddress(withLowercaseL)).isFalse();
    }

    @Test
    void rejectsAnEvmAddressThatIsTooLong() {
        // Phase 11 Gap 2: a regression that accidentally relaxed the regex's upper bound (e.g. to
        // {40,}) would not be caught by the too-short test alone.
        assertThat(validator.isValidEvmAddress(CHECKSUMMED + "0")).isFalse();
    }

    @Test
    void rejectsAnEvmAddressMissingTheZeroXPrefix() {
        // Phase 11 Gap 3: AC2 explicitly names a missing 0x prefix as a structurally malformed case.
        assertThat(validator.isValidEvmAddress(CHECKSUMMED.substring(2))).isFalse();
    }

    @Test
    void rejectsAnEmptyEvmAddress() {
        // Phase 11 Gap 4: symmetry with the Tron empty-string test.
        assertThat(validator.isValidEvmAddress("")).isFalse();
    }

    @Test
    void rejectsANullEvmAddress() {
        assertThat(validator.isValidEvmAddress(null)).isFalse();
    }

    @Test
    void acceptsTheAllZeroEvmAddress() {
        // Phase 9 (correcting Kimi Phase 8 Issue 7's mistaken premise, confirmed by direct execution):
        // the all-zero address has no alphabetic hex characters at all, so EIP-55's case-checksum has
        // nothing to act on - it is its own checksummed form and is correctly ACCEPTED, not rejected,
        // regardless of the strict all-lowercase-rejection policy (which only bites addresses that
        // contain letters).
        assertThat(validator.isValidEvmAddress("0x0000000000000000000000000000000000000000")).isTrue();
    }

    // --- Tron / Base58Check ---

    private static final String VALID_TRON = "TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj";
    private static final String ILLEGAL_CHARACTER_TRON = "0A4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj";
    private static final String WRONG_CHECKSUM_TRON = "TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnA";
    private static final String WRONG_PREFIX_TRON = "16L5yRNPTuciSgXGHqYwn9N6NeoKqopAu";
    // Phase 11 Gap 1: Base58Check-valid, correct 0x41 prefix, but decodes to 20 bytes, not 21 - the
    // wrong-length branch of AC7, distinct from the wrong-prefix-byte branch above.
    private static final String WRONG_DECODED_LENGTH_TRON = "6vi8VnGRpPtZNSKPQ1ZgkiCSbvndcvgHH";

    @Test
    void shouldValidateBase58ChecksumForTronAddresses() {
        assertThat(validator.isValidTronAddress(VALID_TRON)).isTrue();
    }

    @Test
    void rejectsATronAddressWithAnIllegalCharacter() {
        assertThat(validator.isValidTronAddress(ILLEGAL_CHARACTER_TRON)).isFalse();
    }

    @Test
    void rejectsATronAddressWithAWrongChecksum() {
        assertThat(validator.isValidTronAddress(WRONG_CHECKSUM_TRON)).isFalse();
    }

    @Test
    void rejectsABase58CheckValidStringWithTheWrongPrefixByte() {
        // AC7: valid Base58Check, decodes to 21 bytes, but the prefix byte is 0x00, not Tron's 0x41 -
        // e.g. the kind of confusion a differently-prefixed Base58Check format (a different network's
        // address) could cause if only the checksum were checked.
        assertThat(validator.isValidTronAddress(WRONG_PREFIX_TRON)).isFalse();
    }

    @Test
    void rejectsABase58CheckValidStringWithTheWrongDecodedLength() {
        assertThat(validator.isValidTronAddress(WRONG_DECODED_LENGTH_TRON)).isFalse();
    }

    @Test
    void rejectsAnOversizedTronAddress() {
        assertThat(validator.isValidTronAddress("A".repeat(65))).isFalse();
    }

    @Test
    void rejectsATronAddressAtTheMaximumLength() {
        // Phase 11 Gap 5: the exact boundary (64 characters, all valid Base58 alphabet) - a fencepost
        // error (< vs <=) in the length check would only be caught here, not by the 65-character case.
        assertThat(validator.isValidTronAddress("A".repeat(64))).isFalse();
    }

    @Test
    void rejectsANullTronAddress() {
        assertThat(validator.isValidTronAddress(null)).isFalse();
    }

    @Test
    void rejectsAnEmptyTronAddress() {
        // Phase 9 (Kimi Phase 8 Issue 4): locks in the fix for the confirmed NegativeArraySizeException
        // bug - empty input must return false cleanly, not throw.
        assertThat(validator.isValidTronAddress("")).isFalse();
    }

    @Test
    void rejectsAOneCharacterTronAddress() {
        assertThat(validator.isValidTronAddress("A")).isFalse();
    }

    @Test
    void rejectsAFourCharacterTronAddress() {
        // The exact boundary of the confirmed library bug (inputs of length <= 4 threw
        // NegativeArraySizeException before the Phase 9 fix).
        assertThat(validator.isValidTronAddress("AAAA")).isFalse();
    }

    @Test
    void rejectsATronAddressJustBelowTheMinimumLength() {
        assertThat(validator.isValidTronAddress("A".repeat(24))).isFalse();
    }

    @Test
    void rejectsATronAddressAtTheMinimumLength() {
        // Phase 11 Gap 5: the exact boundary (25 characters) - not a valid address, but must not
        // throw, and a fencepost error in the length check would only be caught here.
        assertThat(validator.isValidTronAddress("A".repeat(25))).isFalse();
    }
}
