package com.themistra.payment.receipt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.payment.PaymentReceiptView;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.receipt.dto.ReceiptResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The merchant's receipt read (R28, FU-T29-1). Ownership is decided from the database: receipt to
 * payment to invoice to {@code merchantAccountUuid}. The body comes from the signed JSON object in
 * S3, so the merchant gets exactly what was signed. Before returning it, its {@code receiptUuid}
 * and {@code merchantAccountUuid} are checked against the database; a missing or mismatched object
 * is an internal fault ({@link ReceiptStorageException}, a 500), never another merchant's data.
 *
 * <p>Kept apart from {@link ReceiptService}, which issues receipts; this class only reads.</p>
 */
@Service
public class ReceiptReadService {

    private final ReceiptRepository receiptRepository;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final ReceiptStore receiptStore;
    private final ObjectMapper objectMapper;

    public ReceiptReadService(ReceiptRepository receiptRepository, PaymentService paymentService,
                              InvoiceService invoiceService, ReceiptStore receiptStore, ObjectMapper objectMapper) {
        this.receiptRepository = receiptRepository;
        this.paymentService = paymentService;
        this.invoiceService = invoiceService;
        this.receiptStore = receiptStore;
        this.objectMapper = objectMapper;
    }

    public ReceiptResponse getForMerchant(UUID merchantAccountUuid, UUID receiptUuid) {
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        Objects.requireNonNull(receiptUuid, "receiptUuid must not be null");

        Receipt receipt = receiptRepository.findByReceiptUuid(receiptUuid)
                .orElseThrow(ReceiptNotFoundException::new);
        PaymentReceiptView payment = paymentService.findReceiptPrimitives(receipt.getPaymentId())
                .orElseThrow(ReceiptNotFoundException::new);
        InvoiceReceiptView invoice = invoiceService.findReceiptPrimitives(payment.invoiceId())
                .orElseThrow(ReceiptNotFoundException::new);
        if (!merchantAccountUuid.equals(invoice.merchantAccountUuid())) {
            throw new ReceiptNotFoundException();
        }

        byte[] json = receiptStore.readJsonObject(receipt.getS3Bucket(), receipt.getS3Key())
                .orElseThrow(() -> new ReceiptStorageException(
                        "Stored receipt object missing for receipt " + receiptUuid, null));
        ReceiptResponse response = parse(json, receiptUuid);
        if (!receiptUuid.equals(response.receiptUuid())
                || !invoice.merchantAccountUuid().equals(response.merchantAccountUuid())) {
            throw new ReceiptStorageException(
                    "Stored receipt object does not match the database for receipt " + receiptUuid, null);
        }
        return response;
    }

    private ReceiptResponse parse(byte[] json, UUID receiptUuid) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new ReceiptResponse(
                    UUID.fromString(text(node, "receiptUuid")),
                    UUID.fromString(text(node, "invoiceUuid")),
                    UUID.fromString(text(node, "paymentUuid")),
                    UUID.fromString(text(node, "merchantAccountUuid")),
                    text(node, "chain"),
                    text(node, "txHash"),
                    text(node, "tokenContractAddress"),
                    integer(node, "tokenDecimals"),
                    text(node, "amount"),
                    text(node, "payToAddress"),
                    text(node, "fromAddress"),
                    Instant.parse(text(node, "finalizedAt")),
                    text(node, "signature"),
                    text(node, "kmsKeyId"),
                    Instant.parse(text(node, "signedAt")));
        } catch (IOException | RuntimeException e) {
            throw new ReceiptStorageException("Stored receipt object is unreadable for receipt " + receiptUuid, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException("field '" + field + "' is missing or not a string");
        }
        return value.asText();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
            throw new IllegalStateException("field '" + field + "' is missing or not an integer");
        }
        return value.intValue();
    }
}
