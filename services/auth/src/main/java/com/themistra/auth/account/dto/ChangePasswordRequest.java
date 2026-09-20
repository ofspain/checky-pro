package com.themistra.auth.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The caller's current password and the desired new password (R11). {@code toString()} is
 * overridden to omit both fields — unlike {@code PasswordResetConfirmRequest}, which keeps its
 * one-time {@code token} visible, both fields here are standing account credentials.
 */
public record ChangePasswordRequest(

        @NotBlank
        String currentPassword,

        @NotBlank
        String newPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[]";
    }
}
