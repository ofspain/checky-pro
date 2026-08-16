package com.themistra.auth.token;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.common.Hashing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReuseDetectingAuthorizationServiceTest {

    private static final String AUTHORIZATION_ID = "auth-1";
    private static final String PRESENTED_TOKEN = "opaque-refresh-token-value";
    private static final String PRESENTED_HASH = Hashing.sha256(PRESENTED_TOKEN);

    @Mock
    private OAuth2AuthorizationService delegate;

    @Mock
    private RefreshTokenTracker tracker;

    @Mock
    private AuditService auditService;

    private ReuseDetectingAuthorizationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new ReuseDetectingAuthorizationService(delegate, tracker, auditService);
    }

    private OAuth2Authorization authorizationWithRefreshToken(String tokenValue) {
        OAuth2RefreshToken refreshToken = mock(OAuth2RefreshToken.class);
        when(refreshToken.getTokenValue()).thenReturn(tokenValue);

        @SuppressWarnings("unchecked")
        OAuth2Authorization.Token<OAuth2RefreshToken> tokenHolder = mock(OAuth2Authorization.Token.class);
        when(tokenHolder.getToken()).thenReturn(refreshToken);

        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getId()).thenReturn(AUTHORIZATION_ID);
        lenient().when(authorization.getPrincipalName()).thenReturn("principal-uuid");
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
                eq(Hashing.sha256("raw-token-value")));
        verify(tracker, never()).trackRotation(any(), any());
    }

    @Test
    void saveTracksRotationWhenFamilyAlreadyExists() {
        OAuth2Authorization authorization = authorizationWithRefreshToken("rotated-token-value");
        when(tracker.familyMissingFor(AUTHORIZATION_ID)).thenReturn(false);

        service.save(authorization);

        verify(tracker).trackRotation(AUTHORIZATION_ID, Hashing.sha256("rotated-token-value"));
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
                        RefreshTokenTracker.ReuseCheckResult.Outcome.VALID, null, null));
        OAuth2Authorization expected = mock(OAuth2Authorization.class);
        when(delegate.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(expected);

        OAuth2Authorization result =
                service.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isSameAs(expected);
        verify(auditService, never()).record(any());
    }

    @Test
    void findByTokenPurgesAndReturnsNullOnReuse() {
        UUID accountUuid = UUID.randomUUID();
        when(tracker.checkAndRegisterPresentation(PRESENTED_HASH))
                .thenReturn(new RefreshTokenTracker.ReuseCheckResult(
                        RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED,
                        AUTHORIZATION_ID, accountUuid.toString()));
        OAuth2Authorization compromised = mock(OAuth2Authorization.class);
        when(delegate.findById(AUTHORIZATION_ID)).thenReturn(compromised);

        OAuth2Authorization result =
                service.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        assertThat(result).isNull();
        verify(delegate).remove(compromised);
        verify(delegate, never()).findByToken(any(), any());

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("token.reuse_detected");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(captor.getValue().accountUuid()).isEqualTo(accountUuid);
    }

    @Test
    void reuseAuditRecordsNullAccountWhenPrincipalIsNotAUuid() {
        when(tracker.checkAndRegisterPresentation(PRESENTED_HASH))
                .thenReturn(new RefreshTokenTracker.ReuseCheckResult(
                        RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED,
                        AUTHORIZATION_ID, "some-client-id"));
        when(delegate.findById(AUTHORIZATION_ID)).thenReturn(mock(OAuth2Authorization.class));

        service.findByToken(PRESENTED_TOKEN, OAuth2TokenType.REFRESH_TOKEN);

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().accountUuid()).isNull();
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
        verify(auditService, never()).record(any());
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
