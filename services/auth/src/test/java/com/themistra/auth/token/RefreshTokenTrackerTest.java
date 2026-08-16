package com.themistra.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenTrackerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final String AUTHORIZATION_ID = "auth-1";
    private static final String PRINCIPAL = UUID.randomUUID().toString();
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Mock
    private RefreshTokenFamilyRepository familyRepository;

    @Mock
    private RefreshTokenArchiveRepository archiveRepository;

    private RefreshTokenTracker tracker;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        tracker = new RefreshTokenTracker(familyRepository, archiveRepository, fixed);
    }

    @Test
    void familyMissingForReturnsTrueWhenNoFamilyExists() {
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.empty());

        assertThat(tracker.familyMissingFor(AUTHORIZATION_ID)).isTrue();
    }

    @Test
    void trackIssuanceCreatesFamilyWithGivenHashAsCurrent() {
        tracker.trackIssuance(AUTHORIZATION_ID, PRINCIPAL, "iphone-15", HASH_A);

        ArgumentCaptor<RefreshTokenFamily> captor = ArgumentCaptor.forClass(RefreshTokenFamily.class);
        verify(familyRepository).save(captor.capture());

        RefreshTokenFamily saved = captor.getValue();
        assertThat(saved.getAuthorizationId()).isEqualTo(AUTHORIZATION_ID);
        assertThat(saved.getPrincipalName()).isEqualTo(PRINCIPAL);
        assertThat(saved.getCurrentTokenHash()).isEqualTo(HASH_A);
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void trackRotationArchivesOldHashAndAdvancesCurrent() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_A, NOW.minusSeconds(60));
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.of(family));

        tracker.trackRotation(AUTHORIZATION_ID, HASH_B);

        ArgumentCaptor<RefreshTokenArchiveEntry> archived =
                ArgumentCaptor.forClass(RefreshTokenArchiveEntry.class);
        verify(archiveRepository).save(archived.capture());
        assertThat(archived.getValue().getTokenHash()).isEqualTo(HASH_A);
        assertThat(archived.getValue().getFamilyId()).isEqualTo(family.getFamilyId());

        assertThat(family.getCurrentTokenHash()).isEqualTo(HASH_B);
    }

    @Test
    void trackRotationIsNoOpWhenHashUnchanged() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_A, NOW);
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.of(family));

        tracker.trackRotation(AUTHORIZATION_ID, HASH_A);

        verify(archiveRepository, never()).save(any());
    }

    @Test
    void trackRotationIsNoOpOnAlreadyRevokedFamily() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_A, NOW);
        family.revoke("REUSE_DETECTED", NOW);
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.of(family));

        tracker.trackRotation(AUTHORIZATION_ID, HASH_B);

        verify(archiveRepository, never()).save(any());
        assertThat(family.getCurrentTokenHash()).isEqualTo(HASH_A);
    }

    @Test
    void presentingCurrentHashIsValid() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_A, NOW);
        when(familyRepository.findByCurrentTokenHashAndRevokedAtIsNull(HASH_A))
                .thenReturn(Optional.of(family));

        var result = tracker.checkAndRegisterPresentation(HASH_A);

        assertThat(result.outcome()).isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.VALID);
    }

    @Test
    void presentingUnknownHashIsUnknownNotReuse() {
        when(familyRepository.findByCurrentTokenHashAndRevokedAtIsNull(HASH_A)).thenReturn(Optional.empty());
        when(archiveRepository.findByTokenHash(HASH_A)).thenReturn(Optional.empty());

        var result = tracker.checkAndRegisterPresentation(HASH_A);

        assertThat(result.outcome()).isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.UNKNOWN);
    }

    @Test
    void presentingArchivedHashRevokesWholeFamilyAndReportsAuthorizationToPurge() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_B, NOW.minusSeconds(120));
        UUID familyId = family.getFamilyId();

        when(familyRepository.findByCurrentTokenHashAndRevokedAtIsNull(HASH_A)).thenReturn(Optional.empty());
        when(archiveRepository.findByTokenHash(HASH_A))
                .thenReturn(Optional.of(new RefreshTokenArchiveEntry(familyId, HASH_A, NOW.minusSeconds(60))));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        var result = tracker.checkAndRegisterPresentation(HASH_A);

        assertThat(result.outcome()).isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED);
        assertThat(result.authorizationIdToPurge()).isEqualTo(AUTHORIZATION_ID);
        assertThat(family.isRevoked()).isTrue();
        assertThat(family.getRevokedReason()).isEqualTo("REUSE_DETECTED");
    }

    @Test
    void reuseCheckIsIdempotentOnAlreadyRevokedFamily() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_B, NOW.minusSeconds(120));
        family.revoke("REUSE_DETECTED", NOW.minusSeconds(30));
        Instant firstRevokedAt = family.getRevokedAt();
        UUID familyId = family.getFamilyId();

        when(familyRepository.findByCurrentTokenHashAndRevokedAtIsNull(HASH_A)).thenReturn(Optional.empty());
        when(archiveRepository.findByTokenHash(HASH_A))
                .thenReturn(Optional.of(new RefreshTokenArchiveEntry(familyId, HASH_A, NOW.minusSeconds(90))));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

        tracker.checkAndRegisterPresentation(HASH_A);

        assertThat(family.getRevokedAt()).isEqualTo(firstRevokedAt); // revoke() didn't overwrite it
    }

    @Test
    void revokeAllForPrincipalRevokesEveryUnrevokedFamilyForThatPrincipal() {
        RefreshTokenFamily familyOne = RefreshTokenFamily.start(
                "auth-1", PRINCIPAL, null, HASH_A, NOW.minusSeconds(120));
        RefreshTokenFamily familyTwo = RefreshTokenFamily.start(
                "auth-2", PRINCIPAL, "second-device", HASH_B, NOW.minusSeconds(60));
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(PRINCIPAL))
                .thenReturn(List.of(familyOne, familyTwo));

        tracker.revokeAllForPrincipal(PRINCIPAL, "PASSWORD_RESET");

        assertThat(familyOne.isRevoked()).isTrue();
        assertThat(familyOne.getRevokedReason()).isEqualTo("PASSWORD_RESET");
        assertThat(familyOne.getRevokedAt()).isEqualTo(NOW);
        assertThat(familyTwo.isRevoked()).isTrue();
        assertThat(familyTwo.getRevokedReason()).isEqualTo("PASSWORD_RESET");
        assertThat(familyTwo.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revokeAllForPrincipalDoesNotTouchAnotherPrincipalsFamilies() {
        String otherPrincipal = UUID.randomUUID().toString();
        RefreshTokenFamily otherPrincipalsFamily = RefreshTokenFamily.start(
                "auth-other", otherPrincipal, null, HASH_A, NOW.minusSeconds(60));
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(PRINCIPAL)).thenReturn(List.of());

        tracker.revokeAllForPrincipal(PRINCIPAL, "PASSWORD_RESET");

        assertThat(otherPrincipalsFamily.isRevoked()).isFalse();
        verify(familyRepository, never()).findByPrincipalNameAndRevokedAtIsNull(otherPrincipal);
    }

    @Test
    void revokeAllForPrincipalIsANoOpWhenNoFamiliesExist() {
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(PRINCIPAL)).thenReturn(List.of());

        assertThatCode(() -> tracker.revokeAllForPrincipal(PRINCIPAL, "PASSWORD_RESET"))
                .doesNotThrowAnyException();
    }

    @Test
    void revokeAllForPrincipalIsIdempotentOnASecondCall() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                "auth-1", PRINCIPAL, null, HASH_A, NOW.minusSeconds(60));
        when(familyRepository.findByPrincipalNameAndRevokedAtIsNull(PRINCIPAL))
                .thenReturn(List.of(family))
                // The real query filters RevokedAtIsNull; a second call finds nothing left to revoke.
                .thenReturn(List.of());

        tracker.revokeAllForPrincipal(PRINCIPAL, "PASSWORD_RESET");
        Instant firstRevokedAt = family.getRevokedAt();
        tracker.revokeAllForPrincipal(PRINCIPAL, "PASSWORD_RESET");

        assertThat(family.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    // -------------------------------------------------------------------
    // revokeForAuthorization (T29, R39 - SAS /oauth2/revoke integration)
    // -------------------------------------------------------------------

    @Test
    void revokeForAuthorizationRevokesExistingUnrevokedFamilyAndReturnsTrue() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_A, NOW.minusSeconds(60));
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.of(family));

        boolean result = tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE");

        assertThat(result).isTrue();
        assertThat(family.isRevoked()).isTrue();
        assertThat(family.getRevokedReason()).isEqualTo("OAUTH2_REVOKE");
        assertThat(family.getRevokedAt()).isEqualTo(NOW);
        verify(familyRepository).save(family);
    }

    @Test
    void revokeForAuthorizationIsANoOpOnAlreadyRevokedFamilyAndReturnsFalse() {
        RefreshTokenFamily family = RefreshTokenFamily.start(
                AUTHORIZATION_ID, PRINCIPAL, null, HASH_A, NOW.minusSeconds(60));
        family.revoke("REUSE_DETECTED", NOW.minusSeconds(30));
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.of(family));

        boolean result = tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE");

        assertThat(result).isFalse();
        assertThat(family.getRevokedReason()).isEqualTo("REUSE_DETECTED"); // untouched, not overwritten
        verify(familyRepository, never()).save(any());
    }

    @Test
    void revokeForAuthorizationReturnsFalseWhenNoFamilyExists() {
        when(familyRepository.findByAuthorizationId(AUTHORIZATION_ID)).thenReturn(Optional.empty());

        boolean result = tracker.revokeForAuthorization(AUTHORIZATION_ID, "OAUTH2_REVOKE");

        assertThat(result).isFalse();
        verify(familyRepository, never()).save(any());
    }
}
