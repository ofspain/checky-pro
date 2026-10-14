package com.themistra.payment.invoice;

import com.themistra.payment.common.config.InvoiceProperties;
import com.themistra.payment.events.OutboxPublisher;
import com.themistra.payment.invoice.event.InvoiceCreatedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final UUID WATCH_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(60));

    @Mock
    private InvoiceRepository repository;

    @Mock
    private OutboxPublisher outboxPublisher;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        InvoiceProperties invoiceProperties = new InvoiceProperties(60, 0);
        service = new InvoiceService(repository, invoiceProperties, Clock.fixed(NOW, ZoneOffset.UTC),
                outboxPublisher);
    }

    @Test
    void shouldComputeExpiresAtFromInjectedClockAndConfiguredTtl() {
        assertThat(service.computeExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void shouldCreateInvoiceWithOpenStateAndComputedExpiry() {
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", "order-1", EXPIRES_AT, WATCH_ID);

        assertThat(invoice.getState()).isEqualTo(InvoiceState.OPEN);
        assertThat(invoice.getInvoiceUuid()).isEqualTo(INVOICE_UUID);
        assertThat(invoice.getMerchantAccountUuid()).isEqualTo(MERCHANT_ACCOUNT_UUID);
        assertThat(invoice.getChain()).isEqualTo("ETHEREUM");
        assertThat(invoice.getTokenContractAddress()).isEqualTo("0xabc");
        assertThat(invoice.getTokenDecimals()).isEqualTo((short) 18);
        assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(invoice.getPayToAddress()).isEqualTo("0xdef");
        assertThat(invoice.getReference()).isEqualTo("order-1");
        assertThat(invoice.getWatchId()).isEqualTo(WATCH_ID);
        assertThat(invoice.getCreatedAt()).isEqualTo(NOW);
        assertThat(invoice.getUpdatedAt()).isEqualTo(NOW);
        assertThat(invoice.getExpiresAt()).isEqualTo(EXPIRES_AT);
        verify(repository).save(invoice);
    }

    /** R2 (AC3): the invoice.created outbox event is emitted in the same call as the insert. */
    @Test
    void shouldEmitInvoiceCreatedInSameTransactionAsInsert() {
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", "order-1", EXPIRES_AT, WATCH_ID);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("invoice"), eq(INVOICE_UUID.toString()),
                eq("invoice.created"), payloadCaptor.capture());

        // Phase 11 Gap 6: the bare any() above only proved publish was called, never that the
        // payload itself is correct - in particular, that amount is a decimal String (L6), not a
        // JSON number, matching RegisterWatchRequest's own identical convention.
        InvoiceCreatedPayload payload = (InvoiceCreatedPayload) payloadCaptor.getValue();
        assertThat(payload.invoiceUuid()).isEqualTo(INVOICE_UUID);
        assertThat(payload.merchantAccountUuid()).isEqualTo(MERCHANT_ACCOUNT_UUID);
        assertThat(payload.chain()).isEqualTo("ETHEREUM");
        assertThat(payload.tokenContractAddress()).isEqualTo("0xabc");
        assertThat(payload.tokenDecimals()).isEqualTo((short) 18);
        assertThat(payload.amount()).isEqualTo("1000");
        assertThat(payload.payToAddress()).isEqualTo("0xdef");
        assertThat(payload.reference()).isEqualTo("order-1");
        assertThat(payload.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape() {
        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "BITCOIN", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("BITCOIN");

        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, null, "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class);

        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", null,
                (short) 18, new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("tokenContractAddress");

        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "   ",
                (short) 18, new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("tokenContractAddress");

        verify(repository, never()).save(any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat() {
        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, null, "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("null");

        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, BigDecimal.ZERO, "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("-5"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("10.5"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("whole number");

        BigDecimal tooManyDigits = new BigDecimal("1".repeat(79));
        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, tooManyDigits, "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("78");
    }

    /** Phase 7 finding 2, made permanent: a trailing-zero whole number (scale > 0 but no real
     * fractional component) must be accepted, and the 78-digit precision check must use the
     * amount's true digit count, not the reduced count {@code stripTrailingZeros()} alone would
     * report for a number with trailing zeros. */
    @Test
    void shouldAcceptWholeNumberAmountWithTrailingZerosAndRejectByTrueDigitCountNotReducedPrecision() {
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("100.00"), "0xdef", null, EXPIRES_AT, WATCH_ID);
        assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        BigDecimal eightyOneDigitsWithTrailingZeros = new BigDecimal("1" + "0".repeat(80));
        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, eightyOneDigitsWithTrailingZeros, "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("78");
    }

    /** Phase 11 Gap 2: the 79-digit rejection case alone does not prove the boundary itself is
     * correct - an off-by-one in the digit-count comparison would only surface here, at exactly
     * 78 digits, accepted. */
    @Test
    void shouldAcceptAmountAtExactly78DigitsPrecisionBoundary() {
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal exactly78Digits = new BigDecimal("1".repeat(78));
        Invoice invoice = service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, exactly78Digits, "0xdef", null, EXPIRES_AT, WATCH_ID);

        assertThat(invoice.getAmount()).isEqualByComparingTo(exactly78Digits);
    }

    /** Phase 11 Gap 3 (T05): AC5 requires {@code create(...)} to be {@code @Transactional}; this
     * plain-JUnit suite never loads a Spring context, so removing the annotation would otherwise
     * leave the suite green. A source-text guard closes that gap without needing a context boot,
     * mirroring {@code T01SkeletonRegressionTest}'s own identical source-text-check style. */
    @Test
    void createMethodIsAnnotatedTransactional() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/payment/invoice/InvoiceService.java"));
        assertThat(source).contains("@Transactional");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullInvoiceUuid() {
        assertThatThrownBy(() -> service.create(null, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("invoiceUuid");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullMerchantAccountUuid() {
        assertThatThrownBy(() -> service.create(INVOICE_UUID, null, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("merchantAccountUuid");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullPayToAddress() {
        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), null, null, EXPIRES_AT, WATCH_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payToAddress");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullExpiresAt() {
        assertThatThrownBy(() -> service.create(INVOICE_UUID, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", null, null, WATCH_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt");
    }
}
