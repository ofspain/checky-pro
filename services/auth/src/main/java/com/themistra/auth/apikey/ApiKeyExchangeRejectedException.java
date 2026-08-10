package com.themistra.auth.apikey;

/**
 * The single, uniform failure for {@link ApiKeyService#exchange} (R33): thrown identically
 * whether the presented key is malformed, its prefix is unknown, the matched row is revoked or
 * expired, or the secret doesn't match the stored hash. Mirrors
 * {@code AccountService.VerificationTokenRejectedException}'s established one-exception-for-every-
 * reason shape — never distinguishes cause, so a future caller cannot accidentally build an
 * enumeration oracle out of this exception's type or message.
 */
public class ApiKeyExchangeRejectedException extends RuntimeException {
}
