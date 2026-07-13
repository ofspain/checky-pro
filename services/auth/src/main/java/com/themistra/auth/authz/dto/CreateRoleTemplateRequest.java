package com.themistra.auth.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleTemplateRequest(

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 255)
        String description,

        @NotEmpty
        Set<@NotBlank String> roleNames
) {
}
