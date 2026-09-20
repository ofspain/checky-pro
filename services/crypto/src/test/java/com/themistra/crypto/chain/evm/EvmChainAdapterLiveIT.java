package com.themistra.crypto.chain.evm;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.TxObservation;
import com.themistra.crypto.quorum.QuorumPolicy;
import com.themistra.crypto.quorum.QuorumReader;
import com.themistra.crypto.quorum.QuorumResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter against real Ethereum mainnet.
 *
 * <p>Opt-in via {@code CRYPTO_LIVE_IT=1}, and excluded from CI: it depends on third-party
 * endpoints being up, and a flaky network must never redden the build. Everything it exercises is
 * covered deterministically in {@link Erc20TransferDecoderTest} and the quorum tests — this exists
 * to prove the wiring reaches a real chain.
 */
@EnabledIfEnvironmentVariable(named = "CRYPTO_LIVE_IT", matches = "1")
class EvmChainAdapterLiveIT {

    private static final ChainId MAINNET = ChainId.evm(1);
    private static final String PUBLIC_ENDPOINT = "https://ethereum-rpc.publicnode.com";

    @Test
    @DisplayName("reads and decodes a real ERC-20 transfer from mainnet")
    void readsRealMainnetTransfer() {
        ChainAdapter provider = adapter("evm-public", PUBLIC_ENDPOINT);
        TxObservation observation = findRecentTokenTransfer(provider);

        System.out.println("observed: " + observation);

        assertThat(observation.chainId()).isEqualTo(MAINNET);
        assertThat(observation.amount()).isPositive();
        assertThat(observation.decimals()).isBetween(0, 36);
        assertThat(observation.blockNumber()).isPositive();
        assertThat(observation.txHash()).startsWith("0x");
        // EIP-55 checksummed (§6.3) — mixed case, not the lowercase hex the chain returns
        assertThat(observation.toAddress()).isNotEqualTo(observation.toAddress().toLowerCase());
        assertThat(observation.tokenAddress()).isNotEqualTo(observation.tokenAddress().toLowerCase());
    }

    /**
     * Real 2-of-3 across independent providers.
     *
     * <p>Requires {@code CRYPTO_RPC_URLS} — three comma-separated endpoints. Free public endpoints
     * do not survive this call pattern: each observation costs a receipt lookup plus an
     * {@code eth_call} for decimals, and they rate-limit or time out partway through, which the
     * quorum layer correctly reports as provider failure rather than a fact. That is precisely why
     * §6.1 calls for three commercially independent providers.
     */
    @Test
    @DisplayName("three independent providers agree on a real mainnet transfer")
    @EnabledIfEnvironmentVariable(named = "CRYPTO_RPC_URLS", matches = ".+,.+,.+")
    void quorumOverRealProviders() {
        List<String> urls = Arrays.stream(System.getenv("CRYPTO_RPC_URLS").split(",")).map(String::trim).toList();
        List<ChainAdapter> providers = List.of(
                adapter("evm-provider-a", urls.get(0)),
                adapter("evm-provider-b", urls.get(1)),
                adapter("evm-provider-c", urls.get(2)));

        String txHash = findRecentTokenTransfer(providers.getFirst()).txHash();
        QuorumResult result = new QuorumReader(QuorumPolicy.twoOfThree()).establish(providers, txHash);

        result.answers().forEach(answer -> System.out.printf("  %-18s %s%n",
                answer.providerLabel(),
                answer.isFailure() ? "FAILED: " + answer.failure().orElseThrow()
                        : answer.observation().map(TxObservation::amount).map(String::valueOf).orElse("absent")));

        assertThat(result)
                .withFailMessage("expected quorum, got: %s", result)
                .isInstanceOf(QuorumResult.Agreed.class);
    }

    private static ChainAdapter adapter(String label, String url) {
        return new EvmChainAdapter(label, MAINNET, Web3j.build(new HttpService(url)));
    }

    /**
     * Walks back from the head until a block yields a transaction carrying an ERC-20 Transfer.
     *
     * <p>Starts well behind the head deliberately. Providers lag one another by a few blocks, so a
     * freshly-mined transaction reliably produces disagreement — one sees it, another honestly
     * reports it absent. Correct quorum behaviour, useless as a test signal, and a real constraint
     * on the watcher layer: do not run quorum on first sighting.
     */
    private TxObservation findRecentTokenTransfer(ChainAdapter provider) {
        Web3j web3j = Web3j.build(new HttpService(PUBLIC_ENDPOINT));
        try {
            BigInteger head = web3j.ethBlockNumber().send().getBlockNumber();
            for (int back = 200; back < 216; back++) {
                EthBlock.Block block = web3j
                        .ethGetBlockByNumber(
                                DefaultBlockParameter.valueOf(head.subtract(BigInteger.valueOf(back))), true)
                        .send()
                        .getBlock();
                if (block == null) {
                    continue;
                }
                for (EthBlock.TransactionResult<?> tx : block.getTransactions()) {
                    String hash = ((EthBlock.TransactionObject) tx.get()).getHash();
                    Optional<TxObservation> seen = provider.getTx(hash);
                    if (seen.isPresent() && seen.get().amount().signum() > 0) {
                        return seen.get();
                    }
                }
            }
            throw new IllegalStateException("no ERC-20 transfer found in recent blocks");
        } catch (Exception e) {
            throw new IllegalStateException("could not scan recent blocks", e);
        }
    }
}
