package com.themistra.auth.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

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

    public ReuseDetectingAuthorizationService(OAuth2AuthorizationService delegate,
                                              RefreshTokenTracker tracker) {
        this.delegate = delegate;
        this.tracker = tracker;
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
            String presentedHash = TokenHashing.sha256(token);
            var check = tracker.checkAndRegisterPresentation(presentedHash);

            if (check.outcome() == RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED) {
                log.warn("Purging authorization {} after refresh-token reuse", check.authorizationIdToPurge());
                OAuth2Authorization compromised = delegate.findById(check.authorizationIdToPurge());
                if (compromised != null) {
                    delegate.remove(compromised);
                }
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

        String hash = TokenHashing.sha256(refreshToken.getToken().getTokenValue());
        boolean isNewFamily = tracker.familyMissingFor(authorization.getId());

        if (isNewFamily) {
            tracker.trackIssuance(authorization.getId(), authorization.getPrincipalName(), null, hash);
        } else {
            tracker.trackRotation(authorization.getId(), hash);
        }
    }
}
