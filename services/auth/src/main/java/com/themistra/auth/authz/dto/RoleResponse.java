package com.themistra.auth.authz.dto;

import com.themistra.auth.authz.Role;

/** Roles are named singletons; the API surface never exposes the internal numeric id. */
public record RoleResponse(String name, String description) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getName(), role.getDescription());
    }
}
