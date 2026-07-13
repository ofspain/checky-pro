package com.themistra.auth.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReuseDetectingAuthorizationServiceTest {

    private static final String AUTHORIZATION_ID = "auth-1";
    private static final String PRESENTED_TOKEN = "opaque-refresh-token-value";
    private static final String PRESENTED_HASH = TokenHashing.sha256(PRESENTED_TOKEN);

    @Mock
    private OAuth2AuthorizationService delegate;

    @Mock
    private RefreshTokenTracker tracker;

    private ReuseDetectingAuthorizationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new ReuseDetectingAuthorizationService(delegate, tracker);
    }

    private OAuth2Authorization authorizationWithRefreshToken(String tokenValue) {
        OAuth2RefreshToken refreshToken = mock(OAuth2RefreshToken.class);
        when(refreshToken.getTokenValue()).thenReturn(tokenValue);

        @SuppressWarnings("unchecked")
        OAuth2Authorization.Token<OAuth2RefreshToken> tokenHolder = mock(OAuth2Authorization.Token.class);
        when(tokenHolder.getToken()).thenReturn(refreshToken);

        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getId()).thenReturn(AUTHORIZATION_ID);
        when(authorization.getPrincipalName()).thenReturn("principal-uuid");
        when(authorization.getRefreshToken()).thenReturn(tokenHolder);
        return authorization;
    }

    @Test
    void saveDelegatesFirstThenTracksNewIssuanceWhenNoExistingFamily() {
        OAuth2Authorization authorization = authorizationWithRefreshToken("raw-token-value");
        when(tracker.familyMissingFor(AUTHORIZATION_ID)).thenReturn(true);

        service.save(authorization);

        verify(delegate).save(authorization);
        verify(tracker).trackIssuance(
                eq(AUTHORIZATION_ID), eq("principal-uuid"), any(),
                eq(TokenHashing.sha256("raw-token-value")));
        verify(tracker, never()).trackRotation(any(), any());
    }

    @Test
    void saveTracksRotationWhenFamilyAlreadyExists() {
        OAuth2Authorization authorization = authorizationWithRefreshToken("rotated-token-value");
        when(tracker.familyMissingFor(AUTHORIZATION_ID)).thenReturn(false);

        service.save(authorization);

        verify(tracker).trackRotation(AUTHORIZATION_ID, TokenHashing.sha256("rotated-token-value"));
        verify(tracker, never()).trackIssuance(any(), any(), any(), any());
    }

    @Test
    void saveSkipsTrackingWhenAuthorizationHasNoRefreshToken() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getRefreshToken()).thenReturn(null);

        service.save(authorization);

        verify(delegate).save(authorization);
        verify(tracker, never()).trackIssuance(any(), any(), any(), any());
        verify(tracker, never()).trackRotation(any(), any());
    }

    @Test
    void findByTokenReturnsDelegateResultOnValidPresentation() {
        when(tracker.checkAndRegisterPresentation(PRESENTED_HASH))
                .thenReturn(new RefreshTokenTracker.ReuseCheckResult(
                        RefreshTokenTracker.ReuseCheckResult.Outcome.VALID, null));
        OAuth2Authorization expected = mock(OAuth2Authorization.class);
        when(delegate.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(expected);

        OAuth2Authorization result =
                service.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void findByTokenPurgesAndReturnsNullOnReuse() {
        when(tracker.checkAndRegisterPresentation(PRESENTED_HASH))
                .thenReturn(new RefreshTokenTracker.ReuseCheckResult(
                        RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED, AUTHORIZATION_ID));
        OAuth2Authorization compromised = mock(OAuth2Authorization.class);
        when(delegate.findById(AUTHORIZATION_ID)).thenReturn(compromised);

        OAuth2Authorization result =
                service.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isNull();
        verify(delegate).remove(compromised);
        verify(delegate, never()).findByToken(any(), any());
    }

    @Test
    void findByTokenSkipsReuseCheckForAccessTokenLookups() {
        OAuth2Authorization expected = mock(OAuth2Authorization.class);
        when(delegate.findByToken("access-token-value", OAuth2TokenType.ACCESS_TOKEN))
                .thenReturn(expected);

        OAuth2Authorization result =
                service.findByToken("access-token-value", OAuth2TokenType.ACCESS_TOKEN);

        assertThat(result).isSameAs(expected);
        verify(tracker, never()).checkAndRegisterPresentation(any());
    }

    @Test
    void findByIdAndRemoveDelegateDirectly() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(delegate.findById("some-id")).thenReturn(authorization);

        assertThat(service.findById("some-id")).isSameAs(authorization);

        service.remove(authorization);
        verify(delegate).remove(authorization);
    }
}
