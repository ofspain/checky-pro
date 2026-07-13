package com.themistra.auth.authz;

/** Carries no name — the API layer can decide how much to reveal to the (always ADMIN) caller. */
public class DuplicateRoleException extends RuntimeException {

    public DuplicateRoleException() {
        super("Role name already exists");
    }
}
