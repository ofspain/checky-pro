package com.themistra.payment.chainverify;

import com.themistra.payment.chainverify.dto.VerifyResponse;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.crypto.TransactionLookupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The claims this service actually makes. It decides nothing about the chain - crypto-service does
 * - so every case below is about one of the two things it does do: what it asks for, and what it
 * writes down.
 */
@ExtendWith(MockitoExtension.class)
class ChainVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:30:00Z");
    private static final String USDC = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
    private static final String HASH = "0xabc123";

    @Mock
    private CryptoClient cryptoClient;

    @Mock
    private ChainVerificationRepository repository;

    private ChainVerificationService service;

    @BeforeEach
    void setUp() {
        TokenDisplayProperties tokens = new TokenDisplayProperties(List.of(
                new TokenDisplayProperties.Token("ETHEREUM", USDC, "USDC", 6)));
        service = new ChainVerificationService(cryptoClient, repository, tokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
        // The real repository returns the saved instance; this service reads the row it stored
        // rather than the answer it was given, so the stub has to behave the same way.
        when(repository.save(any(ChainVerification.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static TransactionLookupResponse agreed(String amount) {
        return new TransactionLookupResponse("ETHEREUM", HASH, "AGREED", 12, USDC,
                "0xfrom", "0xto", amount, "AGREED");
    }

    @Test
    void storesEveryAgreedFactAndShowsTheAmountInTokenUnits() {
        when(cryptoClient.lookupTransaction("ETHEREUM", HASH)).thenReturn(agreed("12500000"));

        VerifyResponse response = service.verify(HASH, "ETHEREUM");

        assertThat(response.outcome()).isEqualTo("AGREED");
        assertThat(response.confirmations()).isEqualTo(12);
        assertThat(response.fromAddress()).isEqualTo("0xfrom");
        // Base units are what is stored and returned; the scaled figure is display only.
        assertThat(response.amount()).isEqualTo("12500000");
        assertThat(response.amountDisplay()).isEqualTo("12.5 USDC");
        assertThat(response.requestedAt()).isEqualTo(NOW);
    }

    @Test
    void storesAHeldAnswerWithNoFactsAtAll() {
        // crypto-service withholds every fact when its providers disagree. A row that invented
        // values here - or dropped the answer entirely - would be the one real failure mode.
        when(cryptoClient.lookupTransaction("ETHEREUM", HASH)).thenReturn(
                new TransactionLookupResponse("ETHEREUM", HASH, "HELD",
                        null, null, null, null, null, "HELD"));

        VerifyResponse response = service.verify(HASH, null);

        assertThat(response.outcome()).isEqualTo("HELD");
        assertThat(response.amount()).isNull();
        assertThat(response.amountDisplay()).isNull();
        assertThat(response.confirmations()).isNull();
        assertThat(response.fromAddress()).isNull();
        verify(repository).save(any(ChainVerification.class));
    }

    @Test
    void recordsAnAbsentTransactionAsNotFoundRatherThanDiscardingIt() {
        // A 404 from crypto-service means all three providers agree it does not exist. That is an
        // answer, and the point of the table is that every answer asked for is in it.
        when(cryptoClient.lookupTransaction("ETHEREUM", HASH)).thenReturn(null);

        VerifyResponse response = service.verify(HASH, "ETHEREUM");

        assertThat(response.outcome()).isEqualTo("NOT_FOUND");
        assertThat(response.txHash()).isEqualTo(HASH);
        verify(repository).save(any(ChainVerification.class));
    }

    @Test
    void lowercasesTheHashAndKeepsItsPrefixWhenAskingTheChain() {
        // Hex is case-insensitive, so one spelling is stored. The `0x` is NOT stripped: it is what
        // the providers expect and what `bin/chain pay` prints.
        when(cryptoClient.lookupTransaction("ETHEREUM", "0xabcdef")).thenReturn(agreed("1"));

        VerifyResponse response = service.verify("  0xABCDEF  ", "ETHEREUM");

        verify(cryptoClient).lookupTransaction("ETHEREUM", "0xabcdef");
        assertThat(response.txHash()).isEqualTo("0xabcdef");
    }

    @Test
    void defaultsToEthereumSoThePageCanStayOneField() {
        when(cryptoClient.lookupTransaction(eq("ETHEREUM"), any())).thenReturn(agreed("1"));

        service.verify(HASH, "  ");

        verify(cryptoClient).lookupTransaction("ETHEREUM", HASH);
    }

    @Test
    void showsNoScaledAmountForATokenItCannotName() {
        // UNKNOWN_TOKEN: crypto agreed the facts but the token is not allowlisted. The base units
        // are still true and still stored; only the human-readable line is withheld.
        when(cryptoClient.lookupTransaction("ETHEREUM", HASH)).thenReturn(
                new TransactionLookupResponse("ETHEREUM", HASH, "AGREED", 3, "0xsomeothertoken",
                        "0xfrom", "0xto", "900", "UNKNOWN_TOKEN"));

        VerifyResponse response = service.verify(HASH, "ETHEREUM");

        assertThat(response.outcome()).isEqualTo("UNKNOWN_TOKEN");
        assertThat(response.amount()).isEqualTo("900");
        assertThat(response.amountDisplay()).isNull();
    }

    @Test
    void recordsTheAnswerEvenWhenTheAmountCannotBeRead() {
        // The outcome and the addresses are still true. Dropping the whole row because one field
        // was unreadable would lose more than it protects.
        when(cryptoClient.lookupTransaction("ETHEREUM", HASH)).thenReturn(agreed("not-a-number"));

        VerifyResponse response = service.verify(HASH, "ETHEREUM");

        assertThat(response.outcome()).isEqualTo("AGREED");
        assertThat(response.amount()).isNull();
        assertThat(response.toAddress()).isEqualTo("0xto");
    }

    @Test
    void storesTheHashAndChainItActuallyAsked() {
        when(cryptoClient.lookupTransaction("ETHEREUM", HASH)).thenReturn(agreed("1"));

        service.verify(HASH, "ethereum");

        ArgumentCaptor<ChainVerification> saved = ArgumentCaptor.forClass(ChainVerification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getChain()).isEqualTo("ETHEREUM");
        assertThat(saved.getValue().getTxHash()).isEqualTo(HASH);
        assertThat(saved.getValue().getVerificationUuid()).isNotNull();
        assertThat(saved.getValue().getRequestedAt()).isEqualTo(NOW);
    }
}
