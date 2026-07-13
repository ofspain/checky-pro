package com.themistra.auth.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(

        @NotBlank
        @Size(max = 64)
        String name,

        @Size(max = 255)
        String description
) {
}
