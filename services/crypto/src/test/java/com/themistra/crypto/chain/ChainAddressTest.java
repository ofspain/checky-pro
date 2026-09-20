package com.themistra.crypto.chain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An address accepted for the wrong chain family produces a watch that can never match, which
 * presents to a merchant as a payment that never arrived — the hardest class of bug to diagnose
 * from a support ticket. These are the cases that stop it at the door.
 */
class ChainAddressTest {

    private static final ChainId ETHEREUM = ChainId.evm(1);
    private static final ChainId TRON = new ChainId(ChainId.TRON, "0x2b6653dc");

    private static final String EVM_CHECKSUMMED = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    private static final String TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    @Test
    @DisplayName("a checksummed EVM address is valid and normalises to lowercase")
    void evmChecksummed() {
        assertThat(ChainAddress.isValid(ETHEREUM, EVM_CHECKSUMMED)).isTrue();
        assertThat(ChainAddress.normalise(ETHEREUM, EVM_CHECKSUMMED))
                .isEqualTo(EVM_CHECKSUMMED.toLowerCase());
    }

    @Test
    @DisplayName("casing does not change what an address matches")
    void casingDoesNotAffectMatching() {
        assertThat(ChainAddress.normalise(ETHEREUM, EVM_CHECKSUMMED))
                .isEqualTo(ChainAddress.normalise(ETHEREUM, EVM_CHECKSUMMED.toLowerCase()))
                .isEqualTo(ChainAddress.normalise(ETHEREUM, EVM_CHECKSUMMED.toUpperCase().replace("0X", "0x")));
    }

    @Test
    @DisplayName("an EVM address of the wrong length is rejected")
    void evmWrongLength() {
        assertThat(ChainAddress.isValid(ETHEREUM, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB")).isFalse();
        assertThat(ChainAddress.isValid(ETHEREUM, EVM_CHECKSUMMED + "00")).isFalse();
    }

    @Test
    @DisplayName("an EVM address without the hex prefix is rejected")
    void evmMissingPrefix() {
        assertThat(ChainAddress.isValid(ETHEREUM, EVM_CHECKSUMMED.substring(2))).isFalse();
    }

    @Test
    @DisplayName("a non-hex EVM address is rejected")
    void evmNonHex() {
        assertThat(ChainAddress.isValid(ETHEREUM, "0xZZb86991c6218b36c1d19D4a2e9Eb0cE3606eB48")).isFalse();
    }

    @Test
    @DisplayName("a Tron address on an EVM chain is rejected")
    void tronAddressOnEvmChain() {
        assertThat(ChainAddress.isValid(ETHEREUM, TRON_ADDRESS)).isFalse();
    }

    @Test
    @DisplayName("an EVM address on a Tron chain is rejected")
    void evmAddressOnTronChain() {
        assertThat(ChainAddress.isValid(TRON, EVM_CHECKSUMMED)).isFalse();
    }

    @Test
    @DisplayName("a Tron address is valid and preserved exactly")
    void tronAddress() {
        assertThat(ChainAddress.isValid(TRON, TRON_ADDRESS)).isTrue();
        // Base58 is case-significant: lowercasing a Tron address changes which address it is.
        assertThat(ChainAddress.normalise(TRON, TRON_ADDRESS)).isEqualTo(TRON_ADDRESS);
    }

    @Test
    @DisplayName("a Tron address containing an ambiguous Base58 character is rejected")
    void tronAmbiguousCharacter() {
        assertThat(ChainAddress.isValid(TRON, "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjL0j6")).isFalse();
    }

    @Test
    @DisplayName("surrounding whitespace does not make an address invalid")
    void trimsWhitespace() {
        assertThat(ChainAddress.normalise(ETHEREUM, "  " + EVM_CHECKSUMMED + "  "))
                .isEqualTo(EVM_CHECKSUMMED.toLowerCase());
    }

    @Test
    @DisplayName("null and blank are rejected, not normalised")
    void rejectsNullAndBlank() {
        assertThat(ChainAddress.isValid(ETHEREUM, null)).isFalse();
        assertThat(ChainAddress.isValid(ETHEREUM, "")).isFalse();
        assertThatThrownBy(() -> ChainAddress.normalise(ETHEREUM, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a namespace with no known address form is rejected")
    void unknownNamespace() {
        assertThat(ChainAddress.isValid(new ChainId("solana", "mainnet"), EVM_CHECKSUMMED)).isFalse();
    }
}
