package com.themistra.auth.account;

import java.util.UUID;

/**
 * Message carries the UUID only — safe to log, and the API layer maps it to a uniform 404
 * that never distinguishes "unknown" from "existing but inaccessible" (no enumeration oracle).
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountUuid) {
        super("Account not found: " + accountUuid);
    }
}
