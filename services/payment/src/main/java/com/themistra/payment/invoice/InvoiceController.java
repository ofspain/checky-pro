package com.themistra.payment.invoice;

import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import com.themistra.payment.invoice.dto.CreateInvoiceRequest;
import com.themistra.payment.invoice.dto.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /api/v1/invoices} (R1). {@code ROLE_MERCHANT} only (L10) —
 * {@code JwtRoleAuthoritiesConverter} maps the token's own {@code roles} claim so {@code
 * @PreAuthorize} means something (T06, {@code ResourceServerConfig}'s own {@code
 * @EnableMethodSecurity}).
 *
 * <p><b>Orchestration, not {@code InvoiceService} (Phase 3/4 disposition):</b> {@code
 * invoiceUuid}/{@code expiresAt} are computed once, {@link CryptoClient#registerWatch} runs
 * strictly before any database transaction opens, and only then is {@link
 * InvoiceService#create} (its own {@code @Transactional} method) called with the already-known
 * {@code watchId}. This is deliberate: a transactional method calling another transactional
 * method on {@code this} would silently bypass Spring's AOP proxy and not actually be
 * transactional — keeping the orchestration here, calling two separate beans, avoids that
 * entirely while also keeping the external HTTP call outside any open transaction.</p>
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CryptoClient cryptoClient;

    public InvoiceController(InvoiceService invoiceService, CryptoClient cryptoClient) {
        this.invoiceService = invoiceService;
        this.cryptoClient = cryptoClient;
    }

    @PostMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        UUID merchantAccountUuid = merchantAccountUuidFrom(jwt);
        UUID invoiceUuid = UUID.randomUUID();
        Instant expiresAt = invoiceService.computeExpiresAt();

        RegisterWatchResponse watchResponse = cryptoClient.registerWatch(invoiceUuid, request.chain(),
                request.payToAddress(), request.tokenContractAddress(), request.amount(), expiresAt);

        Invoice invoice = invoiceService.create(invoiceUuid, merchantAccountUuid, request.chain(),
                request.tokenContractAddress(), request.tokenDecimals(), request.amount(),
                request.payToAddress(), request.reference(), expiresAt, watchResponse.watchId());

        InvoiceResponse response = new InvoiceResponse(invoice.getInvoiceUuid(), invoice.getPayToAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Mirrors {@code notification-service}'s own identical {@code accountUuidFrom} helper
     * (Kimi Phase 11 Gap #10 there): a missing/blank/malformed {@code sub} claim maps to this
     * service's own {@code 400}, not an uncaught {@link NullPointerException} falling through to
     * a generic 500. */
    private static UUID merchantAccountUuidFrom(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidInvoiceRequestException("sub claim is missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new InvalidInvoiceRequestException("sub claim is not a valid UUID: " + subject);
        }
    }
}
