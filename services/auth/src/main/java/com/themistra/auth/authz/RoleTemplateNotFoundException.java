package com.themistra.auth.authz;

public class RoleTemplateNotFoundException extends RuntimeException {

    public RoleTemplateNotFoundException(String name) {
        super("Role template not found: " + name);
    }
}
