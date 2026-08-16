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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    /** T29 - a save shaped like SAS's {@code /oauth2/revoke} call: the refresh token is present
     * but its invalidated metadata flag is set. Never stubs {@code getToken()}/{@code getTokenValue()}
     * since the revoke path never needs the raw token value, only the invalidated flag. */
    private OAuth2Authorization invalidatedRefreshTokenAuthorization(String principalName) {
        @SuppressWarnings("unchecked")
        OAuth2Authorization.Token<OAuth2RefreshToken> tokenHolder = mock(OAuth2Authorization.Token.class);
        when(tokenHolder.isInvalidated()).thenReturn(true);

        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getId()).thenReturn(AUTHORIZATION_ID);
        lenient().when(authorization.getPrincipalName()).thenReturn(principalName);
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
        verify(tracker, never()).revokeForAuthorization(any(), any());
    }

    @Test
    void saveDoesNotRevokeWhenOnlyAccessTokenInvalidated() {
        // authorizationWithRefreshToken's mock refreshToken.isInvalidated() is unstubbed -> false
        OAuth2Authorization authorization = authorizationWithRefreshToken("rotated-token-value");
        when(tracker.familyMissingFor(AUTHORIZATION_ID)).thenReturn(false);

        service.save(authorization);

        verify(tracker, never()).revokeForAuthorization(any(), any());
        verify(auditService, never()).record(any());
    }

    // -------------------------------------------------------------------
    // save(...) when the refresh token IS invalidated (T29, R39 - SAS /oauth2/revoke)
    // -------------------------------------------------------------------

    @Test
    void saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated() {
        String principal = UUID.randomUUID().toString();
        OAuth2Authorization authorization = invalidatedRefreshTokenAuthorization(principal);
        when(tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE")).thenReturn(true);

        service.save(authorization);

        verify(delegate).save(authorization);
        verify(tracker).revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE");

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("session.revoked");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(captor.getValue().accountUuid()).isEqualTo(UUID.fromString(principal));
        assertThat(captor.getValue().actorUuid()).isEqualTo(UUID.fromString(principal));
    }

    @Test // Kimi Phase 8 Finding 1 regression: a revoke-shaped save must never also trigger
          // trackIssuance/trackRotation for the same authorization, whether or not a family
          // already existed for it - the two paths are mutually exclusive within one save() call.
    void saveNeverTracksIssuanceOrRotationWhenRefreshTokenIsInvalidated() {
        OAuth2Authorization authorization = invalidatedRefreshTokenAuthorization("principal-uuid");
        when(tracker.revokeForAuthorization(any(), any())).thenReturn(true);

        service.save(authorization);

        verify(tracker, never()).familyMissingFor(any());
        verify(tracker, never()).trackIssuance(any(), any(), any(), any());
        verify(tracker, never()).trackRotation(any(), any());
    }

    @Test // AC2 / Finding 2 (Phase 3) - the no-op ("already revoked", or "no family ever existed")
          // path must not audit, since nothing was actually revoked.
    void saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp() {
        OAuth2Authorization authorization = invalidatedRefreshTokenAuthorization("principal-uuid");
        when(tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE")).thenReturn(false);

        service.save(authorization);

        verify(auditService, never()).record(any());
    }

    @Test // Mirrors reuseAuditRecordsNullAccountWhenPrincipalIsNotAUuid's existing fallback pattern
    void saveAuditsWithNullAccountWhenPrincipalIsNotAUuid() {
        OAuth2Authorization authorization = invalidatedRefreshTokenAuthorization("some-client-id");
        when(tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE")).thenReturn(true);

        service.save(authorization);

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().accountUuid()).isNull();
        assertThat(captor.getValue().actorUuid()).isNull();
    }

    @Test // D2 - an audit failure must never surface to the caller; the revoke already happened.
    void saveSwallowsAuditFailureWithoutPropagating() {
        OAuth2Authorization authorization = invalidatedRefreshTokenAuthorization("principal-uuid");
        when(tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE")).thenReturn(true);
        doThrow(new RuntimeException("audit backend down")).when(auditService).record(any());

        assertThatCode(() -> service.save(authorization)).doesNotThrowAnyException();
    }

    @Test // Kimi Phase 8 Finding 2 - a family-revoke persistence failure must not surface as a
          // caller-visible error for a call SAS itself already treated as successful, and must
          // never reach the audit call with an indeterminate "revoked" state.
    void saveSwallowsRevokeFailureWithoutPropagatingOrAuditing() {
        OAuth2Authorization authorization = invalidatedRefreshTokenAuthorization("principal-uuid");
        when(tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE"))
                .thenThrow(new RuntimeException("transient DB failure"));

        assertThatCode(() -> service.save(authorization)).doesNotThrowAnyException();

        verify(auditService, never()).record(any());
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
