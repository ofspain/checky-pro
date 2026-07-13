package com.themistra.auth.account.dto;

/**
 * The ONLY response shape {@code POST /accounts} ever returns, regardless of whether the email
 * was already taken (target-design §4: "no enumeration anywhere ... registration ... return
 * uniform responses/timing whether or not the account exists"). Never branch this message on
 * {@link com.themistra.auth.account.DuplicateEmailException}.
 */
public record RegistrationAcknowledgement(String message) {

    public static RegistrationAcknowledgement standard() {
        return new RegistrationAcknowledgement(
                "If this email is available, check your inbox to verify your account.");
    }
}
