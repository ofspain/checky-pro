package com.themistra.payment.history;

import com.themistra.payment.history.dto.PaymentResponse;
import com.themistra.payment.invoice.InvalidInvoiceRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Payment read (frontend Phase 1b, 2026-10-21): R32, R33, R34, R39, R55, R56. */
@RestController
@RequestMapping("/api/v1/invoices")
public class PaymentHistoryController {

    private final PaymentHistoryService historyService;

    public PaymentHistoryController(PaymentHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{invoiceUuid}/payments")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<PaymentResponse> list(@PathVariable UUID invoiceUuid, @AuthenticationPrincipal Jwt jwt) {
        return historyService.listForMerchant(merchantAccountUuidFrom(jwt), invoiceUuid);
    }

    private static UUID merchantAccountUuidFrom(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidInvoiceRequestException("sub claim is missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidInvoiceRequestException("sub claim is not a valid UUID");
        }
    }
}
