package com.themistra.auth.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Package-private on purpose, consistent with {@code MfaEnrollmentRepository}/
 * {@code RecoveryCodeRepository}: only this module's future service (task 24) touches this.
 */
interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Looks up API keys by their public {@code prefix}. Returns a {@code List}, not an
     * {@code Optional}: unlike {@code key_hash}, {@code prefix} has no DB-level {@code UNIQUE}
     * constraint (frozen brief, Phase 3 finding #4) — an {@code Optional} return type would
     * silently assume a uniqueness the schema doesn't enforce. Callers (task 25's exchange flow)
     * are responsible for deciding how to handle more than one match.
     */
    List<ApiKey> findByPrefix(String prefix);
}
