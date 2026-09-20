package com.themistra.crypto.adapter.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 3, AC4 (L7) — documents, as an executable test, the design decision already recorded
 * in {@link TokenInfo}'s own Javadoc and reaffirmed at the Phase 9 gate (rejecting an
 * {@code equals}/{@code hashCode} override as an anti-pattern for records): two {@code TokenInfo}
 * instances describing the same contract address but different {@code symbol}/{@code decimals} are
 * NOT equal by the record's own generated equality — business code must compare/key by
 * {@code contractAddress()} explicitly, never rely on {@code equals()}/{@code hashCode()}.
 */
class TokenInfoTest {

    @Test
    void recordEqualityIncludesSymbolAndDecimalsNotJustContractAddress() {
        TokenInfo reportedAsUsdt = new TokenInfo("0xtoken", "USDT", 6);
        TokenInfo sameContractDifferentSymbol = new TokenInfo("0xtoken", "FAKE", 18);

        assertThat(reportedAsUsdt).isNotEqualTo(sameContractDifferentSymbol);
    }

    @Test
    void identityComparisonMustUseContractAddressDirectly() {
        TokenInfo reportedAsUsdt = new TokenInfo("0xtoken", "USDT", 6);
        TokenInfo sameContractDifferentSymbol = new TokenInfo("0xtoken", "FAKE", 18);

        assertThat(reportedAsUsdt.contractAddress()).isEqualTo(sameContractDifferentSymbol.contractAddress());
    }
}
