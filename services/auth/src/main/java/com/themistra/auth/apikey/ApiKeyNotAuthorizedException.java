package com.themistra.auth.apikey;

/**
 * Thrown by {@link ApiKeyService#create} when the caller doesn't hold the {@code MERCHANT} role
 * or has no confirmed TOTP enrollment (R30, L10). Deliberately carries no detail distinguishing
 * which precondition failed — this method has no controller/HTTP mapping yet (T24 is
 * service-layer only), so there is no enumeration-safety requirement driving this today, but a
 * single undifferentiated cause avoids baking in an assumption about what T25/T26's eventual
 * error response should reveal.
 */
public class ApiKeyNotAuthorizedException extends RuntimeException {
}
