package com.themistra.auth.token;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.common.Hashing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises RefreshTokenTracker against the real V2 schema (refresh_token_family,
 * refresh_token_archive) — the specific end-to-end guarantee D-003 exists for: presenting an
 * already-rotated refresh token revokes the family, and the family's new current hash stops
 * validating once revoked.
 *
 * <p>Also covers T29 (R39, SAS {@code /oauth2/revoke} integration) at the D3-scoped level: calling
 * the real {@link OAuth2AuthorizationService} bean's {@code save(...)} directly with a
 * refresh-token-invalidated {@link OAuth2Authorization}, mirroring exactly what SAS's own
 * revocation provider does (traced in Phase 0/3) — not a full HTTP {@code /oauth2/revoke}
 * round-trip, which would need SAS client-credential plumbing out of this task's scope.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenFamilyIntegrationTest {

    @Autowired
    private RefreshTokenTracker tracker;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private AuthClientsProperties authClientsProperties;

    @Autowired
    private RefreshTokenFamilyRepository familyRepository;

    @Autowired
    private AccountService accountService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String PASSWORD = "correct-horse-battery";

    /** {@code auth_audit.account_uuid} has a real FK to {@code accounts} - a fabricated,
     * never-registered UUID silently produces zero audit rows (the audit write fails, but D5's
     * fail-open swallows it) rather than an exception, discovered only once this test's audit
     * assertion actually ran against a real database for the first time. */
    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

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

    // -------------------------------------------------------------------
    // T29 (R39) - saving an invalidated refresh token via the real OAuth2AuthorizationService
    // -------------------------------------------------------------------

    @Test // R39, D3 - the decorator's real save() path revokes the family for a genuine
          // /oauth2/revoke-shaped invalidation, proven against the real JDBC-backed delegate.
    void savingAnInvalidatedRefreshTokenRevokesTheFamily() {
        UUID principal = registerAndActivate("revoke-audit@example.com");
        RegisteredClient client =
                registeredClientRepository.findByClientId(authClientsProperties.spa().clientId());
        OAuth2RefreshToken refreshToken =
                new OAuth2RefreshToken("refresh-" + UUID.randomUUID(), Instant.now());
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(principal.toString())
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .token(newAccessToken())
                .refreshToken(refreshToken)
                .build();

        authorizationService.save(authorization); // issuance - the decorator creates the family
        assertThat(tracker.familyMissingFor(authorization.getId())).isFalse();

        Instant before = Instant.now().minusSeconds(1);
        OAuth2Authorization invalidated =
                OAuth2Authorization.from(authorization).invalidate(refreshToken).build();
        authorizationService.save(invalidated); // simulates SAS's /oauth2/revoke save

        String currentHash = Hashing.sha256(refreshToken.getTokenValue());
        assertThat(tracker.checkAndRegisterPresentation(currentHash).outcome())
                .isEqualTo(RefreshTokenTracker.ReuseCheckResult.Outcome.UNKNOWN);
        // Kimi Phase 11 Gap 1: assert the actual revocation reason, not just that the family is
        // no longer active (which the REUSE_DETECTED path could equally have produced).
        RefreshTokenFamily reloaded = familyRepository.findByAuthorizationId(authorization.getId())
                .orElseThrow();
        assertThat(reloaded.getRevokedReason()).isEqualTo("OAUTH2_REVOKE");
        assertThat(reloaded.getRevokedAt()).isNotNull();
        // Kimi Phase 11 Gap 2: exactly one audit row for this revoke.
        assertThat(countSessionRevokedAuditRows(principal, before)).isEqualTo(1L);
    }

    @Test // Kimi Phase 8 Finding 1 regression, proven end-to-end: a revoke-shaped save for an
          // authorization the decorator has never tracked before (its very first save is already
          // an invalidation - e.g. after a deploy/restore gap) must not create a phantom family.
    void savingAnInvalidatedRefreshTokenForAnUntrackedAuthorizationDoesNotCreateAPhantomFamily() {
        RegisteredClient client =
                registeredClientRepository.findByClientId(authClientsProperties.spa().clientId());
        OAuth2RefreshToken refreshToken =
                new OAuth2RefreshToken("refresh-" + UUID.randomUUID(), Instant.now());
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(UUID.randomUUID().toString())
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .token(newAccessToken())
                .refreshToken(refreshToken)
                .build();
        OAuth2Authorization alreadyInvalidated =
                OAuth2Authorization.from(authorization).invalidate(refreshToken).build();

        authorizationService.save(alreadyInvalidated); // first-ever save IS already a revoke

        assertThat(tracker.familyMissingFor(authorization.getId())).isTrue();
    }

    /** A real authorization always carries an access token alongside any refresh token;
     * {@code OAuth2Authorization.Builder.invalidate(refreshToken)} unconditionally invalidates the
     * access token too (it NPEs if one isn't present) - discovered via this exact test once Docker
     * was actually available to run it, since a mocked/unit test never exercises this path. */
    private static OAuth2AccessToken newAccessToken() {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-" + UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(600));
    }

    /** Mirrors {@code SessionIntegrationTest.countSessionRevokedAuditRows} (T28) - queried
     * natively since {@code AuditEventRepository} is package-private to the {@code audit} module. */
    private long countSessionRevokedAuditRows(UUID accountUuid, Instant since) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM auth_audit WHERE event_type = 'session.revoked' "
                                + "AND outcome = 'SUCCESS' AND account_uuid = :accountUuid AND occurred_at >= :since")
                .setParameter("accountUuid", accountUuid)
                .setParameter("since", since)
                .getSingleResult();
        return count.longValue();
    }
}
