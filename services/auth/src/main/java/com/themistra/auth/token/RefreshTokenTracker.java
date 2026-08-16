package com.themistra.auth.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh-token family lifecycle (D-003), independent of SAS's own persistence — see
 * V2 migration and D-016 for why this lives in dedicated tables rather than inside
 * oauth2_authorization. Pure domain logic over the two repositories; the SAS-facing
 * decorator ({@link ReuseDetectingAuthorizationService}) is the only caller.
 */
@Component
public class RefreshTokenTracker {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenTracker.class);

    private final RefreshTokenFamilyRepository familyRepository;
    private final RefreshTokenArchiveRepository archiveRepository;
    private final Clock clock;

    public RefreshTokenTracker(RefreshTokenFamilyRepository familyRepository,
                               RefreshTokenArchiveRepository archiveRepository,
                               Clock clock) {
        this.familyRepository = familyRepository;
        this.archiveRepository = archiveRepository;
        this.clock = clock;
    }

    /** True when no family exists yet for this authorization id — i.e., first issuance, not a rotation. */
    @Transactional(readOnly = true)
    public boolean familyMissingFor(String authorizationId) {
        return familyRepository.findByAuthorizationId(authorizationId).isEmpty();
    }

    /** Starts a new family when an authorization is first issued a refresh token. */
    @Transactional
    public void trackIssuance(String authorizationId, String principalName,
                              String deviceLabel, String refreshTokenHash) {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                authorizationId, principalName, deviceLabel, refreshTokenHash, clock.instant());
        familyRepository.save(family);
    }

    /**
     * Records a rotation for an authorization that already has a family. The superseded hash
     * is archived so a later replay of it is detectable. No-op (rather than an error) if the
     * hash didn't actually change, since SAS may re-save an authorization for unrelated reasons.
     */
    @Transactional
    public void trackRotation(String authorizationId, String newRefreshTokenHash) {
        familyRepository.findByAuthorizationId(authorizationId).ifPresent(family -> {
            if (family.getCurrentTokenHash().equals(newRefreshTokenHash) || family.isRevoked()) {
                return;
            }
            Instant now = clock.instant();
            archiveRepository.save(new RefreshTokenArchiveEntry(
                    family.getFamilyId(), family.getCurrentTokenHash(), now));
            family.rotateTo(newRefreshTokenHash, now);
        });
    }

    /**
     * Revokes every currently-unrevoked family for a principal (T07, password reset) — the first
     * caller of {@link RefreshTokenFamilyRepository#findByPrincipalNameAndRevokedAtIsNull} outside
     * this class. Families are JPA-managed within this transaction, so {@code revoke(...)}'s
     * mutation is flushed by dirty-checking at commit, the same way {@link #trackRotation} already
     * relies on for {@code rotateTo(...)} — no explicit save call needed.
     */
    @Transactional
    public void revokeAllForPrincipal(String principalName, String reason) {
        Instant now = clock.instant();
        familyRepository.findByPrincipalNameAndRevokedAtIsNull(principalName)
                .forEach(family -> family.revoke(reason, now));
    }

    /**
     * Revokes the single family for this authorization (T29, R39 — SAS {@code /oauth2/revoke}
     * integration), if one exists and isn't already revoked. Returns {@code true} only when a
     * revoke actually happened, so the caller (the audit-owning
     * {@link ReuseDetectingAuthorizationService}) knows whether to record an event — "not found"
     * and "already revoked" both report {@code false}, matching every other revoke path's
     * idempotency in this codebase.
     */
    @Transactional
    public boolean revokeForAuthorization(String authorizationId, String reason) {
        return familyRepository.findByAuthorizationId(authorizationId)
                .filter(family -> !family.isRevoked())
                .map(family -> {
                    family.revoke(reason, clock.instant());
                    familyRepository.save(family);
                    return true;
                })
                .orElse(false);
    }

    /**
     * The reuse check. Call before trusting a presented refresh token's hash:
     * - hash matches a family's CURRENT hash → legitimate, valid presentation.
     * - hash matches an ARCHIVED (superseded) hash → replay: family is revoked and the
     *   caller must also purge the live authorization from SAS's own store.
     * - hash matches neither → unknown token, ordinary invalid-grant (not a reuse event).
     */
    @Transactional
    public ReuseCheckResult checkAndRegisterPresentation(String presentedTokenHash) {
        if (familyRepository.findByCurrentTokenHashAndRevokedAtIsNull(presentedTokenHash).isPresent()) {
            return ReuseCheckResult.valid();
        }

        Optional<RefreshTokenArchiveEntry> archived = archiveRepository.findByTokenHash(presentedTokenHash);
        if (archived.isEmpty()) {
            return ReuseCheckResult.unknown();
        }

        UUID familyId = archived.get().getFamilyId();
        return familyRepository.findById(familyId)
                .map(family -> {
                    if (!family.isRevoked()) {
                        log.warn("Refresh token reuse detected: family={} principal={}",
                                familyId, family.getPrincipalName());
                        family.revoke("REUSE_DETECTED", clock.instant());
                    }
                    return ReuseCheckResult.reuseDetected(
                            family.getAuthorizationId(), family.getPrincipalName());
                })
                .orElseGet(ReuseCheckResult::unknown);
    }

    /** {@code principalName} is populated only for REUSE_DETECTED — the account UUID to audit. */
    public record ReuseCheckResult(Outcome outcome, String authorizationIdToPurge, String principalName) {

        public enum Outcome { VALID, UNKNOWN, REUSE_DETECTED }

        static ReuseCheckResult valid() {
            return new ReuseCheckResult(Outcome.VALID, null, null);
        }

        static ReuseCheckResult unknown() {
            return new ReuseCheckResult(Outcome.UNKNOWN, null, null);
        }

        static ReuseCheckResult reuseDetected(String authorizationId, String principalName) {
            return new ReuseCheckResult(Outcome.REUSE_DETECTED, authorizationId, principalName);
        }
    }
}
