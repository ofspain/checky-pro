package com.themistra.payment.invoice.dto;

import java.util.UUID;

/** {@code POST /api/v1/invoices} response body — {@code invoiceUuid}/{@code payToAddress} (R1's own explicit text, AC4). */
public record InvoiceResponse(
        UUID invoiceUuid,
        String payToAddress
) {
}
