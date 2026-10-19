package com.themistra.payment.receipt;

import com.themistra.payment.receipt.dto.ReceiptResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code GET /api/v1/receipts/{receiptUuid}} (R28, FU-T29-1). {@code ROLE_MERCHANT} only, and only
 * the caller's own receipts: another merchant's receipt is a 404, exactly like a receipt that does
 * not exist (L10). The merchant comes from the token's {@code sub} claim, never the request.
 */
@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptReadService receiptReadService;

    public ReceiptController(ReceiptReadService receiptReadService) {
        this.receiptReadService = receiptReadService;
    }

    @GetMapping("/{receiptUuid}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ReceiptResponse get(@PathVariable UUID receiptUuid, @AuthenticationPrincipal Jwt jwt) {
        return receiptReadService.getForMerchant(merchantAccountUuidFrom(jwt), receiptUuid);
    }

    /** Same rule as {@code InvoiceController.merchantAccountUuidFrom}: a missing or malformed
     * {@code sub} is a 400, not a 500. */
    private static UUID merchantAccountUuidFrom(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidReceiptRequestException("sub claim is missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidReceiptRequestException("sub claim is not a valid UUID: " + subject);
        }
    }
}
