package com.themistra.payment.chainverify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The landing page's one field. {@code chain} is optional and defaults to {@code ETHEREUM}, so the
 * page can stay a single input while the API keeps room for the chain crypto-service already takes.
 *
 * <p>The hash pattern mirrors crypto-service's own handler validation
 * ({@code ^[A-Za-z0-9]{1,128}$}) with the {@code 0x} prefix allowed, because that is how every
 * explorer and wallet shows an EVM hash and the page should accept what people paste. The prefix is
 * not stripped here — {@code VerifyService} normalises it, in one place.</p>
 */
public record VerifyRequest(
        @NotBlank(message = "Enter a transaction hash.")
        @Pattern(regexp = "^(0x)?[A-Za-z0-9]{1,128}$",
                message = "That does not look like a transaction hash.")
        String txHash,

        @Pattern(regexp = "^[A-Z]{1,32}$", message = "Unknown chain.")
        String chain
) {
}
