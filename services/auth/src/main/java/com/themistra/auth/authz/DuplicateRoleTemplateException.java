package com.themistra.auth.authz;

public class DuplicateRoleTemplateException extends RuntimeException {

    public DuplicateRoleTemplateException() {
        super("Role template name already exists");
    }
}
