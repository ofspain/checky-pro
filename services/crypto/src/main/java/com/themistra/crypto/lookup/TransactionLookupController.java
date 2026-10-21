package com.themistra.crypto.lookup;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.common.ResourceServerConfig;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Crypto task 30 (R29, L16): the agreed facts for a transaction hash the caller already has. */
@RestController
@Validated
@RequestMapping("/internal/v1/transactions")
public class TransactionLookupController {

    private final TransactionLookupService lookupService;

    public TransactionLookupController(TransactionLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/{chain}/{txHash}")
    @PreAuthorize("hasAuthority('" + ResourceServerConfig.INTERNAL_SCOPE_AUTHORITY + "')")
    public TransactionLookupResponse lookup(@PathVariable Chain chain,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9]{1,128}$") String txHash) {
        return lookupService.lookup(chain, txHash);
    }
}
