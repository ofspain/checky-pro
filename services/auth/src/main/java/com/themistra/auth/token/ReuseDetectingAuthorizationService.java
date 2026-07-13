package com.themistra.auth.token;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.common.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

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
        trackRefreshTokenIfPresent(authorization);
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
     * principalName is the account UUID for interactive grants (AccountUserDetailsService) but
     * may be a client_id string for other flows; only UUID-shaped principals are attributable
     * to an account in the audit row — anything else is recorded with accountUuid=null rather
     * than guessed at.
     */
    private void auditReuseDetected(String principalName) {
        UUID accountUuid = null;
        if (principalName != null) {
            try {
                accountUuid = UUID.fromString(principalName);
            } catch (IllegalArgumentException ignored) {
                // not an account principal; audited without an account attribution
            }
        }

        auditService.record(new RecordAuditEventRequest(
                "token.reuse_detected", AuditOutcome.FAILURE, accountUuid, null,
                null, null, null, null));
    }
}
