package com.themistra.crypto.adapter.eth;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ChainAdapter;
import com.themistra.crypto.adapter.ObservationSink;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TokenInfo;
import com.themistra.crypto.adapter.model.TxResult;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The first real {@code ChainAdapter} implementation, backed by web3j against a single configured
 * Ethereum provider endpoint (one instance per {@code ProviderProperties} entry — see
 * {@link EthereumAdapterConfig}). See {@code ChainAdapter}'s own class Javadoc for the
 * failure-vs-negative-answer contract this class honors throughout: an unchecked exception means the
 * provider/transport couldn't answer at all; a transaction not observed as mined is a normal
 * {@code TxResult(exists=false, ...)}, never a throw.
 */
public class EthereumAdapter implements ChainAdapter {

    /** keccak256("Transfer(address,address,uint256)") — the standard ERC-20 Transfer event topic. */
    private static final String TRANSFER_EVENT_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final Web3j web3j;
    private final String providerName;
    private final ScheduledExecutorService scheduler;
    private final Duration pollInterval;

    public EthereumAdapter(Web3j web3j, String providerName, ScheduledExecutorService scheduler,
                            Duration pollInterval) {
        this.web3j = web3j;
        this.providerName = providerName;
        this.scheduler = scheduler;
        this.pollInterval = pollInterval;
    }

    @Override
    public Chain chain() {
        return Chain.ETHEREUM;
    }

    @Override
    public TxResult getTx(String txHash) {
        Transaction tx = fetchTransaction(txHash);
        if (tx == null || tx.getBlockNumber() == null) {
            // Not found, or found but still pending/unmined - both are a normal "not yet observed"
            // answer, not an error (Phase 9/frozen-brief amendment #2). This service only reports
            // mined transactions.
            return notObservedResult(txHash);
        }

        TransactionReceipt receipt = fetchReceipt(txHash);
        BigInteger currentBlock = fetchLatestBlockNumber();
        BigInteger txBlock = tx.getBlockNumber();
        int confirmations = currentBlock.subtract(txBlock).add(BigInteger.ONE).intValue();

        Optional<Log> transferLog = receipt == null ? Optional.empty() : findTransferLog(receipt);
        if (transferLog.isPresent()) {
            Log log = transferLog.get();
            String fromAddress = decodeAddressTopic(log.getTopics().get(1));
            String toAddress = decodeAddressTopic(log.getTopics().get(2));
            BigDecimal amount = new BigDecimal(decodeUint256(log.getData()));
            return new TxResult(true, txHash, fromAddress, toAddress, log.getAddress(), amount,
                    confirmations, txBlock.longValue());
        }

        // No decodable Transfer log - report the native transaction value instead (amendment: only
        // Transfer-log-based detection is proactively watched by subscribeAddress, but a direct
        // getTx(txHash) lookup still reports whatever the transaction itself carries).
        BigDecimal nativeAmount = new BigDecimal(tx.getValue());
        return new TxResult(true, txHash, tx.getFrom(), tx.getTo(), null, nativeAmount,
                confirmations, txBlock.longValue());
    }

    @Override
    public TokenInfo getTokenInfo(String contractAddress) {
        String symbol = callErc20StringFunction(contractAddress, "symbol");
        int decimals = callErc20Uint8Function(contractAddress, "decimals");
        return new TokenInfo(contractAddress, symbol, decimals);
    }

    @Override
    public Subscription subscribeAddress(String address, ObservationSink sink) {
        AtomicLong lastScannedBlock = new AtomicLong(fetchLatestBlockNumber().longValue());

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> pollOnce(address, sink, lastScannedBlock),
                pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);

        return () -> future.cancel(false);
    }

    @Override
    public FinalityStatus getFinalityStatus(String txHash) {
        Transaction tx = fetchTransaction(txHash);
        if (tx == null || tx.getBlockNumber() == null) {
            throw new IllegalStateException(
                    "Cannot evaluate finality for a transaction that is not mined: " + txHash);
        }

        long txBlockNumber = tx.getBlockNumber().longValue();
        long currentBlockNumber = fetchLatestBlockNumber().longValue();
        long finalizedBlockNumber = fetchBlockNumber(DefaultBlockParameterName.FINALIZED);

        return new FinalityStatus(txBlockNumber, currentBlockNumber, finalizedBlockNumber);
    }

    private void pollOnce(String address, ObservationSink sink, AtomicLong lastScannedBlock) {
        BigInteger fromBlock = BigInteger.valueOf(lastScannedBlock.get() + 1);
        BigInteger toBlock = fetchLatestBlockNumber();
        if (fromBlock.compareTo(toBlock) > 0) {
            return; // nothing new since the last poll
        }

        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(fromBlock), DefaultBlockParameter.valueOf(toBlock),
                Collections.emptyList()) // no contract-address restriction - see class Javadoc
                .addSingleTopic(TRANSFER_EVENT_TOPIC)
                .addNullTopic()
                .addSingleTopic(topicForAddress(address));

        List<EthLog.LogResult<?>> logs = fetchLogs(filter);
        for (EthLog.LogResult<?> logResult : logs) {
            Log log = (Log) logResult.get();
            TxResult result = getTx(log.getTransactionHash());
            sink.onObservation(result);
        }

        lastScannedBlock.set(toBlock.longValue());
    }

    private TxResult notObservedResult(String txHash) {
        return new TxResult(false, txHash, null, null, null, null, 0, 0L);
    }

    private Optional<Log> findTransferLog(TransactionReceipt receipt) {
        return receipt.getLogs().stream()
                .filter(log -> !log.getTopics().isEmpty() && TRANSFER_EVENT_TOPIC.equalsIgnoreCase(log.getTopics().get(0)))
                .findFirst();
    }

    private String decodeAddressTopic(String topic) {
        Address decoded = (Address) FunctionReturnDecoder.decodeIndexedValue(topic, TypeReference.create(Address.class));
        return decoded.getValue();
    }

    @SuppressWarnings("unchecked")
    private BigInteger decodeUint256(String data) {
        TypeReference<Type> uint256Ref = (TypeReference<Type>) (TypeReference<?>) TypeReference.create(Uint256.class);
        List<Type> decoded = FunctionReturnDecoder.decode(data, Collections.singletonList(uint256Ref));
        return ((Uint256) decoded.get(0)).getValue();
    }

    private String topicForAddress(String address) {
        return "0x" + TypeEncoder.encode(new Address(address));
    }

    @SuppressWarnings("unchecked")
    private String callErc20StringFunction(String contractAddress, String functionName) {
        Function function = new Function(functionName, Collections.emptyList(),
                Collections.singletonList(TypeReference.create(Utf8String.class)));
        String result = ethCall(contractAddress, function);
        List<Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
        return ((Utf8String) decoded.get(0)).getValue();
    }

    @SuppressWarnings("unchecked")
    private int callErc20Uint8Function(String contractAddress, String functionName) {
        Function function = new Function(functionName, Collections.emptyList(),
                Collections.singletonList(TypeReference.create(Uint8.class)));
        String result = ethCall(contractAddress, function);
        List<Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
        return ((Uint8) decoded.get(0)).getValue().intValue();
    }

    private String ethCall(String contractAddress, Function function) {
        String encodedFunction = FunctionEncoder.encode(function);
        org.web3j.protocol.core.methods.request.Transaction callTransaction =
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        null, contractAddress, encodedFunction);
        try {
            return web3j.ethCall(callTransaction, DefaultBlockParameterName.LATEST).send().getValue();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer eth_call for " + contractAddress, e);
        }
    }

    private Transaction fetchTransaction(String txHash) {
        try {
            return web3j.ethGetTransactionByHash(txHash).send().getTransaction().orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer eth_getTransactionByHash for " + txHash, e);
        }
    }

    private TransactionReceipt fetchReceipt(String txHash) {
        try {
            return web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer eth_getTransactionReceipt for " + txHash, e);
        }
    }

    private BigInteger fetchLatestBlockNumber() {
        return BigInteger.valueOf(fetchBlockNumber(DefaultBlockParameterName.LATEST));
    }

    private long fetchBlockNumber(DefaultBlockParameterName tag) {
        try {
            return web3j.ethGetBlockByNumber(tag, false).send().getBlock().getNumber().longValue();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer eth_getBlockByNumber(" + tag.getValue() + ")", e);
        }
    }

    private List<EthLog.LogResult<?>> fetchLogs(EthFilter filter) {
        try {
            return web3j.ethGetLogs(filter).send().getLogs();
        } catch (IOException e) {
            throw new IllegalStateException("Provider " + providerName + " failed to answer eth_getLogs", e);
        }
    }
}
