package com.themistra.crypto.chain.evm;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.FinalityStatus;
import com.themistra.crypto.chain.TokenInfo;
import com.themistra.crypto.chain.TxObservation;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One EVM provider, behind the common adapter interface (ARCHITECTURE §3.4).
 *
 * <p>This class is the only place web3j appears. It answers questions and decides nothing: whether
 * an answer is true is settled by comparing several of these in the quorum layer (§6.1).
 *
 * <p>Network failures are allowed to propagate as unchecked exceptions. The quorum reader converts
 * them into a recorded provider failure, which is not a vote in either direction — so one provider
 * going down cannot deny a read that the other two agree on.
 */
public class EvmChainAdapter implements ChainAdapter {

    /** {@code decimals()} — the ERC-20 selector, keccak256 of the signature, first four bytes. */
    private static final String DECIMALS_SELECTOR = "0x313ce567";

    private final String providerLabel;
    private final ChainId chainId;
    private final Web3j web3j;

    /** Token decimals never change for a deployed contract, so one lookup per token is enough. */
    private final Map<String, Integer> decimalsCache = new ConcurrentHashMap<>();

    public EvmChainAdapter(String providerLabel, ChainId chainId, Web3j web3j) {
        if (providerLabel == null || providerLabel.isBlank()) {
            throw new IllegalArgumentException("providerLabel is required");
        }
        if (chainId == null || !chainId.isEvm()) {
            throw new IllegalArgumentException("EvmChainAdapter requires an eip155 chain, got: " + chainId);
        }
        this.providerLabel = providerLabel;
        this.chainId = chainId;
        this.web3j = web3j;
    }

    @Override
    public String providerLabel() {
        return providerLabel;
    }

    @Override
    public ChainId chainId() {
        return chainId;
    }

    /**
     * The ERC-20 transfer carried by this transaction, if any.
     *
     * <p>A reverted transaction returns empty: it moved no money, and reporting it as an
     * observation would let the payment state machine advance on a payment that never happened.
     */
    @Override
    public Optional<TxObservation> getTx(String txHash) {
        Optional<TransactionReceipt> maybeReceipt = receipt(txHash);
        if (maybeReceipt.isEmpty()) {
            return Optional.empty();
        }
        TransactionReceipt receipt = maybeReceipt.get();
        if (!receipt.isStatusOK()) {
            return Optional.empty();
        }

        return receipt.getLogs().stream()
                .map(log -> Erc20TransferDecoder.decode(log.getAddress(), log.getTopics(), log.getData()))
                .flatMap(Optional::stream)
                .findFirst()
                .map(transfer -> toObservation(receipt, transfer));
    }

    @Override
    public Optional<TokenInfo> getTokenInfo(String tokenAddress) {
        return decimalsOf(tokenAddress)
                .map(decimals -> new TokenInfo(chainId, checksummed(tokenAddress), decimals, null));
    }

    /**
     * Depth and finality for a transaction.
     *
     * <p>Ethereum finality is the beacon-chain finalized checkpoint, not a confirmation count
     * (§6.2), so this compares the transaction's block against the {@code finalized} block tag
     * rather than applying a threshold.
     */
    @Override
    public Optional<FinalityStatus> getFinalityStatus(String txHash) {
        Optional<TransactionReceipt> maybeReceipt = receipt(txHash);
        if (maybeReceipt.isEmpty()) {
            return Optional.empty();
        }
        BigInteger txBlock = maybeReceipt.get().getBlockNumber();

        try {
            BigInteger head = web3j.ethBlockNumber().send().getBlockNumber();
            long confirmations = Math.max(0L, head.subtract(txBlock).longValueExact() + 1L);

            EthBlock.Block finalizedBlock = web3j
                    .ethGetBlockByNumber(DefaultBlockParameterName.FINALIZED, false)
                    .send()
                    .getBlock();

            boolean finalized = finalizedBlock != null
                    && finalizedBlock.getNumber().compareTo(txBlock) >= 0;

            return Optional.of(new FinalityStatus(confirmations, finalized));
        } catch (IOException e) {
            throw new ProviderUnavailableException(providerLabel, "finality lookup failed", e);
        }
    }

    private TxObservation toObservation(TransactionReceipt receipt, Erc20Transfer transfer) {
        int decimals = decimalsOf(transfer.tokenAddress()).orElseThrow(() ->
                new ProviderUnavailableException(providerLabel,
                        "could not read decimals for token " + transfer.tokenAddress(), null));

        return new TxObservation(
                chainId,
                receipt.getTransactionHash(),
                receipt.getBlockNumber().longValueExact(),
                receipt.getBlockHash(),
                checksummed(transfer.from()),
                checksummed(transfer.to()),
                checksummed(transfer.tokenAddress()),
                transfer.value(),
                decimals);
    }

    private Optional<TransactionReceipt> receipt(String txHash) {
        try {
            return web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
        } catch (IOException e) {
            throw new ProviderUnavailableException(providerLabel, "receipt lookup failed", e);
        }
    }

    private Optional<Integer> decimalsOf(String tokenAddress) {
        String key = tokenAddress.toLowerCase();
        Integer cached = decimalsCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            EthCall call = web3j.ethCall(
                            Transaction.createEthCallTransaction(null, tokenAddress, DECIMALS_SELECTOR),
                            DefaultBlockParameterName.LATEST)
                    .send();
            if (call.isReverted()) {
                return Optional.empty();
            }
            return Erc20TransferDecoder.uint256(call.getValue())
                    .map(BigInteger::intValueExact)
                    .map(decimals -> {
                        decimalsCache.put(key, decimals);
                        return decimals;
                    });
        } catch (IOException e) {
            throw new ProviderUnavailableException(providerLabel, "decimals lookup failed", e);
        }
    }

    /** EIP-55 checksum form, required on every EVM address we publish (§6.3). */
    private static String checksummed(String address) {
        return Keys.toChecksumAddress(address);
    }
}
