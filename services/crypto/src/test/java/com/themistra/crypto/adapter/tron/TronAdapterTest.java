package com.themistra.crypto.adapter.tron;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.themistra.crypto.adapter.ObservationSink;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TokenInfo;
import com.themistra.crypto.adapter.model.TxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.NodeType;
import org.tron.trident.core.contract.Contract;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Chain.Transaction.Contract.ContractType;
import org.tron.trident.proto.Contract.TransferContract;
import org.tron.trident.proto.Response.TransactionExtention;
import org.tron.trident.proto.Response.TransactionInfo;
import org.tron.trident.proto.Response.TransactionInfoList;
import org.tron.trident.utils.Base58Check;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** AC1-AC11 (frozen brief), plus Phase 9 review-resolution fixes. No real gRPC call anywhere -
 * {@link ApiWrapper} and the scheduler are both mocked (AC6). All protobuf fixtures below are built
 * via trident's own real builders/constructors, not mocked - verified against direct bytecode
 * inspection of Trc20Contract/Contract/ApiWrapper during Phase 6-9 (see class Javadoc on
 * {@link TronAdapter}). */
@ExtendWith(MockitoExtension.class)
class TronAdapterTest {

    private static final String TX_HASH = "abc123";

    private static final byte[] WATCHED_BODY = fill20((byte) 0xAA);
    private static final byte[] SENDER_BODY = fill20((byte) 0xBB);
    private static final byte[] CONTRACT_BODY = fill20((byte) 0xCC);
    private static final byte[] NATIVE_OWNER_21 = prefixed(fill20((byte) 0x11));
    private static final byte[] NATIVE_TO_21 = prefixed(fill20((byte) 0x22));

    private static final String WATCHED_ADDRESS = Base58Check.bytesToBase58(prefixed(WATCHED_BODY));
    private static final String CONTRACT_ADDRESS = Base58Check.bytesToBase58(prefixed(CONTRACT_BODY));

    private static final String TRANSFER_EVENT_TOPIC =
            "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @Mock
    private ApiWrapper apiWrapper;
    @Mock
    private ScheduledExecutorService scheduler;

    private TronAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TronAdapter(apiWrapper, "test-provider", scheduler, Duration.ofSeconds(3));
    }

    // ---------- fixture helpers ----------

    private static byte[] fill20(byte seed) {
        byte[] b = new byte[20];
        java.util.Arrays.fill(b, seed);
        return b;
    }

    private static byte[] prefixed(byte[] body20) {
        byte[] full = new byte[21];
        full[0] = 0x41;
        System.arraycopy(body20, 0, full, 1, 20);
        return full;
    }

    private static ByteString paddedTopic(byte[] body20) {
        byte[] padded = new byte[32];
        System.arraycopy(body20, 0, padded, 12, 20);
        return ByteString.copyFrom(padded);
    }

    private static ByteString topicOf(String hex64) {
        return ByteString.copyFrom(org.tron.trident.core.utils.ByteArray.fromHexString(hex64));
    }

    private static TransactionInfo.Log transferLog(byte[] tokenContractBody20, byte[] fromBody20,
                                                     byte[] toBody20, long value) {
        return TransactionInfo.Log.newBuilder()
                .setAddress(ByteString.copyFrom(tokenContractBody20))
                .addTopics(topicOf(TRANSFER_EVENT_TOPIC))
                .addTopics(paddedTopic(fromBody20))
                .addTopics(paddedTopic(toBody20))
                .setData(ByteString.copyFrom(java.math.BigInteger.valueOf(value).toByteArray()))
                .build();
    }

    private static TransactionInfo minedInfo(long blockNumber, TransactionInfo.Log... logs) {
        TransactionInfo.Builder builder = TransactionInfo.newBuilder()
                .setId(ByteString.copyFromUtf8(TX_HASH))
                .setBlockNumber(blockNumber);
        for (TransactionInfo.Log log : logs) {
            builder.addLog(log);
        }
        return builder.build();
    }

    private static Transaction nativeTransferTransaction(byte[] ownerRaw21, byte[] toRaw21, long amountSun) {
        TransferContract transfer = TransferContract.newBuilder()
                .setOwnerAddress(ByteString.copyFrom(ownerRaw21))
                .setToAddress(ByteString.copyFrom(toRaw21))
                .setAmount(amountSun)
                .build();
        Transaction.Contract contract = Transaction.Contract.newBuilder()
                .setType(ContractType.TransferContract)
                .setParameter(Any.pack(transfer))
                .build();
        return Transaction.newBuilder()
                .setRawData(Transaction.raw.newBuilder().addContract(contract))
                .build();
    }

    private static Transaction transactionWithContractType(ContractType type) {
        Transaction.Contract contract = Transaction.Contract.newBuilder().setType(type).build();
        return Transaction.newBuilder()
                .setRawData(Transaction.raw.newBuilder().addContract(contract))
                .build();
    }

    private static Transaction transactionWithNoContracts() {
        return Transaction.newBuilder().setRawData(Transaction.raw.newBuilder()).build();
    }

    private static Chain.Block blockAt(long number) {
        Chain.BlockHeader.raw raw = Chain.BlockHeader.raw.newBuilder().setNumber(number).build();
        Chain.BlockHeader header = Chain.BlockHeader.newBuilder().setRawData(raw).build();
        return Chain.Block.newBuilder().setBlockHeader(header).build();
    }

    private static TransactionInfoList infoListOf(TransactionInfo... infos) {
        TransactionInfoList.Builder builder = TransactionInfoList.newBuilder();
        for (TransactionInfo info : infos) {
            builder.addTransactionInfo(info);
        }
        return builder.build();
    }

    private void stubCurrentBlock(long number) throws IllegalException {
        when(apiWrapper.getNowBlock(NodeType.FULL_NODE)).thenReturn(blockAt(number));
    }

    private void stubSolidBlock(long number) throws IllegalException {
        when(apiWrapper.getNowBlock(NodeType.SOLIDITY_NODE)).thenReturn(blockAt(number));
    }

    // ---------- chain ----------

    @Test
    void chainReturnsTron() {
        // Phase 11 Gap 1: AC4 - a direct, unit-level assertion distinct from the config test's
        // indirect reflection-based check.
        assertThat(adapter.chain()).isEqualTo(com.themistra.crypto.adapter.Chain.TRON);
    }

    // ---------- getFinalityStatus ----------

    @Test
    void usesSolidityAndCurrentBlockQueriesNotAConfirmationCount() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(150L);
        stubSolidBlock(120L);

        FinalityStatus status = adapter.getFinalityStatus(TX_HASH);

        assertThat(status.txBlockNumber()).isEqualTo(100L);
        assertThat(status.currentBlockNumber()).isEqualTo(150L);
        assertThat(status.finalizedBlockNumber()).isEqualTo(120L);
        verify(apiWrapper).getNowBlock(NodeType.SOLIDITY_NODE);
    }

    @Test
    void throwsForANotFoundOrPendingTransaction() throws IllegalException {
        // Per the class Javadoc: trident exposes no separate "pending" signal for Tron distinct from
        // "not found" - both collapse to the same IllegalException("TransactionInfo not found: ...")
        // catch, so there is deliberately no separate "pending" variant of this test.
        when(apiWrapper.getTransactionInfoById(TX_HASH))
                .thenThrow(new IllegalException("TransactionInfo not found: " + TX_HASH));

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TX_HASH);
    }

    @Test
    void throwsWhenSolidifiedBlockExceedsCurrentBlock() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(140L);
        stubSolidBlock(150L);

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("150")
                .hasMessageContaining("140");
    }

    @Test
    void throwsWhenTxBlockExceedsCurrentBlock() throws IllegalException {
        // Phase 11 Gap 13: the same class of impossible-state guard as the finalized-vs-current one
        // above, closed in Phase 9's resolution - a provider claiming the transaction's own block is
        // ahead of the chain head it just reported.
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(150L));
        stubCurrentBlock(140L);
        stubSolidBlock(100L);

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("150")
                .hasMessageContaining("140");
    }

    @Test
    void getFinalityStatusPropagatesCurrentBlockTransportFailure() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        when(apiWrapper.getNowBlock(NodeType.FULL_NODE)).thenThrow(new IllegalException("deadline exceeded"));

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getNowBlock")
                .hasCauseInstanceOf(IllegalException.class);
    }

    @Test
    void getFinalityStatusPropagatesSolidityBlockTransportFailure() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(150L);
        when(apiWrapper.getNowBlock(NodeType.SOLIDITY_NODE))
                .thenThrow(new IllegalException("deadline exceeded"));

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getNowBlock")
                .hasCauseInstanceOf(IllegalException.class);
    }

    // ---------- getTx ----------

    @Test
    void getTxReturnsExistsFalseForANotFoundOrPendingTransaction() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH))
                .thenThrow(new IllegalException("TransactionInfo not found: " + TX_HASH));

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isFalse();
        assertThat(result.txHash()).isEqualTo(TX_HASH);
    }

    @Test
    void getTxReturnsFullyPopulatedResultForAFoundTrc20TransferWithAddressesFromTheTransferLog()
            throws IllegalException {
        TransactionInfo info = minedInfo(100L, transferLog(CONTRACT_BODY, SENDER_BODY, WATCHED_BODY, 500L));
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(info);
        stubCurrentBlock(100L);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isTrue();
        assertThat(result.fromAddress()).isEqualTo(Base58Check.bytesToBase58(prefixed(SENDER_BODY)));
        assertThat(result.toAddress()).isEqualTo(WATCHED_ADDRESS);
        assertThat(result.tokenContractAddress()).isEqualTo(CONTRACT_ADDRESS);
        assertThat(result.amount().longValueExact()).isEqualTo(500L);
        assertThat(result.confirmations()).isEqualTo(1);
        verify(apiWrapper, never()).getTransactionById(any());
    }

    @Test
    void getTxUsesFirstTransferLogWhenMultipleArePresent() throws IllegalException {
        // Phase 11 Gap 9: deliberate, Ethereum-mirroring behavior for a direct getTx(txHash) lookup -
        // see the class Javadoc's "Multiple-Transfer-log limitation" precedent from EthereumAdapter.
        TransactionInfo.Log first = transferLog(CONTRACT_BODY, SENDER_BODY, WATCHED_BODY, 111L);
        TransactionInfo.Log second = transferLog(fill20((byte) 0xEE), SENDER_BODY, WATCHED_BODY, 222L);
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L, first, second));
        stubCurrentBlock(100L);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.tokenContractAddress()).isEqualTo(CONTRACT_ADDRESS);
        assertThat(result.amount().longValueExact()).isEqualTo(111L);
    }

    @Test
    void getTxReturnsNativeTrxValueWhenNoTransferLogIsPresent() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH))
                .thenReturn(nativeTransferTransaction(NATIVE_OWNER_21, NATIVE_TO_21, 1_000_000L));

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isTrue();
        assertThat(result.tokenContractAddress()).isNull();
        assertThat(result.amount().longValueExact()).isEqualTo(1_000_000L);
        assertThat(result.fromAddress()).isEqualTo(Base58Check.bytesToBase58(NATIVE_OWNER_21));
        assertThat(result.toAddress()).isEqualTo(Base58Check.bytesToBase58(NATIVE_TO_21));
    }

    @Test
    void getTxReportsExistenceOnlyForANonTransferContractType() throws IllegalException {
        // Amendment #10: TRC-10/other contract types are out of scope for launch.
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH))
                .thenReturn(transactionWithContractType(ContractType.TriggerSmartContract));

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isTrue();
        assertThat(result.fromAddress()).isNull();
        assertThat(result.toAddress()).isNull();
        assertThat(result.tokenContractAddress()).isNull();
        assertThat(result.amount()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    void getTxThrowsWhenTransactionInfoExistsButTransactionDoesNot() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH))
                .thenThrow(new IllegalException("Transaction not found: " + TX_HASH));

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TX_HASH);
    }

    @Test
    void getTxThrowsWhenTransactionHasNoContracts() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH)).thenReturn(transactionWithNoContracts());

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TX_HASH);
    }

    @Test
    void getTxConfirmationsEqualsOneWhenTxBlockEqualsCurrentBlock() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH))
                .thenReturn(nativeTransferTransaction(NATIVE_OWNER_21, NATIVE_TO_21, 1L));

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.confirmations()).isEqualTo(1);
    }

    @Test
    void getTxThrowsWhenCurrentBlockIsEarlierThanTxBlock() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(50L);

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("earlier");
    }

    @Test
    void getTxPropagatesATransportFailureUnchecked() throws IllegalException {
        when(apiWrapper.getTransactionInfoById(TX_HASH))
                .thenThrow(new IllegalException("deadline exceeded"));

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IllegalException.class);
    }

    @Test
    void getTxPropagatesTransactionByIdTransportFailureUnchecked() throws IllegalException {
        // Phase 11 Gap 7: the existing propagates-failure test only covers getTransactionInfoById -
        // this covers the distinct fallback call site (getTransactionById, reached when no Transfer
        // log is present).
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH)).thenThrow(new IllegalException("deadline exceeded"));

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getTransactionById")
                .hasCauseInstanceOf(IllegalException.class);
    }

    @Test
    void getTxDoesNotMatchATransferLogWithFewerThanThreeTopics() throws IllegalException {
        // Phase 9 (Kimi Issue 3): a log with a colliding topic[0] but only 2 topics must not match -
        // falls back to the native-TRX path instead of throwing IndexOutOfBoundsException.
        TransactionInfo.Log shortLog = TransactionInfo.Log.newBuilder()
                .addTopics(topicOf(TRANSFER_EVENT_TOPIC))
                .addTopics(paddedTopic(SENDER_BODY))
                .build();
        when(apiWrapper.getTransactionInfoById(TX_HASH)).thenReturn(minedInfo(100L, shortLog));
        stubCurrentBlock(100L);
        when(apiWrapper.getTransactionById(TX_HASH))
                .thenReturn(nativeTransferTransaction(NATIVE_OWNER_21, NATIVE_TO_21, 42L));

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.amount().longValueExact()).isEqualTo(42L);
        assertThat(result.fromAddress()).isEqualTo(Base58Check.bytesToBase58(NATIVE_OWNER_21));
    }

    // ---------- getTokenInfo ----------

    @Test
    void getTokenInfoDecodesSymbolAndDecimalsFromAMockedTrc20ContractResponse() {
        // symbol() ABI-encodes "USDT" as a dynamic Utf8String; decimals() encodes uint8(6) - same
        // encoding trident's Trc20Contract.symbol()/decimals() decode via FunctionReturnDecoder,
        // verified structurally identical to web3j's own ABI package (Phase 6/10 inspection).
        byte[] symbolEncoded = hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000020"
                        + "0000000000000000000000000000000000000000000000000000000000000004"
                        + "5553445400000000000000000000000000000000000000000000000000000000");
        byte[] decimalsEncoded = hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000006");

        TransactionExtention symbolResponse = TransactionExtention.newBuilder()
                .addConstantResult(ByteString.copyFrom(symbolEncoded))
                .build();
        TransactionExtention decimalsResponse = TransactionExtention.newBuilder()
                .addConstantResult(ByteString.copyFrom(decimalsEncoded))
                .build();

        Contract contract = new Contract(ByteString.copyFrom(prefixed(CONTRACT_BODY)), null,
                ByteString.EMPTY, 0L, "", 1L);
        when(apiWrapper.getContract(CONTRACT_ADDRESS)).thenReturn(contract);
        when(apiWrapper.constantCall(anyString(), anyString(), any()))
                .thenReturn(symbolResponse, decimalsResponse);

        TokenInfo tokenInfo = adapter.getTokenInfo(CONTRACT_ADDRESS);

        assertThat(tokenInfo.contractAddress()).isEqualTo(CONTRACT_ADDRESS);
        assertThat(tokenInfo.symbol()).isEqualTo("USDT");
        assertThat(tokenInfo.decimals()).isEqualTo(6);
    }

    @Test
    void getTokenInfoThrowsWithContractContextWhenTheUnderlyingCallFails() {
        // Phase 9 (Kimi Issues 4+9, merged fix): getContract never throws/returns null itself
        // (verified), so this simulates the failure one level up, at whatever RuntimeException
        // Trc20Contract's own construction/decode chain could produce.
        when(apiWrapper.getContract(CONTRACT_ADDRESS)).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> adapter.getTokenInfo(CONTRACT_ADDRESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CONTRACT_ADDRESS)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void getTokenInfoDecodesTheMaximumUint8DecimalsValue() {
        // Phase 11 Gap 8, revised: Kimi's suggested "decimals() overflows int" premise does not hold
        // - direct bytecode inspection of Trc20Contract$3 (decimals()'s own TypeReference) confirmed
        // its output type is Uint8, not Uint256, so FunctionReturnDecoder can never legitimately
        // produce a decoded value outside 0-255 for it; intValueExact() cannot overflow through any
        // real ABI response. Verified this rather than shipping a test that could never fail as
        // intended. This test instead pins the boundary that IS reachable: decimals() == 255 (the
        // max a real uint8 can carry) still decodes correctly through to TokenInfo.
        byte[] symbolEncoded = hexToBytes(
                "0000000000000000000000000000000000000000000000000000000000000020"
                        + "0000000000000000000000000000000000000000000000000000000000000004"
                        + "5553445400000000000000000000000000000000000000000000000000000000");
        byte[] decimalsEncoded = hexToBytes(
                "00000000000000000000000000000000000000000000000000000000000000ff");

        TransactionExtention symbolResponse = TransactionExtention.newBuilder()
                .addConstantResult(ByteString.copyFrom(symbolEncoded))
                .build();
        TransactionExtention decimalsResponse = TransactionExtention.newBuilder()
                .addConstantResult(ByteString.copyFrom(decimalsEncoded))
                .build();

        Contract contract = new Contract(ByteString.copyFrom(prefixed(CONTRACT_BODY)), null,
                ByteString.EMPTY, 0L, "", 1L);
        when(apiWrapper.getContract(CONTRACT_ADDRESS)).thenReturn(contract);
        when(apiWrapper.constantCall(anyString(), anyString(), any()))
                .thenReturn(symbolResponse, decimalsResponse);

        TokenInfo tokenInfo = adapter.getTokenInfo(CONTRACT_ADDRESS);

        assertThat(tokenInfo.decimals()).isEqualTo(255);
    }

    private static byte[] hexToBytes(String hex) {
        return org.tron.trident.core.utils.ByteArray.fromHexString(hex);
    }

    // ---------- subscribeAddress ----------

    @Test
    @SuppressWarnings("unchecked")
    void logFilterBase58AddressProducesTheExpectedThirtyTwoByteTopic() throws IllegalException {
        // Amendment #1: pins the exact Base58Check -> 32-byte topic conversion with a known pair.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        when(apiWrapper.getTransactionInfoByBlockNum(101L)).thenReturn(infoListOf());
        stubCurrentBlock(101L);

        taskCaptor.getValue().run();

        // No direct filter-object capture exists (trident has no EthFilter equivalent) - the poll's
        // own recipient-matching logic is what actually proves the conversion; exercised end-to-end
        // by pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx below. This test additionally
        // pins the conversion formula itself in isolation:
        byte[] expectedTopic = new byte[32];
        System.arraycopy(WATCHED_BODY, 0, expectedTopic, 12, 20);
        assertThat(paddedTopic(WATCHED_BODY).toByteArray()).isEqualTo(expectedTopic);
    }

    @Test
    @SuppressWarnings("unchecked")
    void blockScanPollHasNoContractAddressRestrictionOnlyTheRecipientTopic() throws IllegalException {
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(WATCHED_ADDRESS, received::add);

        TransactionInfo.Log matched = transferLog(CONTRACT_BODY, SENDER_BODY, WATCHED_BODY, 10L);
        // A second, unrelated token contract's Transfer to the same recipient - proves no
        // contract-address restriction is applied (matches purely on recipient topic).
        TransactionInfo.Log matchedFromDifferentContract =
                transferLog(fill20((byte) 0xDD), SENDER_BODY, WATCHED_BODY, 20L);
        when(apiWrapper.getTransactionInfoByBlockNum(101L))
                .thenReturn(infoListOf(minedInfo(101L, matched, matchedFromDifferentContract)));
        stubCurrentBlock(101L);

        taskCaptor.getValue().run();

        assertThat(received).hasSize(2);
        assertThat(received).extracting(TxResult::tokenContractAddress)
                .containsExactlyInAnyOrder(CONTRACT_ADDRESS, Base58Check.bytesToBase58(prefixed(fill20((byte) 0xDD))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribeAddressCursorInitializesToCurrentBlockNotGenesis() throws IllegalException {
        stubCurrentBlock(999L);
        when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));

        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        verify(apiWrapper).getNowBlock(NodeType.FULL_NODE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribeAddressSchedulesWithFixedDelayUsingTheConfiguredPollInterval() throws IllegalException {
        // Phase 11 Gap 2: the adapter is constructed in setUp() with a 3-second poll interval.
        stubCurrentBlock(100L);
        when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), any()))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));

        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        long expectedMillis = Duration.ofSeconds(3).toMillis();
        verify(scheduler).scheduleWithFixedDelay(
                any(), eq(expectedMillis), eq(expectedMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollSkipsEthGetLogsEntirelyWhenNoNewBlocksExistSinceTheLastPoll() throws IllegalException {
        // Phase 11 Gap 4: the fromBlock > headBlock early-return guard - a regression removing it
        // would issue a block-number query with a reversed/inverted range instead of skipping it.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        // No re-stub of the current block - the chain head hasn't moved since the cursor was seeded.
        taskCaptor.getValue().run();
        taskCaptor.getValue().run();

        verify(apiWrapper, never()).getTransactionInfoByBlockNum(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cursorAdvancesToToBlockAfterARoutinePoll() throws IllegalException {
        // Phase 11 Gap 3: a regression that forgot to advance lastScannedBlock would have every
        // subsequent poll re-scan the same block(s).
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        when(apiWrapper.getTransactionInfoByBlockNum(101L)).thenReturn(infoListOf());
        stubCurrentBlock(101L);
        taskCaptor.getValue().run();

        when(apiWrapper.getTransactionInfoByBlockNum(102L)).thenReturn(infoListOf());
        stubCurrentBlock(102L);
        taskCaptor.getValue().run();

        verify(apiWrapper, org.mockito.Mockito.times(1)).getTransactionInfoByBlockNum(101L);
        verify(apiWrapper).getTransactionInfoByBlockNum(102L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void observationCarriesConfirmationsBasedOnPollHeadBlock() throws IllegalException {
        // Phase 11 Gap 5.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(WATCHED_ADDRESS, received::add);

        TransactionInfo.Log log = transferLog(CONTRACT_BODY, SENDER_BODY, WATCHED_BODY, 10L);
        for (long block = 101L; block <= 110L; block++) {
            when(apiWrapper.getTransactionInfoByBlockNum(block)).thenReturn(infoListOf());
        }
        when(apiWrapper.getTransactionInfoByBlockNum(105L)).thenReturn(infoListOf(minedInfo(105L, log)));
        stubCurrentBlock(110L);

        taskCaptor.getValue().run();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).confirmations()).isEqualTo(6); // 110 - 105 + 1
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollBoundarySurvivesAnInvalidBase58WatchAddress() throws IllegalException {
        // Phase 11 Gap 10: topicForAddress -> ApiWrapper.parseAddress validates Base58Check and
        // throws on invalid input; the poll boundary (Phase 9) must swallow this like any other
        // unexpected failure rather than crash the scheduler.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress("not-a-valid-base58-address", result -> { });

        stubCurrentBlock(101L);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollBoundarySurvivesATransactionInfoWithNoId() throws IllegalException {
        // Phase 11 Gap 14: ByteArray.toHexString(info.getId().toByteArray()) on a default/empty id
        // must not crash the poll loop - the exception boundary (Phase 9) catches it, dropping just
        // that one tick's observations rather than propagating.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        TransactionInfo.Log log = transferLog(CONTRACT_BODY, SENDER_BODY, WATCHED_BODY, 10L);
        TransactionInfo infoWithNoId = TransactionInfo.newBuilder().setBlockNumber(101L).addLog(log).build();
        when(apiWrapper.getTransactionInfoByBlockNum(101L)).thenReturn(infoListOf(infoWithNoId));
        stubCurrentBlock(101L);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancellingTheSubscriptionStopsFurtherPolling() throws IllegalException {
        stubCurrentBlock(100L);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) future);

        Subscription subscription = adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });
        subscription.cancel();

        verify(future).cancel(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx() throws IllegalException {
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(WATCHED_ADDRESS, received::add);

        TransactionInfo.Log log = transferLog(CONTRACT_BODY, SENDER_BODY, WATCHED_BODY, 42L);
        when(apiWrapper.getTransactionInfoByBlockNum(101L)).thenReturn(infoListOf(minedInfo(101L, log)));
        stubCurrentBlock(101L);

        taskCaptor.getValue().run();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).tokenContractAddress()).isEqualTo(CONTRACT_ADDRESS);
        assertThat(received.get(0).amount().longValueExact()).isEqualTo(42L);
        verify(apiWrapper, never()).getTransactionById(any());
        verify(apiWrapper, never()).getTransactionInfoById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void catchUpIsCappedWhenManyBlocksArePending() throws IllegalException {
        // Amendment #5: at most MAX_BLOCKS_PER_POLL (50) blocks scanned in one tick, even when far
        // more are pending; the cursor reflects partial progress, not a jump straight to the head.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        for (long block = 101L; block <= 150L; block++) {
            when(apiWrapper.getTransactionInfoByBlockNum(block)).thenReturn(infoListOf());
        }
        stubCurrentBlock(500L); // far ahead - would be 400 blocks behind without the cap

        taskCaptor.getValue().run();

        verify(apiWrapper).getTransactionInfoByBlockNum(150L); // 101 + 50 - 1
        verify(apiWrapper, never()).getTransactionInfoByBlockNum(151L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollDoesNotPropagateAnUnexpectedFailure() throws IllegalException {
        // Phase 9 (Kimi Issues 2 and 7): scheduleWithFixedDelay would silently and permanently
        // cancel all future executions if the Runnable ever threw - this proves it doesn't.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        when(apiWrapper.getTransactionInfoByBlockNum(101L))
                .thenThrow(new IllegalException("provider hiccup"));
        stubCurrentBlock(101L);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullResponseFromTransactionInfoByBlockNumThrowsANamedExceptionCaughtByThePollBoundary()
            throws IllegalException {
        // Phase 9 (Kimi Issue 6): a null response (never confirmed impossible) must not NPE.
        stubCurrentBlock(100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));
        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        when(apiWrapper.getTransactionInfoByBlockNum(101L)).thenReturn(null);
        stubCurrentBlock(101L);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
    }

    // ---------- close ----------

    @Test
    void closeShutsDownTheSchedulerBeforeClosingApiWrapper() {
        adapter.close();

        var inOrder = org.mockito.Mockito.inOrder(scheduler, apiWrapper);
        inOrder.verify(scheduler).shutdown();
        inOrder.verify(apiWrapper).close();
    }
}
