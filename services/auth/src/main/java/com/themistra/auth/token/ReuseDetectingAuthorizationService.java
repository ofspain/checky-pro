package com.themistra.auth.token;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.common.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.UUID;

/**
 * Decorates the delegate {@link OAuth2AuthorizationService} with refresh-token family tracking
 * and reuse detection (D-003), without altering how the delegate itself serializes or stores
 * an {@link OAuth2Authorization} — see D-016 for why value-hashing of the delegate's own
 * columns is deliberately out of scope for this pass, and why that's an acceptable interim.
 *
 * Wraps rather than replaces: all normal persistence, lookup by id, and access-token handling
 * go straight to the delegate. Only refresh-token bookkeeping and the reuse check are added.
 */
public class ReuseDetectingAuthorizationService implements OAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ReuseDetectingAuthorizationService.class);

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenTracker tracker;
    private final AuditService auditService;

    public ReuseDetectingAuthorizationService(OAuth2AuthorizationService delegate,
                                              RefreshTokenTracker tracker,
                                              AuditService auditService) {
        this.delegate = delegate;
        this.tracker = tracker;
        this.auditService = auditService;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        if (isRefreshTokenInvalidated(authorization)) {
            revokeFamilyForInvalidatedRefreshToken(authorization);
        } else {
            trackRefreshTokenIfPresent(authorization);
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        boolean isRefreshTokenLookup =
                tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType);

        if (isRefreshTokenLookup) {
            String presentedHash = Hashing.sha256(token);
            var check = tracker.checkAndRegisterPresentation(presentedHash);

            if (check.outcome() == RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED) {
                log.warn("Purging authorization {} after refresh-token reuse", check.authorizationIdToPurge());
                OAuth2Authorization compromised = delegate.findById(check.authorizationIdToPurge());
                if (compromised != null) {
                    delegate.remove(compromised);
                }
                auditReuseDetected(check.principalName());
                return null; // token endpoint sees an ordinary invalid_grant
            }
        }

        return delegate.findByToken(token, tokenType);
    }

    private void trackRefreshTokenIfPresent(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken =
                authorization.getRefreshToken();
        if (refreshToken == null) {
            return;
        }

        String hash = Hashing.sha256(refreshToken.getToken().getTokenValue());
        boolean isNewFamily = tracker.familyMissingFor(authorization.getId());

        if (isNewFamily) {
            tracker.trackIssuance(authorization.getId(), authorization.getPrincipalName(), null, hash);
        } else {
            tracker.trackRotation(authorization.getId(), hash);
        }
    }

    /**
     * Detects a SAS {@code /oauth2/revoke} call (T29, R39): SAS never calls {@link #remove}
     * to revoke — it calls {@code save} with the presented token's invalidated metadata flag
     * set (traced in SAS 1.5.1's {@code OAuth2TokenRevocationAuthenticationProvider}). Only a
     * refresh-token invalidation cascades to revoking the family; an access-token-only
     * invalidation (or an authorization with no refresh token at all, e.g. client-credentials)
     * leaves the family untouched, matching R39's own "called with a refresh token" scoping.
     */
    private static boolean isRefreshTokenInvalidated(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        return refreshToken != null && refreshToken.isInvalidated();
    }

    /**
     * Handles a revoke-shaped save exclusively of {@link #trackRefreshTokenIfPresent} (Kimi
     * Phase 8 Finding 1): calling both on the same save, in the order {@code save} originally
     * had them, would create a brand-new family via {@code trackIssuance} for an authorization
     * this decorator had never tracked before, then immediately revoke that just-created row —
     * a phantom family plus a misleading audit event for a session that was never active.
     */
    private void revokeFamilyForInvalidatedRefreshToken(OAuth2Authorization authorization) {
        boolean revoked;
        try {
            revoked = tracker.revokeForAuthorization(authorization.getId(), "OAUTH2_REVOKE");
        } catch (Exception e) {
            // D1's safe-failure-direction: the SAS-side token invalidation already committed
            // (delegate.save above); a family-revoke persistence failure must not surface as a
            // caller-visible error for a call SAS itself already treated as successful.
            log.error("Failed to revoke family for authorization {} during /oauth2/revoke",
                    authorization.getId(), e);
            return;
        }
        if (revoked) {
            auditSessionRevoked(authorization.getId(), authorization.getPrincipalName());
        }
    }

    private void auditSessionRevoked(String authorizationId, String principalName) {
        UUID accountUuid = parseAccountUuid(principalName);
        try {
            auditService.record(new RecordAuditEventRequest(
                    "session.revoked", AuditOutcome.SUCCESS, accountUuid, accountUuid,
                    null, null, null, null));
        } catch (Exception e) {
            // The revoke itself already succeeded (tracker.revokeForAuthorization returned true
            // before this call) - an audit failure must never undo it, so log and swallow rather
            // than rethrow.
            log.error("Failed to audit session revoke for authorization {}", authorizationId, e);
        }
    }

    /**
     * principalName is the account UUID for interactive grants (AccountUserDetailsService) but
     * may be a client_id string for other flows; only UUID-shaped principals are attributable
     * to an account in the audit row — anything else is recorded with accountUuid=null rather
     * than guessed at.
     */
    private void auditReuseDetected(String principalName) {
        auditService.record(new RecordAuditEventRequest(
                "token.reuse_detected", AuditOutcome.FAILURE, parseAccountUuid(principalName), null,
                null, null, null, null));
    }

    private static UUID parseAccountUuid(String principalName) {
        if (principalName == null) {
            return null;
        }
        try {
            return UUID.fromString(principalName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
