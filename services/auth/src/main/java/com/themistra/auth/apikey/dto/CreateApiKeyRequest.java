package com.themistra.auth.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api-keys}'s request body (T26, R30). Bounds mirror
 * {@code ApiKeyService.requireValidName}'s own limit exactly — validated here so a blank or
 * over-length name 400s via the framework's existing {@code MethodArgumentNotValidException}
 * handling, rather than falling through to {@code ApiKeyService}'s {@code IllegalArgumentException}
 * (unmapped, would otherwise surface as an opaque 500).
 */
public record CreateApiKeyRequest(

        @NotBlank @Size(max = 100) String name
) {
}
