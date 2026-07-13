package com.themistra.auth.account;

/**
 * Deliberately carries no email in the message. The API layer responds to registration with
 * the same 202 shape whether or not the address was taken (enumeration defense, target-design §4);
 * this exception exists so that layer can branch, not so the caller learns anything.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("Email already registered");
    }
}
