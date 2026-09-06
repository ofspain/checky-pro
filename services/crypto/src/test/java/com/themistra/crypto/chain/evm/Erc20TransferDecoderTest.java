package com.themistra.crypto.chain.evm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The decoding failure modes that would silently misattribute or misvalue a payment.
 */
class Erc20TransferDecoderTest {

    private static final String USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    private static final String FROM_TOPIC =
            "0x0000000000000000000000001111111111111111111111111111111111111111";
    private static final String TO_TOPIC =
            "0x0000000000000000000000002222222222222222222222222222222222222222";
    /** 1500000 base units = 1.50 USDC at 6 decimals. */
    private static final String VALUE_DATA =
            "0x000000000000000000000000000000000000000000000000000000000016e360";

    @Test
    @DisplayName("a well-formed transfer decodes to its three values")
    void decodesTransfer() {
        Optional<Erc20Transfer> decoded = Erc20TransferDecoder.decode(
                USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, FROM_TOPIC, TO_TOPIC),
                VALUE_DATA);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().from()).isEqualTo("0x1111111111111111111111111111111111111111");
        assertThat(decoded.get().to()).isEqualTo("0x2222222222222222222222222222222222222222");
        assertThat(decoded.get().value()).isEqualTo(new BigInteger("1500000"));
        assertThat(decoded.get().tokenAddress()).isEqualTo(USDC.toLowerCase());
    }

    @Test
    @DisplayName("a log from another event is not an error, just not ours")
    void ignoresOtherEvents() {
        Optional<Erc20Transfer> decoded = Erc20TransferDecoder.decode(
                USDC,
                List.of("0xdeadbeef00000000000000000000000000000000000000000000000000000000",
                        FROM_TOPIC, TO_TOPIC),
                VALUE_DATA);

        assertThat(decoded).isEmpty();
    }

    @Test
    @DisplayName("an Approval-shaped log with too few topics is rejected")
    void rejectsWrongTopicCount() {
        assertThat(Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, FROM_TOPIC), VALUE_DATA)).isEmpty();
    }

    @Test
    @DisplayName("a truncated address topic yields nothing rather than a wrong address")
    void rejectsTruncatedTopic() {
        assertThat(Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, "0x1111", TO_TOPIC), VALUE_DATA))
                .isEmpty();
    }

    @Test
    @DisplayName("a non-hex topic yields nothing rather than a garbage address")
    void rejectsNonHexTopic() {
        String poisoned = "0x00000000000000000000000011111111111111111111111111111111111111zz";
        assertThat(Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, poisoned, TO_TOPIC), VALUE_DATA))
                .isEmpty();
    }

    @Test
    @DisplayName("a data field of the wrong width is rejected rather than guessed at")
    void rejectsWrongWidthData() {
        assertThat(Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, FROM_TOPIC, TO_TOPIC), "0x16e360"))
                .isEmpty();
    }

    @Test
    @DisplayName("empty data is rejected — it is not a zero transfer")
    void rejectsEmptyData() {
        assertThat(Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, FROM_TOPIC, TO_TOPIC), "0x"))
                .isEmpty();
    }

    @Test
    @DisplayName("a genuine zero-value transfer decodes to zero")
    void decodesZeroValue() {
        String zero = "0x" + "0".repeat(64);
        Optional<Erc20Transfer> decoded = Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, FROM_TOPIC, TO_TOPIC), zero);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().value()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    @DisplayName("an amount beyond 64 bits survives exactly")
    void decodesLargeValue() {
        String max = "0x" + "f".repeat(64);
        Optional<Erc20Transfer> decoded = Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE, FROM_TOPIC, TO_TOPIC), max);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().value())
                .isEqualTo(BigInteger.TWO.pow(256).subtract(BigInteger.ONE))
                .isGreaterThan(BigInteger.valueOf(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("the signature match is case-insensitive")
    void signatureMatchIsCaseInsensitive() {
        assertThat(Erc20TransferDecoder.decode(USDC,
                List.of(Erc20TransferDecoder.TRANSFER_SIGNATURE.toUpperCase().replace("0X", "0x"),
                        FROM_TOPIC, TO_TOPIC),
                VALUE_DATA)).isPresent();
    }
}
