package com.themistra.auth.apikey;

import com.themistra.auth.common.Hashing;
import org.springframework.stereotype.Component;

/**
 * The one place the task statement's "constant-time hash compare" requirement lives — a thin
 * wrapper over {@link Hashing}, kept as its own class per {@code design.md}'s package map, since
 * the API-key exchange comparison is the security-critical call site this codebase's other
 * hash-and-lookup patterns (recovery codes, verification tokens) don't need: those look up by
 * hash directly via a DB-level equality index, never comparing a caller-supplied value against a
 * candidate outside the database.
 */
@Component
public class ApiKeyHasher {

    /** SHA-256 hex digest of a full presented key ({@code prefix.secret}). */
    public String hash(String fullKey) {
        return Hashing.sha256(fullKey);
    }

    /** Whether {@code presentedFullKey} hashes to {@code storedHash}, compared in constant time. */
    public boolean matches(String presentedFullKey, String storedHash) {
        return Hashing.constantTimeEquals(hash(presentedFullKey), storedHash);
    }
}
