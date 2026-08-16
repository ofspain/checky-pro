package com.themistra.auth.token;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.token.dto.SessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit + Mockito, fixed {@link Clock} — no Spring context. Verifies the
 * frozen brief's D1–D5 behaviors directly, most importantly the revocation ordering
 * ({@link SessionService}'s own class Javadoc) and D3's per-family independence.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private RefreshTokenFamilyRepository familyRepository;

    @Mock
    private OAuth2AuthorizationService authorizationService;

    @Mock
    private AuditService auditService;

    private SessionService service;

    @BeforeEach
    void setUp() {
        // Constructed here, not as a field initializer: @Mock fields are injected by
        // MockitoExtension after the test instance's own field initializers already ran, so
        // building this at declaration time would capture null collaborators permanently.
        service = new SessionService(familyRepository, authorizationService, auditService, FIXED_CLOCK);
    }

    private RefreshTokenFamily newFamily(String authorizationId) {
        return RefreshTokenFamily.start(authorizationId, ACCOUNT_UUID.toString(), null, "hash-" + authorizationId, NOW);
    }

    @Test // R36
    void listMapsActiveFamiliesToResponses() {
        RefreshTokenFamily family = newFamily("auth-1");
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(ACCOUNT_UUID.toString()))
                .thenReturn(List.of(family));

        List<SessionResponse> result = service.list(ACCOUNT_UUID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).familyId()).isEqualTo(family.getFamilyId());
    }

    @Test // R36
    void listReturnsEmptyListWhenNoActiveSessions() {
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(ACCOUNT_UUID.toString()))
                .thenReturn(List.of());

        assertThat(service.list(ACCOUNT_UUID)).isEmpty();
    }

    @Test // R37 - removes the authorization, marks the family revoked, audits
    void revokeOneRevokesFamilyRemovesAuthorizationAndAudits() {
        RefreshTokenFamily family = newFamily("auth-1");
        UUID familyId = family.getFamilyId();
        when(familyRepository.findByFamilyIdAndPrincipalName(familyId, ACCOUNT_UUID.toString()))
                .thenReturn(Optional.of(family));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findById("auth-1")).thenReturn(authorization);

        service.revokeOne(ACCOUNT_UUID, familyId);

        assertThat(family.isRevoked()).isTrue();
        verify(authorizationService).remove(authorization);
        verify(familyRepository).save(family);
        verify(auditService).record(argThat((RecordAuditEventRequest req) ->
                req.eventType().equals("session.revoked")
                        && req.outcome() == AuditOutcome.SUCCESS
                        && req.accountUuid().equals(ACCOUNT_UUID)));
    }

    @Test // R37/AC7 - no enumeration oracle between "doesn't exist" and "not yours"
    void revokeOneThrowsWhenFamilyNotFoundOrNotOwned() {
        when(familyRepository.findByFamilyIdAndPrincipalName(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeOne(ACCOUNT_UUID, UUID.randomUUID()))
                .isInstanceOf(SessionNotFoundException.class);
        verifyNoInteractions(authorizationService);
        verifyNoInteractions(auditService);
    }

    @Test // D2 - a null authorization lookup is a no-op, not an error
    void revokeOneTreatsNullAuthorizationAsNoOp() {
        RefreshTokenFamily family = newFamily("auth-1");
        when(familyRepository.findByFamilyIdAndPrincipalName(family.getFamilyId(), ACCOUNT_UUID.toString()))
                .thenReturn(Optional.of(family));
        when(authorizationService.findById("auth-1")).thenReturn(null);

        assertThatCode(() -> service.revokeOne(ACCOUNT_UUID, family.getFamilyId())).doesNotThrowAnyException();

        assertThat(family.isRevoked()).isTrue();
        verify(authorizationService, never()).remove(any());
    }

    @Test // D1 - an already-revoked-but-owned family is found (no revokedAt filter) and the
          // existing idempotent revoke() takes over rather than 404ing
    void revokeOneOnAlreadyRevokedFamilyDoesNotThrow() {
        RefreshTokenFamily family = newFamily("auth-1");
        family.revoke("PRIOR_REASON", NOW.minusSeconds(60));
        when(familyRepository.findByFamilyIdAndPrincipalName(family.getFamilyId(), ACCOUNT_UUID.toString()))
                .thenReturn(Optional.of(family));
        when(authorizationService.findById("auth-1")).thenReturn(null);

        assertThatCode(() -> service.revokeOne(ACCOUNT_UUID, family.getFamilyId())).doesNotThrowAnyException();

        // idempotent - revoke() does not overwrite the original reason/timestamp
        assertThat(family.getRevokedReason()).isEqualTo("PRIOR_REASON");
    }

    @Test // ordering - the authorization is removed BEFORE the family is marked revoked
    void revokeOneRemovesAuthorizationBeforeMarkingFamilyRevoked() {
        RefreshTokenFamily family = newFamily("auth-1");
        when(familyRepository.findByFamilyIdAndPrincipalName(family.getFamilyId(), ACCOUNT_UUID.toString()))
                .thenReturn(Optional.of(family));
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorizationService.findById(eq("auth-1"))).thenAnswer(invocation -> {
            assertThat(family.isRevoked()).isFalse();
            return authorization;
        });

        service.revokeOne(ACCOUNT_UUID, family.getFamilyId());

        assertThat(family.isRevoked()).isTrue();
    }

    @Test // ordering - if authorization removal fails, the family must NOT be marked revoked
    void revokeOneDoesNotMarkFamilyRevokedWhenAuthorizationRemovalFails() {
        RefreshTokenFamily family = newFamily("auth-1");
        when(familyRepository.findByFamilyIdAndPrincipalName(family.getFamilyId(), ACCOUNT_UUID.toString()))
                .thenReturn(Optional.of(family));
        when(authorizationService.findById("auth-1")).thenThrow(new RuntimeException("transient failure"));

        assertThatThrownBy(() -> service.revokeOne(ACCOUNT_UUID, family.getFamilyId()))
                .isInstanceOf(RuntimeException.class);

        assertThat(family.isRevoked()).isFalse();
        verify(familyRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test // D3 - a failure on one family does not stop the others
    void revokeAllContinuesPastAFailureOnOneFamily() {
        RefreshTokenFamily familyA = newFamily("auth-a");
        RefreshTokenFamily familyB = newFamily("auth-b");
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(ACCOUNT_UUID.toString()))
                .thenReturn(List.of(familyA, familyB));
        when(authorizationService.findById("auth-a")).thenThrow(new RuntimeException("transient failure"));
        when(authorizationService.findById("auth-b")).thenReturn(null);

        assertThatCode(() -> service.revokeAll(ACCOUNT_UUID)).doesNotThrowAnyException();

        assertThat(familyA.isRevoked()).isFalse();
        assertThat(familyB.isRevoked()).isTrue();
        verify(familyRepository, never()).save(familyA);
        verify(familyRepository).save(familyB);
    }

    @Test // R38 - zero active sessions succeeds trivially
    void revokeAllSucceedsTriviallyWithZeroSessions() {
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(ACCOUNT_UUID.toString()))
                .thenReturn(List.of());

        assertThatCode(() -> service.revokeAll(ACCOUNT_UUID)).doesNotThrowAnyException();
        verifyNoInteractions(authorizationService);
        verifyNoInteractions(auditService);
    }

    @Test // R43 - one audit row per family revoked in a bulk operation
    void revokeAllAuditsEachFamilyIndependently() {
        RefreshTokenFamily familyA = newFamily("auth-a");
        RefreshTokenFamily familyB = newFamily("auth-b");
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(ACCOUNT_UUID.toString()))
                .thenReturn(List.of(familyA, familyB));
        when(authorizationService.findById(any())).thenReturn(null);

        service.revokeAll(ACCOUNT_UUID);

        verify(auditService, times(2)).record(any());
    }
}
