package com.themistra.auth.mfa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain-JUnit unit tests for {@link MfaEnrollment} — the entity-level behavior that doesn't need
 * a real database (guards, null validation, defensive copying). Persistence-specific behavior
 * (mapping correctness, the DB's unique constraint, real UUID resolution) is covered by
 * {@link MfaPersistenceIntegrationTest} instead.
 */
class MfaEnrollmentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private MfaEnrollment newEnrollment() {
        return MfaEnrollment.create(1L, MfaEnrollment.Type.TOTP, new byte[]{1, 2, 3, 4}, CREATED_AT);
    }

    @Test // AC1
    void createStartsUnconfirmedWithNoLastUse() {
        MfaEnrollment enrollment = newEnrollment();

        assertThat(enrollment.getAccountId()).isEqualTo(1L);
        assertThat(enrollment.getType()).isEqualTo(MfaEnrollment.Type.TOTP);
        assertThat(enrollment.getConfirmedAt()).isNull();
        assertThat(enrollment.getLastUsedAt()).isNull();
        assertThat(enrollment.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test // Phase 8/9 fix: create() rejects null required arguments
    void createRejectsNullArguments() {
        byte[] secret = new byte[]{1};
        assertThatThrownBy(() -> MfaEnrollment.create(null, MfaEnrollment.Type.TOTP, secret, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MfaEnrollment.create(1L, null, secret, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MfaEnrollment.create(1L, MfaEnrollment.Type.TOTP, null, CREATED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MfaEnrollment.create(1L, MfaEnrollment.Type.TOTP, secret, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test // Phase 8/9 fix: create() defensively copies the caller's array
    void createDoesNotAliasTheCallersSecretArray() {
        byte[] original = new byte[]{1, 2, 3, 4};
        MfaEnrollment enrollment = MfaEnrollment.create(1L, MfaEnrollment.Type.TOTP, original, CREATED_AT);

        original[0] = 99;

        assertThat(enrollment.getSecretEncrypted()[0]).isEqualTo((byte) 1);
    }

    @Test // Phase 8/9 fix: getSecretEncrypted() returns a defensive copy, not the live reference
    void getSecretEncryptedReturnsADefensiveCopy() {
        MfaEnrollment enrollment = newEnrollment();

        byte[] returned = enrollment.getSecretEncrypted();
        returned[0] = 99;

        assertThat(enrollment.getSecretEncrypted()[0]).isEqualTo((byte) 1);
    }

    @Test // AC2
    void confirmSetsConfirmedAtInPlace() {
        MfaEnrollment enrollment = newEnrollment();
        Instant confirmedAt = CREATED_AT.plusSeconds(60);

        enrollment.confirm(confirmedAt);

        assertThat(enrollment.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test // AC2
    void confirmTwiceThrowsIllegalStateException() {
        MfaEnrollment enrollment = newEnrollment();
        Instant firstConfirmedAt = CREATED_AT.plusSeconds(60);
        enrollment.confirm(firstConfirmedAt);

        assertThatThrownBy(() -> enrollment.confirm(CREATED_AT.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already confirmed");
        // Phase 11 gap #5: the rejected second call must not have mutated the field before throwing.
        assertThat(enrollment.getConfirmedAt()).isEqualTo(firstConfirmedAt);
    }

    @Test // Phase 8/9 fix: confirm(null) must fail loudly, not silently no-op
    void confirmRejectsNullArgument() {
        MfaEnrollment enrollment = newEnrollment();

        assertThatThrownBy(() -> enrollment.confirm(null)).isInstanceOf(NullPointerException.class);
        assertThat(enrollment.getConfirmedAt()).isNull();
    }

    @Test
    void recordUseSetsLastUsedAt() {
        MfaEnrollment enrollment = newEnrollment();
        Instant usedAt = CREATED_AT.plusSeconds(30);

        enrollment.recordUse(usedAt);

        assertThat(enrollment.getLastUsedAt()).isEqualTo(usedAt);
    }

    @Test // Phase 8/9 fix: recordUse(null) must fail loudly, not erase a real timestamp
    void recordUseRejectsNullArgument() {
        MfaEnrollment enrollment = newEnrollment();
        Instant firstUse = CREATED_AT.plusSeconds(30);
        enrollment.recordUse(firstUse);

        assertThatThrownBy(() -> enrollment.recordUse(null)).isInstanceOf(NullPointerException.class);
        assertThat(enrollment.getLastUsedAt()).isEqualTo(firstUse);
    }
}
