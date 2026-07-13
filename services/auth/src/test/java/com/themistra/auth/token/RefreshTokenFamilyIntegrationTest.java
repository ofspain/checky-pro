package com.themistra.auth.token;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.common.Hashing;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises RefreshTokenTracker against the real V2 schema (refresh_token_family,
 * refresh_token_archive) — the specific end-to-end guarantee D-003 exists for: presenting an
 * already-rotated refresh token revokes the family, and the family's new current hash stops
 * validating once revoked.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenFamilyIntegrationTest {

    @Autowired
    private RefreshTokenTracker tracker;

    @Test
    void issuanceRotationAndReuseRevokeTheWholeFamily() {
        String authorizationId = "auth-" + UUID.randomUUID();
        String principal = UUID.randomUUID().toString();
        String firstHash = Hashing.sha256("token-v1-" + authorizationId);
        String secondHash = Hashing.sha256("token-v2-" + authorizationId);

        assertThat(tracker.familyMissingFor(authorizationId)).isTrue();

        tracker.trackIssuance(authorizationId, principal, "integration-test-device", firstHash);
        assertThat(tracker.familyMissingFor(authorizationId)).isFalse();

        var validPresentation = tracker.checkAndRegisterPresentation(firstHash);
        assertThat(validPresentation.outcome())
                .isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.VALID);

        tracker.trackRotation(authorizationId, secondHash);

        // replaying the now-superseded first hash is exactly the attack D-003 defends against
        var reuse = tracker.checkAndRegisterPresentation(firstHash);
        assertThat(reuse.outcome()).isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.REUSE_DETECTED);
        assertThat(reuse.authorizationIdToPurge()).isEqualTo(authorizationId);
        assertThat(reuse.principalName()).isEqualTo(principal);

        // the family is now revoked — even its legitimate current hash no longer validates
        var afterRevocation = tracker.checkAndRegisterPresentation(secondHash);
        assertThat(afterRevocation.outcome()).isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.UNKNOWN);
    }

    @Test
    void unrelatedFamiliesDoNotInterfereWithEachOther() {
        String authA = "auth-" + UUID.randomUUID();
        String authB = "auth-" + UUID.randomUUID();
        String hashA = Hashing.sha256("token-a-" + authA);
        String hashB = Hashing.sha256("token-b-" + authB);

        tracker.trackIssuance(authA, UUID.randomUUID().toString(), null, hashA);
        tracker.trackIssuance(authB, UUID.randomUUID().toString(), null, hashB);

        assertThat(tracker.checkAndRegisterPresentation(hashA).outcome())
                .isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.VALID);
        assertThat(tracker.checkAndRegisterPresentation(hashB).outcome())
                .isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.VALID);
    }
}
