package com.themistra.auth.authz.dto;

import com.themistra.auth.authz.Role;
import com.themistra.auth.authz.RoleTemplate;

import java.util.Set;
import java.util.stream.Collectors;

public record RoleTemplateResponse(String name, String description, Set<String> roleNames) {

    public static RoleTemplateResponse from(RoleTemplate template) {
        return new RoleTemplateResponse(
                template.getName(),
                template.getDescription(),
                template.getRoles().stream().map(RoleTemplateResponse::roleName).collect(Collectors.toSet()));
    }

    private static String roleName(Role role) {
        return role.getName();
    }
}
