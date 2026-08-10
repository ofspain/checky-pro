package com.themistra.auth.apikey;

/**
 * Thrown by {@link ApiKeyService#revoke} when the key doesn't exist or isn't owned by the caller
 * — deliberately the same exception for both causes, so a caller probing key UUIDs they don't own
 * can't distinguish "doesn't exist" from "exists but isn't yours" (no enumeration oracle),
 * matching {@code AccountNotFoundException}'s stated reasoning.
 */
public class ApiKeyNotFoundException extends RuntimeException {
}
