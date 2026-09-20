package com.themistra.auth.token;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.token.dto.SessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Self-service session (refresh-token family) listing and revocation (T28, R36/R37/R38).
 *
 * <p><strong>Revocation order is load-bearing, not cosmetic:</strong> tracing
 * {@link ReuseDetectingAuthorizationService#findByToken}, once a family's {@code revokedAt} is
 * set, {@link RefreshTokenTracker#checkAndRegisterPresentation} no longer matches it as
 * {@code VALID} (excluded by {@code findByCurrentTokenHashAndRevokedAtIsNull}) — but since it is
 * the family's *current*, never-superseded token, it is not in the archive table either, so the
 * check returns {@code UNKNOWN}. An {@code UNKNOWN} outcome is not blocked: {@code findByToken}
 * falls through to the raw SAS lookup regardless. <strong>Marking a family revoked does not, by
 * itself, stop its token from working</strong> if the live {@code oauth2_authorization} row is
 * still present — only removing that row does. Every revoke here therefore removes the SAS
 * authorization <em>first</em>; only if that succeeds is the family marked revoked. A family is
 * never marked revoked after a failed authorization removal — that would be a strictly worse,
 * misleading state (looks revoked, still works) than an honestly-failed, retryable revoke.</p>
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final RefreshTokenFamilyRepository familyRepository;
    private final OAuth2AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public SessionService(RefreshTokenFamilyRepository familyRepository,
                          OAuth2AuthorizationService authorizationService,
                          AuditService auditService, Clock clock) {
        this.familyRepository = familyRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** The caller's active sessions (R36). Deliberately active-only (frozen brief D4) — matches
     * both R36's literal wording and the existing repository query, no join against SAS's own
     * authorization expiry. */
    @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID accountUuid) {
        return familyRepository.findByPrincipalNameAndRevokedAtIsNull(accountUuid.toString()).stream()
                .map(SessionResponse::from)
                .toList();
    }

    /**
     * Revokes one family the caller owns (R37). {@link SessionNotFoundException} — thrown
     * identically whether {@code familyId} doesn't exist at all or exists but isn't owned by the
     * caller (no enumeration oracle) — propagates uncaught for {@link SessionExceptionHandler} to
     * translate to a uniform 404. An already-revoked-but-owned family is still found (frozen
     * brief D1, no {@code revokedAt} filter on the lookup) and revoked idempotently by
     * {@link RefreshTokenFamily#revoke}, returning success rather than 404.
     */
    @Transactional
    public void revokeOne(UUID accountUuid, UUID familyId) {
        RefreshTokenFamily family = familyRepository
                .findByFamilyIdAndPrincipalName(familyId, accountUuid.toString())
                .orElseThrow(SessionNotFoundException::new);
        revokeFamily(family, "USER_REVOKED", clock.instant());
    }

    /**
     * Revokes every active family the caller owns (R38), best-effort per family (frozen brief
     * D3): a failure revoking one family is logged and does not prevent the others from being
     * revoked. <strong>Deliberately not {@code @Transactional}</strong> — wrapping this loop in
     * one transaction would silently defeat D3 regardless of the {@code try/catch} below, since a
     * rollback would undo every earlier iteration's JPA-side change too. Correctness instead
     * relies on {@code revokeFamily}'s own {@code familyRepository.save(...)} call getting its own
     * implicit transaction from {@code SimpleJpaRepository}'s default {@code @Transactional} —
     * exactly the per-family independence D3 requires, with no explicit transaction management.
     */
    public void revokeAll(UUID accountUuid) {
        Instant now = clock.instant();
        for (RefreshTokenFamily family
                : familyRepository.findByPrincipalNameAndRevokedAtIsNull(accountUuid.toString())) {
            try {
                revokeFamily(family, "USER_REVOKED_ALL", now);
            } catch (Exception e) {
                log.error("Failed to revoke session family {} during bulk revoke", family.getFamilyId(), e);
            }
        }
    }

    private void revokeFamily(RefreshTokenFamily family, String reason, Instant now) {
        removeSasAuthorizationIfPresent(family.getAuthorizationId());
        family.revoke(reason, now);
        familyRepository.save(family);
        recordAudit(family);
    }

    /** D2: a {@code null} result means the authorization is already gone — a no-op, not an error.
     * Mirrors {@link ReuseDetectingAuthorizationService#findByToken}'s identical defensive check
     * against the same {@code OAuth2AuthorizationService} null-return contract. */
    private void removeSasAuthorizationIfPresent(String authorizationId) {
        OAuth2Authorization authorization = authorizationService.findById(authorizationId);
        if (authorization != null) {
            authorizationService.remove(authorization);
        }
    }

    /** {@code principalName} is the account UUID for interactive grants; a non-UUID principal
     * (out of this task's scope, frozen brief D5) is audited without an account attribution
     * rather than guessed at — mirrors {@code ReuseDetectingAuthorizationService.auditReuseDetected}. */
    private void recordAudit(RefreshTokenFamily family) {
        UUID accountUuid = null;
        try {
            accountUuid = UUID.fromString(family.getPrincipalName());
        } catch (IllegalArgumentException ignored) {
            // not an account principal; audited without an account attribution
        }

        auditService.record(new RecordAuditEventRequest(
                "session.revoked", AuditOutcome.SUCCESS, accountUuid, accountUuid, null, null, null, null));
    }
}
