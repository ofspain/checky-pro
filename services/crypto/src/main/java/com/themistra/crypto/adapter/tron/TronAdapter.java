package com.themistra.crypto.adapter.tron;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ChainAdapter;
import com.themistra.crypto.adapter.ObservationSink;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TokenInfo;
import com.themistra.crypto.adapter.model.TxResult;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.NodeType;
import org.tron.trident.core.contract.Contract;
import org.tron.trident.core.contract.Trc20Contract;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Chain.Transaction.Contract.ContractType;
import org.tron.trident.proto.Contract.TransferContract;
import org.tron.trident.proto.Response.TransactionInfo;
import org.tron.trident.proto.Response.TransactionInfoList;
import org.tron.trident.utils.Base58Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The second real {@code ChainAdapter} implementation, backed by trident against a single configured
 * Tron provider endpoint (one instance per {@code ProviderProperties} entry — see
 * {@link TronAdapterConfig}). See {@code ChainAdapter}'s own class Javadoc for the failure-vs-
 * negative-answer contract this class honors throughout, and {@link com.themistra.crypto.adapter.eth.EthereumAdapter}
 * for the Ethereum sibling this class deliberately mirrors wherever Tron's own chain semantics allow.
 *
 * <p><b>Not-found signaling (Phase 6 finding, resolves Phase 5 Open Item 1).</b> Contrary to the
 * Phase 5 plan's hypothesis (a default/empty protobuf instance), direct bytecode inspection of
 * {@code ApiWrapper.getTransactionById}/{@code getTransactionInfoById} confirmed both throw a checked
 * {@code IllegalException} with a distinguishable message prefix ({@code "Transaction not found: "} /
 * {@code "TransactionInfo not found: "}) when the queried item does not exist. This class matches on
 * that prefix to distinguish "not found" (a normal negative answer) from every other
 * {@code IllegalException}, which is treated as a transport/provider failure and rethrown unchecked.
 *
 * <p><b>No separate "pending" signal (Phase 6 deviation from the frozen brief's amendment #7, flagged
 * per the Phase 6 directive to flag rather than hide a deviation forced by reality).</b> Unlike
 * {@code web3j}'s {@code Transaction}, trident's {@code Chain.Transaction} carries no block-membership
 * field of its own — only {@link TransactionInfo}'s existence signals that a transaction has been
 * packed into a block. This means Tron has no distinct code path for "mined but TransactionInfo not
 * yet indexed" (T06's literal Ethereum precedent, and the scenario amendment #7 was originally written
 * against) versus "genuinely still pending" — trident exposes no query that distinguishes them. Both
 * collapse to the same {@code TxResult(exists=false, ...)} outcome via the same not-found catch on
 * {@link #fetchTransactionInfo}, which is the correct behavior per this task's own AC2/L4 intent even
 * though the mechanism differs from amendment #7's literal text. {@link #getTx} therefore checks
 * {@link TransactionInfo} existence *first* (it is the only reliable mined-vs-not-mined signal), and
 * only fetches the raw {@link Transaction} when the native-TRX fallback path needs it.
 *
 * <p><b>Dual address representations (Phase 6 finding).</b> Tron addresses appear in two distinct raw
 * shapes depending on source: {@link TransferContract}'s {@code ownerAddress}/{@code toAddress} are
 * already full 21-byte Tron-native addresses (0x41 prefix + 20-byte body); TRC-20 event log topics and
 * the log's own {@code address} field are plain EVM-style 20-byte values (Tron's TVM is EVM-bytecode-
 * compatible, so a ported TRC-20 contract emits standard Ethereum-shaped event data with no Tron
 * prefix). {@link #encodeNativeAddress} and {@link #encodeEvmStyleAddress} handle these two cases
 * separately — conflating them would silently corrupt one of the two address sources.
 */
public class TronAdapter implements ChainAdapter, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TronAdapter.class);

    /** keccak256("Transfer(address,address,uint256)") — the standard ERC-20/TRC-20 Transfer event
     * topic; TVM is EVM-bytecode-compatible so a ported TRC-20 contract emits the identical signature
     * hash (reused from EthereumAdapter's own constant, not re-derived). No "0x" prefix here — unlike
     * web3j's JSON-RPC string topics, trident's topics are raw {@code ByteString}s decoded via
     * {@link ByteArray#toHexString}, which never produces one. */
    private static final String TRANSFER_EVENT_TOPIC =
            "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    /** Tron's raw-address prefix byte for mainnet addresses (the "T..." Base58Check addresses all
     * decode to a 21-byte value starting with this). */
    private static final byte ADDRESS_PREFIX = 0x41;

    /** Amendment #5 (frozen brief): caps how many blocks a single poll tick will scan, so a
     * subscription that fell far behind (e.g. after a long pause) does not attempt unbounded
     * catch-up work in one tick. */
    private static final long MAX_BLOCKS_PER_POLL = 50;

    private final ApiWrapper apiWrapper;
    private final String providerName;
    private final ScheduledExecutorService scheduler;
    private final Duration pollInterval;

    public TronAdapter(ApiWrapper apiWrapper, String providerName, ScheduledExecutorService scheduler,
                        Duration pollInterval) {
        this.apiWrapper = apiWrapper;
        this.providerName = providerName;
        this.scheduler = scheduler;
        this.pollInterval = pollInterval;
    }

    @Override
    public Chain chain() {
        return Chain.TRON;
    }

    @Override
    public TxResult getTx(String txHash) {
        TransactionInfo info = fetchTransactionInfo(txHash);
        if (info == null) {
            // Not found, or not yet packed into a block - both are a normal "not yet observed"
            // answer, not an error (L4/AC2). See class Javadoc: trident gives no separate signal to
            // distinguish these two cases for Tron.
            return notObservedResult(txHash);
        }

        long txBlock = info.getBlockNumber();
        long currentBlock = fetchCurrentBlockNumber();
        int confirmations = computeConfirmations(currentBlock, txBlock);

        Optional<TransactionInfo.Log> transferLog = findTransferLog(info);
        if (transferLog.isPresent()) {
            return buildTxResultFromLog(txHash, transferLog.get(), confirmations, txBlock);
        }

        // No decodable Transfer log - amendment #2: fall back to the native TRX transfer contract,
        // scoped to plain TRX transfers only (not TRC-10 or other contract types, amendment #10).
        Transaction tx = fetchTransaction(txHash);
        if (tx == null) {
            // TransactionInfo existed but the raw Transaction doesn't - a genuine provider
            // inconsistency, not a normal negative answer (the tx must exist if it has an info).
            throw new IllegalStateException(
                    "Provider " + providerName + " has TransactionInfo but no Transaction for " + txHash);
        }
        return buildNativeTransferResult(txHash, tx, confirmations, txBlock);
    }

    @Override
    public TokenInfo getTokenInfo(String contractAddress) {
        // Phase 9 (Kimi Issues 4+9, merged): ApiWrapper.getContract never throws or returns null for
        // an unknown address (confirmed via bytecode inspection - it unconditionally wraps whatever
        // the gRPC call returns), so the previously-planned null guard on fetchContract was dead code.
        // Rather than guess at whichever internal failure mode Trc20Contract's own construction or
        // symbol()/decimals() calls might produce for an unknown/non-TRC-20 address, this wraps the
        // whole lookup and converts any unexpected failure into this class's usual named,
        // provider-and-address-scoped IllegalStateException.
        try {
            Contract contract = apiWrapper.getContract(contractAddress);
            // Owner address for a read-only constant call (symbol()/decimals() never touch state or
            // require a funded/real account) - the well-known all-zero Tron address, the standard
            // placeholder for a caller identity that doesn't matter for a constant call.
            Trc20Contract trc20 = new Trc20Contract(contract, zeroAddressBase58(), apiWrapper);
            String symbol = trc20.symbol();
            int decimals = trc20.decimals().intValueExact();
            return new TokenInfo(contractAddress, symbol, decimals);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to read TRC-20 metadata for " + contractAddress, e);
        }
    }

    @Override
    public Subscription subscribeAddress(String address, ObservationSink sink) {
        AtomicLong lastScannedBlock = new AtomicLong(fetchCurrentBlockNumber());

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> pollOnce(address, sink, lastScannedBlock),
                pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);

        return () -> future.cancel(false);
    }

    @Override
    public FinalityStatus getFinalityStatus(String txHash) {
        TransactionInfo info = fetchTransactionInfo(txHash);
        if (info == null) {
            throw new IllegalStateException(
                    "Cannot evaluate finality for a transaction that is not mined: " + txHash);
        }

        long txBlockNumber = info.getBlockNumber();
        long currentBlockNumber = fetchCurrentBlockNumber();
        long finalizedBlockNumber = fetchSolidifiedBlockNumber();
        if (finalizedBlockNumber > currentBlockNumber) {
            // Amendment #4: mirrors EthereumAdapter's Phase 11 Gap 10 guard - getNowBlock() and
            // getNowBlockSolidity() are independent gRPC round trips.
            throw new IllegalStateException(
                    "Provider " + providerName + " reported a solidified block (" + finalizedBlockNumber
                            + ") ahead of its own current block (" + currentBlockNumber + ")");
        }

        return new FinalityStatus(txBlockNumber, currentBlockNumber, finalizedBlockNumber);
    }

    @Override
    public void close() {
        // Phase 9 (Kimi Issue 10): scheduler shuts down first, so no further poll tick can start once
        // apiWrapper.close() runs; try/finally ensures the scheduler is always shut down even if
        // apiWrapper.close() itself throws, rather than leaking its threads.
        try {
            scheduler.shutdown();
        } finally {
            apiWrapper.close();
        }
    }

    private void pollOnce(String address, ObservationSink sink, AtomicLong lastScannedBlock) {
        // Phase 9 (Kimi Issue 2/7): scheduleWithFixedDelay silently and permanently cancels all future
        // executions of this task if it ever throws - one transient RPC failure (or a malformed watch
        // address reaching topicForAddress) must not silently end this subscription's polling forever.
        try {
            pollOnceUnguarded(address, sink, lastScannedBlock);
        } catch (RuntimeException e) {
            logger.error("Provider {} poll tick failed for watch address {} - will retry next tick",
                    providerName, address, e);
        }
    }

    private void pollOnceUnguarded(String address, ObservationSink sink, AtomicLong lastScannedBlock) {
        long fromBlock = lastScannedBlock.get() + 1;
        long headBlock = fetchCurrentBlockNumber();
        if (fromBlock > headBlock) {
            return; // nothing new since the last poll
        }

        // Amendment #5: cap catch-up so a subscription that fell far behind doesn't scan an unbounded
        // number of blocks in one tick.
        long toBlock = Math.min(headBlock, fromBlock + MAX_BLOCKS_PER_POLL - 1);
        String recipientTopic = topicForAddress(address);

        for (long blockNum = fromBlock; blockNum <= toBlock; blockNum++) {
            for (TransactionInfo info : fetchTransactionInfoByBlockNum(blockNum)) {
                for (TransactionInfo.Log log : info.getLogList()) {
                    if (isMatchingTransferLog(log, recipientTopic)) {
                        int confirmations = computeConfirmations(headBlock, info.getBlockNumber());
                        sink.onObservation(buildTxResultFromLog(
                                ByteArray.toHexString(info.getId().toByteArray()), log, confirmations,
                                info.getBlockNumber()));
                    }
                }
            }
        }

        lastScannedBlock.set(toBlock);
    }

    private boolean isMatchingTransferLog(TransactionInfo.Log log, String recipientTopic) {
        return log.getTopicsCount() >= 3
                && TRANSFER_EVENT_TOPIC.equalsIgnoreCase(hex(log.getTopics(0)))
                && recipientTopic.equalsIgnoreCase(hex(log.getTopics(2)));
    }

    private TxResult buildTxResultFromLog(String txHash, TransactionInfo.Log log, int confirmations,
                                           long txBlock) {
        String fromAddress = encodeEvmStyleAddress(log.getTopics(1));
        String toAddress = encodeEvmStyleAddress(log.getTopics(2));
        String tokenContractAddress = encodeEvmStyleAddress(log.getAddress());
        BigDecimal amount = new BigDecimal(new BigInteger(1, log.getData().toByteArray()));
        return new TxResult(true, txHash, fromAddress, toAddress, tokenContractAddress, amount,
                confirmations, txBlock);
    }

    private TxResult buildNativeTransferResult(String txHash, Transaction tx, int confirmations,
                                                long txBlock) {
        if (tx.getRawData().getContractCount() == 0) {
            // Phase 9 (Kimi Issue 5): the protobuf repeated field doesn't structurally guarantee at
            // least one contract, even though every real broadcast transaction carries exactly one -
            // a named, contextual failure beats a bare IndexOutOfBoundsException.
            throw new IllegalStateException(
                    "Provider " + providerName + " returned a Transaction with no contracts for " + txHash);
        }
        Transaction.Contract contract = tx.getRawData().getContract(0);
        if (contract.getType() != ContractType.TransferContract) {
            // Amendment #10: TRC-10 and any other contract type is out of scope - report existence
            // with no fact-bearing fields rather than guessing at a shape this task doesn't cover.
            return new TxResult(true, txHash, null, null, null, BigDecimal.ZERO, confirmations, txBlock);
        }
        TransferContract transfer = unpackTransferContract(contract);
        String fromAddress = encodeNativeAddress(transfer.getOwnerAddress());
        String toAddress = encodeNativeAddress(transfer.getToAddress());
        BigDecimal amount = BigDecimal.valueOf(transfer.getAmount()); // SUN units
        return new TxResult(true, txHash, fromAddress, toAddress, null, amount, confirmations, txBlock);
    }

    private static TransferContract unpackTransferContract(Transaction.Contract contract) {
        try {
            return contract.getParameter().unpack(TransferContract.class);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Malformed TransferContract payload", e);
        }
    }

    private static Optional<TransactionInfo.Log> findTransferLog(TransactionInfo info) {
        // Phase 9 (Kimi Issue 3): matches isMatchingTransferLog's guard exactly - a log whose topic[0]
        // collides with the Transfer signature but carries fewer than 3 topics must not match here
        // either, or buildTxResultFromLog's getTopics(1)/getTopics(2) below throws unguarded.
        return info.getLogList().stream()
                .filter(log -> log.getTopicsCount() >= 3
                        && TRANSFER_EVENT_TOPIC.equalsIgnoreCase(hex(log.getTopics(0))))
                .findFirst();
    }

    private int computeConfirmations(long currentBlock, long txBlock) {
        long difference = currentBlock - txBlock + 1;
        if (difference < 0) {
            throw new IllegalStateException(
                    "Provider " + providerName + " reported a current block (" + currentBlock
                            + ") earlier than the transaction's own block (" + txBlock + ")");
        }
        return Math.toIntExact(difference);
    }

    private TxResult notObservedResult(String txHash) {
        return new TxResult(false, txHash, null, null, null, null, 0, 0L);
    }

    /** Amendment #1: converts a Base58Check watch address into the 32-byte, zero-padded hex topic
     * value TRC-20 Transfer events encode a recipient/sender as. */
    private static String topicForAddress(String base58Address) {
        byte[] raw = ApiWrapper.parseAddress(base58Address).toByteArray(); // 21 bytes: prefix + body
        byte[] body = Arrays.copyOfRange(raw, 1, raw.length); // strip the Tron 0x41 prefix -> 20 bytes
        return "0".repeat(24) + ByteArray.toHexString(body); // 64 hex chars total
    }

    /** Decodes a 21-byte Tron-native address (already prefixed) - for {@link TransferContract}'s own
     * fields, which carry the prefix as part of their raw protobuf bytes. */
    private static String encodeNativeAddress(ByteString rawTronAddress) {
        return Base58Check.bytesToBase58(rawTronAddress.toByteArray());
    }

    /** Decodes a plain 20-byte (or 32-byte zero-padded) EVM-style address with no Tron prefix - for
     * TRC-20 event log topics/addresses, which are TVM/EVM-shaped, not Tron-native. */
    private static String encodeEvmStyleAddress(ByteString evmStyleValue) {
        byte[] bytes = evmStyleValue.toByteArray();
        byte[] last20 = bytes.length > 20 ? Arrays.copyOfRange(bytes, bytes.length - 20, bytes.length) : bytes;
        byte[] rawTronAddress = new byte[21];
        rawTronAddress[0] = ADDRESS_PREFIX;
        System.arraycopy(last20, 0, rawTronAddress, 1, 20);
        return Base58Check.bytesToBase58(rawTronAddress);
    }

    private static String zeroAddressBase58() {
        byte[] zero = new byte[21];
        zero[0] = ADDRESS_PREFIX;
        return Base58Check.bytesToBase58(zero);
    }

    private static String hex(ByteString value) {
        return ByteArray.toHexString(value.toByteArray());
    }

    private Transaction fetchTransaction(String txHash) {
        try {
            return apiWrapper.getTransactionById(txHash);
        } catch (IllegalException e) {
            if (isNotFound(e, "Transaction not found:")) {
                return null;
            }
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer getTransactionById for " + txHash, e);
        }
    }

    private TransactionInfo fetchTransactionInfo(String txHash) {
        try {
            return apiWrapper.getTransactionInfoById(txHash);
        } catch (IllegalException e) {
            if (isNotFound(e, "TransactionInfo not found:")) {
                return null;
            }
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer getTransactionInfoById for " + txHash, e);
        }
    }

    private static boolean isNotFound(IllegalException e, String prefix) {
        return e.getMessage() != null && e.getMessage().startsWith(prefix);
    }

    private List<TransactionInfo> fetchTransactionInfoByBlockNum(long blockNum) {
        try {
            TransactionInfoList response = apiWrapper.getTransactionInfoByBlockNum(blockNum);
            if (response == null) {
                // Phase 9 (Kimi Issue 6): a null response was never confirmed impossible - guard
                // rather than let getTransactionInfoList() NPE with no provider/block context.
                throw new IllegalStateException(
                        "Provider " + providerName + " returned a null response for "
                                + "getTransactionInfoByBlockNum for block " + blockNum);
            }
            return response.getTransactionInfoList();
        } catch (IllegalException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer getTransactionInfoByBlockNum for block "
                            + blockNum, e);
        }
    }

    private long fetchCurrentBlockNumber() {
        return fetchBlockNumber(NodeType.FULL_NODE, "getNowBlock");
    }

    private long fetchSolidifiedBlockNumber() {
        // NodeType.SOLIDITY_NODE routes this same call through trident's solidity-node gRPC stub.
        // The dedicated getNowBlockSolidity() convenience method is deprecated (confirmed via
        // javac -Xlint:deprecation); this is its supported replacement, and it also avoids the
        // separate Response.BlockExtention return type getNowBlockSolidity() has.
        return fetchBlockNumber(NodeType.SOLIDITY_NODE, "getNowBlock(SOLIDITY_NODE)");
    }

    private long fetchBlockNumber(NodeType nodeType, String callName) {
        try {
            return apiWrapper.getNowBlock(nodeType).getBlockHeader().getRawData().getNumber();
        } catch (IllegalException e) {
            throw new IllegalStateException(
                    "Provider " + providerName + " failed to answer " + callName, e);
        }
    }
}
