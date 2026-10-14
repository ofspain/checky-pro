package com.themistra.payment.invoice;

import com.themistra.payment.common.config.InvoiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private InvoiceRepository repository;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        InvoiceProperties invoiceProperties = new InvoiceProperties(60, 0);
        service = new InvoiceService(repository, invoiceProperties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldCreateInvoiceWithOpenStateAndComputedExpiry() {
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", "order-1");

        assertThat(invoice.getState()).isEqualTo(InvoiceState.OPEN);
        assertThat(invoice.getInvoiceUuid()).isNotNull();
        assertThat(invoice.getMerchantAccountUuid()).isEqualTo(MERCHANT_ACCOUNT_UUID);
        assertThat(invoice.getChain()).isEqualTo("ETHEREUM");
        assertThat(invoice.getTokenContractAddress()).isEqualTo("0xabc");
        assertThat(invoice.getTokenDecimals()).isEqualTo((short) 18);
        assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(invoice.getPayToAddress()).isEqualTo("0xdef");
        assertThat(invoice.getReference()).isEqualTo("order-1");
        assertThat(invoice.getCreatedAt()).isEqualTo(NOW);
        assertThat(invoice.getUpdatedAt()).isEqualTo(NOW);
        assertThat(invoice.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        verify(repository).save(invoice);
    }

    @Test
    void shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape() {
        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "BITCOIN", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("BITCOIN");

        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, null, "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class);

        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", null, (short) 18,
                new BigDecimal("1000"), "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("tokenContractAddress");

        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "   ", (short) 18,
                new BigDecimal("1000"), "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("tokenContractAddress");
    }

    @Test
    void shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat() {
        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                null, "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("null");

        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                BigDecimal.ZERO, "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("-5"), "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("10.5"), "0xdef", null))
                .isInstanceOf(InvalidInvoiceRequestException.class)
                .hasMessageContaining("whole number");

        BigDecimal tooManyDigits = new BigDecimal("1".repeat(79));
        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                tooManyDigits, "0xdef", null))
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

        Invoice invoice = service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("100.00"), "0xdef", null);
        assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        BigDecimal eightyOneDigitsWithTrailingZeros = new BigDecimal("1" + "0".repeat(80));
        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                eightyOneDigitsWithTrailingZeros, "0xdef", null))
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
        Invoice invoice = service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                exactly78Digits, "0xdef", null);

        assertThat(invoice.getAmount()).isEqualByComparingTo(exactly78Digits);
    }

    /** Phase 11 Gap 3: AC5 requires {@code create(...)} to be {@code @Transactional}; this plain-
     * JUnit suite never loads a Spring context, so removing the annotation would otherwise leave
     * the suite green. A source-text guard closes that gap without needing a context boot,
     * mirroring {@code T01SkeletonRegressionTest}'s own identical source-text-check style. */
    @Test
    void createMethodIsAnnotatedTransactional() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/payment/invoice/InvoiceService.java"));
        assertThat(source).contains("@Transactional");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullMerchantAccountUuid() {
        assertThatThrownBy(() -> service.create(null, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("merchantAccountUuid");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullPayToAddress() {
        assertThatThrownBy(() -> service.create(MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payToAddress");
    }
}
