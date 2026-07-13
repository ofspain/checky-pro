package com.themistra.auth.account.dto;

import com.themistra.auth.account.Account;
import com.themistra.auth.account.AccountStatus;

import java.time.Instant;
import java.util.UUID;

/** Outbound view of an account: external UUID only — never the internal id or password hash. */
public record AccountResponse(
        UUID accountUuid,
        String email,
        boolean emailVerified,
        AccountStatus status,
        Instant createdAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountUuid(),
                account.getEmail(),
                account.isEmailVerified(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
