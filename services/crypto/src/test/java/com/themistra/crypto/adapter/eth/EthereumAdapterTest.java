package com.themistra.crypto.adapter.eth;

import com.themistra.crypto.adapter.ObservationSink;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AC1-AC11 (frozen brief). No real network call anywhere - {@link Web3j} and the scheduler are
 * both mocked (AC5). */
@ExtendWith(MockitoExtension.class)
class EthereumAdapterTest {

    private static final String TX_HASH = "0xabc";
    private static final String CONTRACT = "0xtoken";
    private static final String WATCHED_ADDRESS = "0x00000000000000000000000000000000000000aa";
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    @Mock
    private Web3j web3j;
    @Mock
    private ScheduledExecutorService scheduler;

    private EthereumAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EthereumAdapter(web3j, "test-provider", scheduler, Duration.ofSeconds(15));
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private static <T extends Response> Request<?, T> requestReturning(T response) throws IOException {
        Request<?, T> request = mock(Request.class);
        when(request.send()).thenReturn(response);
        return request;
    }

    private static Transaction minedTransaction(long blockNumber, String from, String to, BigInteger value) {
        Transaction tx = mock(Transaction.class);
        when(tx.getBlockNumber()).thenReturn(BigInteger.valueOf(blockNumber));
        // from/to/value are only read on the native-value (no-Transfer-log) path - lenient so tests
        // exercising the log path (or getFinalityStatus, which never reads them at all) don't fail
        // strict-stubbing checks for stubs they legitimately don't need.
        lenient().when(tx.getFrom()).thenReturn(from);
        lenient().when(tx.getTo()).thenReturn(to);
        lenient().when(tx.getValue()).thenReturn(value);
        return tx;
    }

    private void stubTransaction(Transaction tx) throws IOException {
        EthTransaction response = mock(EthTransaction.class);
        when(response.getTransaction()).thenReturn(Optional.ofNullable(tx));
        Request<?, EthTransaction> request = requestReturning(response);
        when(web3j.ethGetTransactionByHash(TX_HASH)).thenReturn((Request) request);
    }

    private void stubReceipt(TransactionReceipt receipt) throws IOException {
        EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
        when(response.getTransactionReceipt()).thenReturn(Optional.ofNullable(receipt));
        Request<?, EthGetTransactionReceipt> request = requestReturning(response);
        when(web3j.ethGetTransactionReceipt(TX_HASH)).thenReturn((Request) request);
    }

    private void stubBlockNumber(DefaultBlockParameterName tag, long number) throws IOException {
        EthBlock.Block block = mock(EthBlock.Block.class);
        when(block.getNumber()).thenReturn(BigInteger.valueOf(number));
        EthBlock response = mock(EthBlock.class);
        when(response.getBlock()).thenReturn(block);
        Request<?, EthBlock> request = requestReturning(response);
        when(web3j.ethGetBlockByNumber(eq(tag), eq(false))).thenReturn((Request) request);
    }

    private static Log transferLog(long blockNumber, String tokenContract, String from, String to, BigInteger value) {
        Log log = mock(Log.class);
        // findTransferLog's stream short-circuits at the first Transfer-topic match, so a "second"
        // log built with this helper in a multi-log test may never have these getters called at
        // all - lenient so that's not a strict-stubbing failure.
        lenient().when(log.getBlockNumber()).thenReturn(BigInteger.valueOf(blockNumber));
        lenient().when(log.getAddress()).thenReturn(tokenContract);
        lenient().when(log.getTransactionHash()).thenReturn(TX_HASH);
        lenient().when(log.getTopics()).thenReturn(List.of(TRANSFER_TOPIC, paddedTopic(from), paddedTopic(to)));
        lenient().when(log.getData()).thenReturn(paddedData(value));
        return log;
    }

    private static String paddedTopic(String address) {
        String hex = address.substring(2).toLowerCase();
        return "0x" + "0".repeat(64 - hex.length()) + hex;
    }

    private static String paddedData(BigInteger value) {
        String hex = value.toString(16);
        return "0x" + "0".repeat(64 - hex.length()) + hex;
    }

    // ---------- getFinalityStatus ----------

    @Test
    void getFinalityStatusUsesFinalizedAndLatestBlockTagsNotAConfirmationCount() throws IOException {
        stubTransaction(minedTransaction(100L, "0xfrom", "0xto", BigInteger.ZERO));
        stubBlockNumber(DefaultBlockParameterName.LATEST, 150L);
        stubBlockNumber(DefaultBlockParameterName.FINALIZED, 120L);

        FinalityStatus status = adapter.getFinalityStatus(TX_HASH);

        assertThat(status.txBlockNumber()).isEqualTo(100L);
        assertThat(status.currentBlockNumber()).isEqualTo(150L);
        assertThat(status.finalizedBlockNumber()).isEqualTo(120L);
        verify(web3j).ethGetBlockByNumber(DefaultBlockParameterName.FINALIZED, false);
    }

    @Test
    void getFinalityStatusThrowsForANotFoundTransaction() throws IOException {
        stubTransaction(null);

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TX_HASH);
    }

    @Test
    void getFinalityStatusThrowsForAPendingUnminedTransaction() throws IOException {
        Transaction pending = mock(Transaction.class);
        when(pending.getBlockNumber()).thenReturn(null);
        stubTransaction(pending);

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- getTx ----------

    @Test
    void getTxReturnsExistsFalseForANotFoundTransaction() throws IOException {
        stubTransaction(null);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isFalse();
        assertThat(result.txHash()).isEqualTo(TX_HASH);
    }

    @Test
    void getTxReturnsExistsFalseForAPendingUnminedTransaction() throws IOException {
        Transaction pending = mock(Transaction.class);
        when(pending.getBlockNumber()).thenReturn(null);
        stubTransaction(pending);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isFalse();
    }

    @Test
    void getTxReturnsFullyPopulatedResultForAFoundErc20TransferWithAddressesFromTheTransferLog() throws IOException {
        // The transaction's own `from` is deliberately different from the log's `from` topic, to
        // prove fromAddress/toAddress are sourced from the log, not the raw transaction (amendment #7).
        String txLevelFrom = "0x00000000000000000000000000000000000000ee";
        String logFrom = "0x000000000000000000000000000000000000001a";
        String logTo = "0x000000000000000000000000000000000000001b";
        stubTransaction(minedTransaction(100L, txLevelFrom, CONTRACT, BigInteger.ZERO));
        Log log = transferLog(100L, CONTRACT, logFrom, logTo, BigInteger.valueOf(500));
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getLogs()).thenReturn(List.of(log));
        stubReceipt(receipt);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isTrue();
        assertThat(result.fromAddress()).isEqualToIgnoringCase(logFrom);
        assertThat(result.toAddress()).isEqualToIgnoringCase(logTo);
        assertThat(result.tokenContractAddress()).isEqualTo(CONTRACT);
        assertThat(result.amount().longValueExact()).isEqualTo(500L);
        assertThat(result.confirmations()).isEqualTo(1);
    }

    @Test
    void getTxUsesTheFirstTransferLogWhenAReceiptContainsMultiple() throws IOException {
        // Documents the limitation recorded in EthereumAdapter's own class Javadoc (Phase 9 Finding 7):
        // a direct getTx call has no way to disambiguate among several Transfer logs in one receipt.
        stubTransaction(minedTransaction(100L, "0xfrom", CONTRACT, BigInteger.ZERO));
        Log first = transferLog(100L, "0xtoken-first", "0xa", "0xb", BigInteger.valueOf(111));
        Log second = transferLog(100L, "0xtoken-second", "0xc", "0xd", BigInteger.valueOf(222));
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getLogs()).thenReturn(List.of(first, second));
        stubReceipt(receipt);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.tokenContractAddress()).isEqualTo("0xtoken-first");
        assertThat(result.amount().longValueExact()).isEqualTo(111L);
    }

    @Test
    void getTxReturnsNativeValueWithNullTokenContractWhenNoTransferLogIsPresent() throws IOException {
        stubTransaction(minedTransaction(100L, "0xfrom", "0xto", BigInteger.valueOf(1_000_000)));
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getLogs()).thenReturn(List.of());
        stubReceipt(receipt);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.tokenContractAddress()).isNull();
        assertThat(result.amount().longValueExact()).isEqualTo(1_000_000L);
        assertThat(result.fromAddress()).isEqualTo("0xfrom");
        assertThat(result.toAddress()).isEqualTo("0xto");
    }

    @Test
    void getTxConfirmationsEqualsOneWhenTxBlockEqualsCurrentBlock() throws IOException {
        stubTransaction(minedTransaction(100L, "0xfrom", "0xto", BigInteger.ZERO));
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getLogs()).thenReturn(List.of());
        stubReceipt(receipt);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.confirmations()).isEqualTo(1);
    }

    @Test
    void getTxThrowsWhenCurrentBlockIsEarlierThanTxBlock() throws IOException {
        stubTransaction(minedTransaction(100L, "0xfrom", "0xto", BigInteger.ZERO));
        stubReceipt(null);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 50L);

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("earlier");
    }

    @Test
    void getTxPropagatesAMockedIoExceptionUnchecked() throws IOException {
        Request<?, EthTransaction> failing = mock(Request.class);
        when(failing.send()).thenThrow(new IOException("connection refused"));
        when(web3j.ethGetTransactionByHash(TX_HASH)).thenReturn((Request) failing);

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    // ---------- getTokenInfo ----------

    @Test
    @SuppressWarnings("unchecked")
    void getTokenInfoDecodesSymbolAndDecimalsFromAMockedAbiResponse() throws IOException {
        // symbol() ABI-encodes "USDT" as a dynamic Utf8String; decimals() encodes uint8(6).
        String symbolEncoded = "0x0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "5553445400000000000000000000000000000000000000000000000000000000";
        String decimalsEncoded = "0x0000000000000000000000000000000000000000000000000000000000000006";

        Request<?, EthCall> symbolRequest = mock(Request.class);
        EthCall symbolResponse = mock(EthCall.class);
        when(symbolResponse.hasError()).thenReturn(false);
        when(symbolResponse.getValue()).thenReturn(symbolEncoded);
        when(symbolRequest.send()).thenReturn(symbolResponse);

        Request<?, EthCall> decimalsRequest = mock(Request.class);
        EthCall decimalsResponse = mock(EthCall.class);
        when(decimalsResponse.hasError()).thenReturn(false);
        when(decimalsResponse.getValue()).thenReturn(decimalsEncoded);
        when(decimalsRequest.send()).thenReturn(decimalsResponse);

        when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST)))
                .thenReturn((Request) symbolRequest, (Request) decimalsRequest);

        var tokenInfo = adapter.getTokenInfo(CONTRACT);

        assertThat(tokenInfo.contractAddress()).isEqualTo(CONTRACT);
        assertThat(tokenInfo.symbol()).isEqualTo("USDT");
        assertThat(tokenInfo.decimals()).isEqualTo(6);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTokenInfoThrowsWithContractContextWhenTheCallIsReverted() throws IOException {
        Request<?, EthCall> request = mock(Request.class);
        EthCall response = mock(EthCall.class);
        when(response.hasError()).thenReturn(true);
        Response.Error error = mock(Response.Error.class);
        when(error.getMessage()).thenReturn("execution reverted");
        when(response.getError()).thenReturn(error);
        when(request.send()).thenReturn(response);
        when(web3j.ethCall(any(), eq(DefaultBlockParameterName.LATEST))).thenReturn((Request) request);

        assertThatThrownBy(() -> adapter.getTokenInfo(CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CONTRACT)
                .hasMessageContaining("execution reverted");
    }

    // ---------- subscribeAddress ----------

    @Test
    @SuppressWarnings("unchecked")
    void subscribeAddressLogFilterHasNoContractAddressRestrictionOnlyTheRecipientTopic() throws IOException {
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(WATCHED_ADDRESS, received::add);

        EthLog emptyLogResponse = mock(EthLog.class);
        when(emptyLogResponse.getLogs()).thenReturn(List.of());
        Request<?, EthLog> emptyLogRequest = requestReturning(emptyLogResponse);
        ArgumentCaptor<EthFilter> filterCaptor = ArgumentCaptor.forClass(EthFilter.class);
        when(web3j.ethGetLogs(filterCaptor.capture())).thenReturn((Request) emptyLogRequest);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 105L);

        taskCaptor.getValue().run();

        assertThat(filterCaptor.getValue().getAddress()).isEmpty();
        assertThat(filterCaptor.getValue().getTopics()).hasSize(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribeAddressCursorInitializesToLatestNotGenesis() throws IOException {
        stubBlockNumber(DefaultBlockParameterName.LATEST, 999L);
        when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));

        adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });

        // The only way to observe the cursor without a poll running is indirectly: fetchLatestBlockNumber
        // must have been called once during subscribeAddress itself (to seed the cursor), which this
        // verifies happened before any scheduled task could have run.
        verify(web3j).ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancellingTheSubscriptionStopsFurtherPolling() throws IOException {
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) future);

        Subscription subscription = adapter.subscribeAddress(WATCHED_ADDRESS, result -> { });
        subscription.cancel();

        verify(future).cancel(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollBuildsObservationsDirectlyFromTheMatchedLogNotViaGetTx() throws IOException {
        // Phase 9 Finding 1: proves pollOnce no longer calls eth_getTransactionByHash/receipt at all
        // for an observation it already has a matched log for.
        stubBlockNumber(DefaultBlockParameterName.LATEST, 100L);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(taskCaptor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn((ScheduledFuture) mock(ScheduledFuture.class));

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(WATCHED_ADDRESS, received::add);

        Log log = transferLog(101L, CONTRACT, "0xsender", WATCHED_ADDRESS, BigInteger.valueOf(42));
        EthLog logResponse = mock(EthLog.class);
        when(logResponse.getLogs()).thenReturn(List.of((EthLog.LogResult<?>) () -> log));
        Request<?, EthLog> logRequest = requestReturning(logResponse);
        when(web3j.ethGetLogs(any(EthFilter.class))).thenReturn((Request) logRequest);
        stubBlockNumber(DefaultBlockParameterName.LATEST, 101L);

        taskCaptor.getValue().run();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).tokenContractAddress()).isEqualTo(CONTRACT);
        assertThat(received.get(0).amount().longValueExact()).isEqualTo(42L);
        verify(web3j, never()).ethGetTransactionByHash(any());
        verify(web3j, never()).ethGetTransactionReceipt(any());
    }

    // ---------- close ----------

    @Test
    void closeShutsDownWeb3jAndScheduler() {
        adapter.close();

        verify(web3j).shutdown();
        verify(scheduler).shutdown();
    }
}
