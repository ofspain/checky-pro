package com.themistra.crypto.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 11 Gaps 2/11 — AC2, and the documented `Chain.valueOf(String)` bridge to T03's config. */
class ChainTest {

    @Test
    void hasExactlyEthereumAndTron() {
        assertThat(Chain.values()).containsExactlyInAnyOrder(Chain.ETHEREUM, Chain.TRON);
    }

    @Test
    void valueOfBridgesFromT03sConfigStringSpellingsForBothChains() {
        // T03's ProviderProperties/FinalityProperties constrain config values to
        // @Pattern(regexp = "ETHEREUM|TRON") - proving Chain.valueOf succeeds for exactly those
        // two spellings documents and locks in the bridge Chain's own Javadoc describes.
        assertThat(Chain.valueOf("ETHEREUM")).isEqualTo(Chain.ETHEREUM);
        assertThat(Chain.valueOf("TRON")).isEqualTo(Chain.TRON);
    }
}
