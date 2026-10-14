package com.themistra.payment.invoice;

import com.themistra.payment.common.config.InvoiceProperties;
import com.themistra.payment.events.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * T07 (R28/AC1/AC2) — a dedicated file, not an extension of {@link InvoiceServiceTest}
 * (frozen brief's own explicit choice): that file is already large and T05/T06-scoped; the
 * read path gets its own discoverable home.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceReadTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID OTHER_MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final UUID WATCH_ID = UUID.randomUUID();
    private static final Instant EXPIRES_AT = NOW.plusSeconds(3600);

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

    private Invoice invoice(UUID merchantAccountUuid) {
        return Invoice.create(INVOICE_UUID, merchantAccountUuid, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", "order-1", EXPIRES_AT, WATCH_ID, NOW);
    }

    /** Named test (R28). */
    @Test
    void shouldScopeInvoiceReadsToOwningMerchant() {
        Invoice owned = invoice(MERCHANT_ACCOUNT_UUID);
        when(repository.findByInvoiceUuidAndMerchantAccountUuid(INVOICE_UUID, MERCHANT_ACCOUNT_UUID))
                .thenReturn(Optional.of(owned));
        when(repository.findByInvoiceUuidAndMerchantAccountUuid(INVOICE_UUID, OTHER_MERCHANT_ACCOUNT_UUID))
                .thenReturn(Optional.empty());

        assertThat(service.getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID)).isEqualTo(owned);
        assertThatThrownBy(() -> service.getForMerchant(OTHER_MERCHANT_ACCOUNT_UUID, INVOICE_UUID))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void shouldReturnOnlyCallersOwnInvoicesFromList() {
        Invoice owned = invoice(MERCHANT_ACCOUNT_UUID);
        when(repository.findByMerchantAccountUuidOrderByCreatedAtDesc(MERCHANT_ACCOUNT_UUID))
                .thenReturn(List.of(owned));

        List<Invoice> result = service.listForMerchant(MERCHANT_ACCOUNT_UUID);

        assertThat(result).containsExactly(owned);
    }

    @Test
    void shouldReturnEmptyListWhenCallerHasNoInvoices() {
        when(repository.findByMerchantAccountUuidOrderByCreatedAtDesc(MERCHANT_ACCOUNT_UUID))
                .thenReturn(List.of());

        assertThat(service.listForMerchant(MERCHANT_ACCOUNT_UUID)).isEmpty();
    }

    @Test
    void shouldReturnInvoiceForOwningMerchant() {
        Invoice owned = invoice(MERCHANT_ACCOUNT_UUID);
        when(repository.findByInvoiceUuidAndMerchantAccountUuid(INVOICE_UUID, MERCHANT_ACCOUNT_UUID))
                .thenReturn(Optional.of(owned));

        assertThat(service.getForMerchant(MERCHANT_ACCOUNT_UUID, INVOICE_UUID)).isSameAs(owned);
    }

    @Test
    void shouldReturn404ForNonOwningMerchant() {
        when(repository.findByInvoiceUuidAndMerchantAccountUuid(INVOICE_UUID, OTHER_MERCHANT_ACCOUNT_UUID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForMerchant(OTHER_MERCHANT_ACCOUNT_UUID, INVOICE_UUID))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    /** AC2: the identical exception type/instance shape as the non-owning-merchant case above -
     * {@link InvoiceService} itself has no way to tell the two cases apart, since both reach the
     * same {@code Optional.empty()} branch of the same query. */
    @Test
    void shouldReturn404ForNonexistentInvoiceUuid() {
        UUID nonexistentUuid = UUID.randomUUID();
        when(repository.findByInvoiceUuidAndMerchantAccountUuid(nonexistentUuid, MERCHANT_ACCOUNT_UUID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT_ACCOUNT_UUID, nonexistentUuid))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionForNullMerchantAccountUuidOnList() {
        assertThatThrownBy(() -> service.listForMerchant(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("merchantAccountUuid");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullMerchantAccountUuidOnGet() {
        assertThatThrownBy(() -> service.getForMerchant(null, INVOICE_UUID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("merchantAccountUuid");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullInvoiceUuidOnGet() {
        assertThatThrownBy(() -> service.getForMerchant(MERCHANT_ACCOUNT_UUID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("invoiceUuid");
    }
}
