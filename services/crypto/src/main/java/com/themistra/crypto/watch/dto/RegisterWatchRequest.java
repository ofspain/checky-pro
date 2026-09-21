package com.themistra.crypto.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /internal/v1/watches} request body - VERBATIM shape (`design.md` §4c):
 * {@code { invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt }}.
 * {@code expectedAmount} is a decimal string on the wire (agents.md: monetary values are decimal
 * strings, never JSON numbers) - structural presence only is checked here; the scale-0-positive-integer
 * format check (Phase 3 Finding 2) and the {@code chain}-conditional address-validity check (Phase 3
 * Finding 1, L8) both need logic bean validation cannot express, and are performed in
 * {@code WatchService.register}. Hand-written, not generated from {@code contracts/api/crypto-internal.yaml}
 * - that file does not exist anywhere in this repository yet (Phase 0 finding); mirrors {@code
 * services/auth}'s own established hand-written-DTO precedent.
 */
public record RegisterWatchRequest(
        @NotNull UUID invoiceUuid,
        @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
        @NotBlank @Size(max = 128) String address,
        @NotBlank @Size(max = 128) String tokenContractAddress,
        @NotBlank String expectedAmount,
        @NotNull Instant expiresAt) {
}
