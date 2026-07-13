package com.themistra.auth.account;

import java.util.UUID;

/** Thrown when a lifecycle transition is attempted from a state that does not permit it. */
public class InvalidAccountStateException extends RuntimeException {

    public InvalidAccountStateException(UUID accountUuid, AccountStatus current, String action) {
        super("Account %s: cannot %s while %s".formatted(accountUuid, action, current));
    }
}
