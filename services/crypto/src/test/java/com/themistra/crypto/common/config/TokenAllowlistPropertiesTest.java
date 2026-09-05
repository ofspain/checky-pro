package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** T11 — see class Javadoc on {@link TokenAllowlistProperties}. */
class TokenAllowlistPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(TokenAllowlistProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    private static final String PREFIX = "themistra.crypto.token-allowlist.entries[0]";

    private String[] validEntry() {
        return new String[] {
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0x1111111111111111111111111111111111111a",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=6",
                PREFIX + ".version=1",
                PREFIX + ".signature=local-only-unsigned-placeholder"
        };
    }

    @Test
    void bindsAValidEntry() {
        contextRunner.withPropertyValues(validEntry()).run(context -> {
            assertThat(context).hasNotFailed();
            TokenAllowlistProperties props = context.getBean(TokenAllowlistProperties.class);
            assertThat(props.entries()).hasSize(1);
            TokenAllowlistProperties.Entry entry = props.entries().get(0);
            assertThat(entry.chain()).isEqualTo("ETHEREUM");
            assertThat(entry.contractAddress()).isEqualTo("0x1111111111111111111111111111111111111a");
            assertThat(entry.symbol()).isEqualTo("USDT");
            assertThat(entry.decimals()).isEqualTo(6);
            assertThat(entry.version()).isEqualTo(1);
            assertThat(entry.signature()).isEqualTo("local-only-unsigned-placeholder");
        });
    }

    @Test
    void failsWhenEntriesMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenChainIsNotEthereumOrTron() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=SOLANA",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=6",
                PREFIX + ".version=1",
                PREFIX + ".signature=sig"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenDecimalsIsNegative() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=-1",
                PREFIX + ".version=1",
                PREFIX + ".signature=sig"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenDecimalsExceedsThirty() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=31",
                PREFIX + ".version=1",
                PREFIX + ".signature=sig"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenVersionIsNotPositive() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=6",
                PREFIX + ".version=0",
                PREFIX + ".signature=sig"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenContractAddressIsBlank() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=6",
                PREFIX + ".version=1",
                PREFIX + ".signature=sig"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSymbolIsBlank() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=",
                PREFIX + ".decimals=6",
                PREFIX + ".version=1",
                PREFIX + ".signature=sig"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSignatureIsBlank() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=6",
                PREFIX + ".version=1",
                PREFIX + ".signature="
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsOnADuplicateChainContractAddressVersionTuple() {
        contextRunner.withPropertyValues(
                PREFIX + ".chain=ETHEREUM",
                PREFIX + ".contract-address=0xabc",
                PREFIX + ".symbol=USDT",
                PREFIX + ".decimals=6",
                PREFIX + ".version=1",
                PREFIX + ".signature=sig-one",
                "themistra.crypto.token-allowlist.entries[1].chain=ETHEREUM",
                "themistra.crypto.token-allowlist.entries[1].contract-address=0xabc",
                "themistra.crypto.token-allowlist.entries[1].symbol=USDC",
                "themistra.crypto.token-allowlist.entries[1].decimals=6",
                "themistra.crypto.token-allowlist.entries[1].version=1",
                "themistra.crypto.token-allowlist.entries[1].signature=sig-two"
        ).run(context -> assertThat(context).hasFailed());
    }
}
