package com.themistra.auth.account.dto;

import com.themistra.auth.account.AccountStatus;

import java.util.UUID;

/**
 * Minimal credential view for the authn module — the only sanctioned way another module sees a
 * password hash. Never serialized to any API response.
 */
public record LoginView(
        UUID accountUuid,
        String passwordHash,
        AccountStatus status
) {
}
