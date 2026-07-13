package com.themistra.auth.account;

/**
 * Account lifecycle states. Transitions are guarded by {@link Account} methods —
 * never set status directly. Values match the CHECK constraint on accounts.status.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    SUSPENDED,
    DELETED
}
