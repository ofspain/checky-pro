package com.themistra.auth.token;

/**
 * Thrown by {@link SessionService#revokeOne} when the family doesn't exist or isn't owned by the
 * caller — deliberately the same exception for both causes, so a caller probing family ids they
 * don't own can't distinguish "doesn't exist" from "exists but isn't yours" (no enumeration
 * oracle), matching {@code ApiKeyNotFoundException}'s established reasoning.
 */
public class SessionNotFoundException extends RuntimeException {
}
