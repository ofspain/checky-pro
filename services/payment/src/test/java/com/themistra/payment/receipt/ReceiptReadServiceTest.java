package com.themistra.payment.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.payment.common.CanonicalJson;
import com.themistra.payment.common.crypto.AttestationResult;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.payment.PaymentReceiptView;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import com.themistra.payment.receipt.dto.ReceiptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * FU-T29-1: the merchant receipt read. Ownership comes from the database chain (receipt, payment,
 * invoice); the body comes from the signed S3 JSON, built here with the real {@code
 * ReceiptStore.toCanonicalMap} and {@code CanonicalJson} so the parser is tested against the exact
 * bytes the store writes.
 */
class ReceiptReadServiceTest {

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final UUID OTHER_MERCHANT = UUID.randomUUID();
    private static final UUID RECEIPT_UUID = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final UUID PAYMENT_UUID = UUID.randomUUID();
    private static final long PAYMENT_ID = 11L;
    private static final long INVOICE_ID = 22L;
    private static final Instant FINALIZED_AT = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant SIGNED_AT = Instant.parse("2026-10-19T10:00:05.123Z");

    private final ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final ReceiptStore receiptStore = mock(ReceiptStore.class);
    private ReceiptReadService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptReadService(receiptRepository, paymentService, invoiceService, receiptStore,
                new ObjectMapper().findAndRegisterModules());
        Receipt receipt = Receipt.create(RECEIPT_UUID, PAYMENT_ID, "a".repeat(64), "sig-hex", "kms-key-1",
                "bucket", "receipts/prefix", SIGNED_AT, SIGNED_AT);
        when(receiptRepository.findByReceiptUuid(RECEIPT_UUID)).thenReturn(Optional.of(receipt));
        when(paymentService.findReceiptPrimitives(PAYMENT_ID)).thenReturn(Optional.of(new PaymentReceiptView(
                PAYMENT_ID, PAYMENT_UUID, INVOICE_ID, "ETHEREUM", "0xtx", "0xtoken", new BigDecimal("1000000"),
                "0xfrom", FINALIZED_AT, PaymentState.ATTESTED)));
        when(invoiceService.findReceiptPrimitives(INVOICE_ID)).thenReturn(Optional.of(new InvoiceReceiptView(
                INVOICE_ID, INVOICE_UUID, MERCHANT, (short) 6, "0xpayto", InvoiceState.PAID)));
    }

    private static Map<String, Object> canonicalFields(UUID receiptUuid, UUID merchant) {
        ReceiptPayload payload = new ReceiptPayload(receiptUuid, INVOICE_UUID, PAYMENT_UUID, merchant, "ETHEREUM",
                "0xtx", "0xtoken", (short) 6, new BigDecimal("1000000"), "0xpayto", "0xfrom", FINALIZED_AT);
        return ReceiptStore.toCanonicalMap(payload, new AttestationResult.Signed("sig-hex", "kms-key-1", SIGNED_AT));
    }

    private void storedJson(Map<String, Object> fields) {
        when(receiptStore.readJsonObject("bucket", "receipts/prefix"))
                .thenReturn(Optional.of(CanonicalJson.serialize(fields).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void returnsTheStoredSignedReceiptToItsOwner() {
        storedJson(canonicalFields(RECEIPT_UUID, MERCHANT));

        ReceiptResponse response = service.getForMerchant(MERCHANT, RECEIPT_UUID);

        assertThat(response).isEqualTo(new ReceiptResponse(RECEIPT_UUID, INVOICE_UUID, PAYMENT_UUID, MERCHANT,
                "ETHEREUM", "0xtx", "0xtoken", 6, "1000000", "0xpayto", "0xfrom", FINALIZED_AT, "sig-hex",
                "kms-key-1", SIGNED_AT));
    }

    @Test
    void responseFieldOrderIsTheCanonicalSignedOrder() {
        List<String> canonical = List.copyOf(canonicalFields(RECEIPT_UUID, MERCHANT).keySet());
        List<String> record = java.util.Arrays.stream(ReceiptResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(record).isEqualTo(canonical);
    }

    @Test
    void anotherMerchantsReceiptIsNotFoundAndS3IsNeverRead() {
        assertThatThrownBy(() -> service.getForMerchant(OTHER_MERCHANT, RECEIPT_UUID))
                .isInstanceOf(ReceiptNotFoundException.class);
        verify(receiptStore, never()).readJsonObject(any(), any());
    }

    @Test
    void anUnknownReceiptIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(receiptRepository.findByReceiptUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT, unknown))
                .isInstanceOf(ReceiptNotFoundException.class);
    }

    @Test
    void aMissingStoredObjectIsAStorageFaultNotA404() {
        when(receiptStore.readJsonObject("bucket", "receipts/prefix")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT, RECEIPT_UUID))
                .isInstanceOf(ReceiptStorageException.class);
    }

    @Test
    void aStoredObjectForADifferentMerchantIsNeverReturned() {
        storedJson(canonicalFields(RECEIPT_UUID, OTHER_MERCHANT));

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT, RECEIPT_UUID))
                .isInstanceOf(ReceiptStorageException.class);
    }

    @Test
    void aStoredObjectForADifferentReceiptIsNeverReturned() {
        storedJson(canonicalFields(UUID.randomUUID(), MERCHANT));

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT, RECEIPT_UUID))
                .isInstanceOf(ReceiptStorageException.class);
    }

    @Test
    void aStoredObjectMissingAFieldIsAStorageFault() {
        Map<String, Object> fields = new LinkedHashMap<>(canonicalFields(RECEIPT_UUID, MERCHANT));
        fields.remove("signature");
        storedJson(fields);

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT, RECEIPT_UUID))
                .isInstanceOf(ReceiptStorageException.class);
    }

    @Test
    void aNonIntegerTokenDecimalsIsAStorageFault() {
        Map<String, Object> fields = new LinkedHashMap<>(canonicalFields(RECEIPT_UUID, MERCHANT));
        fields.put("tokenDecimals", "6");
        storedJson(fields);

        assertThatThrownBy(() -> service.getForMerchant(MERCHANT, RECEIPT_UUID))
                .isInstanceOf(ReceiptStorageException.class);
    }
}
