package com.themistra.crypto.quorum;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.FakeChainAdapter;
import com.themistra.crypto.chain.TxObservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the quorum requirements in ARCHITECTURE §6.1 and the corresponding scenarios in
 * openspec/changes/add-chain-event-contracts/specs/chain/tx-events/spec.md.
 */
class QuorumReaderTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC);

    private final QuorumReader reader = new QuorumReader(QuorumPolicy.twoOfThree(), FIXED);

    @Test
    @DisplayName("three providers agreeing yields the fact")
    void allThreeAgree() {
        TxObservation transfer = FakeChainAdapter.transfer();
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", transfer),
                FakeChainAdapter.seeing("evm-provider-b", transfer),
                FakeChainAdapter.seeing("evm-provider-c", transfer));

        QuorumResult result = reader.establish(providers, "0xabc123");

        assertThat(result).isInstanceOf(QuorumResult.Agreed.class);
        assertThat(((QuorumResult.Agreed) result).fact().amount()).isEqualTo(new BigInteger("1500000"));
        assertThat(result.answers()).hasSize(3);
    }

    @Test
    @DisplayName("two agreeing is enough, even with the third dissenting")
    void twoOfThreeIsEnough() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transfer()),
                FakeChainAdapter.seeing("evm-provider-b", FakeChainAdapter.transfer()),
                FakeChainAdapter.blind("evm-provider-c"));

        assertThat(reader.establish(providers, "0xabc123")).isInstanceOf(QuorumResult.Agreed.class);
    }

    @Test
    @DisplayName("an unreachable provider is not a vote — the other two still reach quorum")
    void providerFailureDoesNotBlockQuorum() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transfer()),
                FakeChainAdapter.seeing("evm-provider-b", FakeChainAdapter.transfer()),
                FakeChainAdapter.broken("evm-provider-c", "connection reset"));

        QuorumResult result = reader.establish(providers, "0xabc123");

        assertThat(result).isInstanceOf(QuorumResult.Agreed.class);
        assertThat(result.answers())
                .filteredOn(ProviderAnswer::isFailure)
                .singleElement()
                .satisfies(answer -> assertThat(answer.failure()).contains("connection reset"));
    }

    @Test
    @DisplayName("conflicting amounts never resolve automatically")
    void conflictingAmountsDisagree() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transferOf(new BigInteger("1500000"))),
                FakeChainAdapter.seeing("evm-provider-b", FakeChainAdapter.transferOf(new BigInteger("9900000"))),
                FakeChainAdapter.broken("evm-provider-c", "timeout"));

        QuorumResult result = reader.establish(providers, "0xabc123");

        assertThat(result).isInstanceOf(QuorumResult.Disagreed.class);
        assertThat(((QuorumResult.Disagreed) result).reason()).contains("conflicting");
    }

    @Test
    @DisplayName("a lone provider is never authoritative")
    void singleProviderIsNeverAuthoritative() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transfer()),
                FakeChainAdapter.broken("evm-provider-b", "timeout"),
                FakeChainAdapter.broken("evm-provider-c", "timeout"));

        assertThat(reader.establish(providers, "0xabc123")).isInstanceOf(QuorumResult.Disagreed.class);
    }

    @Test
    @DisplayName("providers agreeing the transaction is absent is itself a quorum")
    void absenceReachesQuorum() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.blind("evm-provider-a"),
                FakeChainAdapter.blind("evm-provider-b"),
                FakeChainAdapter.broken("evm-provider-c", "timeout"));

        assertThat(reader.establish(providers, "0xabc123")).isInstanceOf(QuorumResult.Absent.class);
    }

    @Test
    @DisplayName("the same transfer in different blocks is a disagreement, not a fact")
    void differentBlocksDoNotAgree() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transferInBlock(18_000_000L, "0xblockA")),
                FakeChainAdapter.seeing("evm-provider-b", FakeChainAdapter.transferInBlock(18_000_001L, "0xblockB")),
                FakeChainAdapter.broken("evm-provider-c", "timeout"));

        assertThat(reader.establish(providers, "0xabc123")).isInstanceOf(QuorumResult.Disagreed.class);
    }

    @Test
    @DisplayName("every answer is retained for the observation log, including failures")
    void allAnswersAreRecorded() {
        List<ChainAdapter> providers = List.of(
                FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transfer()),
                FakeChainAdapter.seeing("evm-provider-b", FakeChainAdapter.transfer()),
                FakeChainAdapter.broken("evm-provider-c", "connection reset"));

        QuorumResult result = reader.establish(providers, "0xabc123");

        assertThat(result.answers())
                .extracting(ProviderAnswer::providerLabel)
                .containsExactlyInAnyOrder("evm-provider-a", "evm-provider-b", "evm-provider-c");
        assertThat(result.answers()).allSatisfy(answer ->
                assertThat(answer.observedAt()).isEqualTo(Instant.parse("2026-09-05T10:00:00Z")));
    }

    @Test
    @DisplayName("a threshold below two is rejected outright")
    void thresholdBelowTwoIsRejected() {
        assertThatThrownBy(() -> new QuorumPolicy(3, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single provider is never authoritative");
    }

    @Test
    @DisplayName("too few providers to ever reach the threshold is a programming error")
    void tooFewProvidersIsRejected() {
        List<ChainAdapter> providers = List.of(FakeChainAdapter.seeing("evm-provider-a", FakeChainAdapter.transfer()));

        assertThatThrownBy(() -> reader.establish(providers, "0xabc123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot reach a threshold");
    }
}
