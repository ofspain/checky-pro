package com.themistra.auth.authz;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String name) {
        super("Role not found: " + name);
    }
}
