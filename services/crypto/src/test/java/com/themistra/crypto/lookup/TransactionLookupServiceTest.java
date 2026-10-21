package com.themistra.crypto.lookup;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ChainAdapter;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.quorum.QuorumEvaluator;
import com.themistra.crypto.token.TokenAllowlist;
import com.themistra.crypto.token.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Crypto task 30 (R29, L16). Real {@link QuorumEvaluator}; the three providers are mocks. */
class TransactionLookupServiceTest {

    private static final String TX = "0xabc";
    private static final String TOKEN = "0xToken";

    private final ProviderSet providerSet = mock(ProviderSet.class);
    private final TokenValidator tokenValidator = mock(TokenValidator.class);
    private final TransactionLookupService service =
            new TransactionLookupService(providerSet, new QuorumEvaluator(), tokenValidator);

    @BeforeEach
    void allowlistTheToken() {
        when(tokenValidator.validate("ETHEREUM", TOKEN)).thenReturn(Optional.of(mock(TokenAllowlist.class)));
    }

    @Test
    void threeAgreeingProvidersGiveEveryFactAgreed() {
        providersAnswer(found("1000000", 12), found("1000000", 12), found("1000000", 12));

        TransactionLookupResponse response = service.lookup(Chain.ETHEREUM, TX);

        assertThat(response).isEqualTo(new TransactionLookupResponse("ETHEREUM", TX, "AGREED", 12, TOKEN,
                "0xFrom", "0xTo", "1000000", "AGREED"));
    }

    /** The task's named case: a 2-of-3 disagreement stays HELD and is never resolved. */
    @Test
    void aFactOnWhichAllThreeDisagreeIsHeldWithNoFacts() {
        providersAnswer(found("1000000", 12), found("2000000", 12), found("3000000", 12));

        TransactionLookupResponse response = service.lookup(Chain.ETHEREUM, TX);

        assertThat(response).isEqualTo(TransactionLookupResponse.held("ETHEREUM", TX, "AGREED"));
    }

    @Test
    void twoOfThreeAgreeingOnAFactIsEnoughAndTheDissentIsIgnored() {
        providersAnswer(found("1000000", 12), found("9999999", 12), found("1000000", 12));

        assertThat(service.lookup(Chain.ETHEREUM, TX).amount()).isEqualTo("1000000");
    }

    @Test
    void amountsAtDifferentScalesAgree() {
        providersAnswer(found("1000000", 12), found("1000000.00", 12), found("1000000.0", 12));

        TransactionLookupResponse response = service.lookup(Chain.ETHEREUM, TX);

        assertThat(response.outcome()).isEqualTo("AGREED");
        assertThat(response.amount()).isEqualTo("1000000");
    }

    @Test
    void unanimousAbsenceIsNotFound() {
        providersAnswer(missing(), missing(), missing());

        assertThatThrownBy(() -> service.lookup(Chain.ETHEREUM, TX)).isInstanceOf(TransactionNotFoundException.class);
    }

    /** Two say it does not exist, one says it does: never reported absent, never reported present. */
    @Test
    void aDisputedAbsenceIsHeldNotNotFound() {
        providersAnswer(missing(), found("1000000", 1), missing());

        assertThat(service.lookup(Chain.ETHEREUM, TX)).isEqualTo(TransactionLookupResponse.held("ETHEREUM", TX, "HELD"));
    }

    /** Exists by 2-of-3, but one provider has not seen it: the other facts have only 2 answers. */
    @Test
    void aTransactionOneProviderHasNotSeenHasNoDecidableFacts() {
        providersAnswer(found("1000000", 3), found("1000000", 3), missing());

        assertThat(service.lookup(Chain.ETHEREUM, TX)).isEqualTo(TransactionLookupResponse.held("ETHEREUM", TX, "AGREED"));
    }

    /** A provider reporting the transaction absent is left out of every other fact, even when it
     * echoes values that would agree (as the watcher does). */
    @Test
    void aProviderReportingAbsenceIsLeftOutEvenWhenItEchoesMatchingValues() {
        TxResult absentButEchoing = new TxResult(false, TX, "0xFrom", "0xTo", TOKEN, new BigDecimal("1000000"), 3, 100L);
        providersAnswer(found("1000000", 3), found("1000000", 3), absentButEchoing);

        assertThat(service.lookup(Chain.ETHEREUM, TX)).isEqualTo(TransactionLookupResponse.held("ETHEREUM", TX, "AGREED"));
    }

    @Test
    void agreedFactsForATokenOffTheAllowlistAreUnknownToken() {
        when(tokenValidator.validate(anyString(), anyString())).thenReturn(Optional.empty());
        providersAnswer(found("1000000", 12), found("1000000", 12), found("1000000", 12));

        TransactionLookupResponse response = service.lookup(Chain.ETHEREUM, TX);

        assertThat(response.outcome()).isEqualTo("UNKNOWN_TOKEN");
        assertThat(response.existence()).isEqualTo("AGREED");
    }

    @Test
    void aProviderThatCannotAnswerIsUnavailableNotHeld() {
        List<ProviderSet.NamedAdapter> adapters = adapters(found("1", 1), found("1", 1), found("1", 1));
        ChainAdapter failing = mock(ChainAdapter.class);
        when(failing.getTx(TX)).thenThrow(new IllegalStateException("timeout"));
        adapters.set(1, new ProviderSet.NamedAdapter("provider-b", failing));
        when(providerSet.adaptersFor(Chain.ETHEREUM)).thenReturn(adapters);

        assertThatThrownBy(() -> service.lookup(Chain.ETHEREUM, TX)).isInstanceOf(ProvidersUnavailableException.class);
    }

    @Test
    void aMissingValueForAFactIsHeld() {
        providersAnswer(found("1000000", 12), found(null, 12), found("1000000", 12));

        assertThat(service.lookup(Chain.ETHEREUM, TX).outcome()).isEqualTo("HELD");
    }

    private void providersAnswer(TxResult a, TxResult b, TxResult c) {
        List<ProviderSet.NamedAdapter> adapters = adapters(a, b, c);
        when(providerSet.adaptersFor(Chain.ETHEREUM)).thenReturn(adapters);
    }

    private static List<ProviderSet.NamedAdapter> adapters(TxResult... results) {
        List<ProviderSet.NamedAdapter> adapters = new ArrayList<>();
        for (int i = 0; i < results.length; i++) {
            ChainAdapter adapter = mock(ChainAdapter.class);
            when(adapter.getTx(TX)).thenReturn(results[i]);
            adapters.add(new ProviderSet.NamedAdapter("provider-" + (char) ('a' + i), adapter));
        }
        return adapters;
    }

    private static TxResult found(String amount, int confirmations) {
        return new TxResult(true, TX, "0xFrom", "0xTo", TOKEN, amount == null ? null : new BigDecimal(amount),
                confirmations, 100L);
    }

    private static TxResult missing() {
        return new TxResult(false, TX, null, null, null, null, 0, 0L);
    }
}
