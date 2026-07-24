package com.themistra.auth.account;

import com.themistra.auth.account.VerificationTokenService.VerificationTokenResult;
import com.themistra.auth.common.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VerificationTokenService} — R3–R5, L5. Plain JUnit + Mockito, no Spring
 * context, fixed {@link Clock}, per {@code agents.md}. The repository layer is mocked (same
 * pattern as {@code AccountServiceTest}) — DB-level atomicity of the {@code @Modifying} queries
 * themselves is not provable by a unit test; these tests verify the service correctly interprets
 * the repository's contract (e.g., "0 rows affected" from {@code markConsumed}).
 *
 * <p>Every account fixture is built via {@link #usableAccount} into a local variable *before* it
 * is handed to {@code when(...).thenReturn(...)} — inlining the mock-construction call as a
 * {@code thenReturn} argument confuses Mockito's stubbing state (its own {@code when(...)} call
 * starts before the outer one completes).</p>
 */
@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final Long ACCOUNT_ID = 42L;
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private AccountRepository accountRepository;

    private VerificationTokenService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        VerificationTokenProperties properties = new VerificationTokenProperties(30);
        service = new VerificationTokenService(tokenRepository, accountRepository, properties, fixedClock);
    }

    @Test
    void shouldActivateAccountWithValidVerificationToken() {
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        when(tokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationTokenResult issued = service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);

        assertThat(issued.accountUuid()).isEqualTo(ACCOUNT_UUID);
        assertThat(issued.purpose()).isEqualTo(VerificationToken.Purpose.EMAIL_VERIFY);
        assertThat(issued.token().getTokenHash()).isEqualTo(Hashing.sha256(issued.rawToken()));
        assertThat(issued.token().getExpiresAt()).isEqualTo(NOW.plusSeconds(30 * 60));
        assertThat(issued.token().getCreatedAt()).isEqualTo(NOW);
        assertThat(issued.token().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(issued.token().getPurpose()).isEqualTo(VerificationToken.Purpose.EMAIL_VERIFY);

        // Now redeem the same raw token via consume() and confirm it resolves to the account.
        String hash = issued.token().getTokenHash();
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(issued.token()));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(tokenRepository.markConsumed(eq(hash), any())).thenReturn(1);

        assertThat(service.consume(issued.rawToken())).contains(ACCOUNT_UUID);
    }

    @Test
    void shouldNotRevealAccountExistenceForInvalidVerificationToken() {
        // Not found.
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThat(service.verify("nonexistent-token")).isEmpty();
        assertThat(service.consume("nonexistent-token")).isEmpty();

        // Expired - verify() path (in-service expiry check).
        String expiredRaw = "expired-token";
        stubToken(expiredRaw, NOW.minusSeconds(1), null);
        assertThat(service.verify(expiredRaw)).isEmpty();

        // Expired - consume() path (markConsumed's own WHERE expiresAt > :now excludes it; the
        // account must still be usable so the pre-check doesn't short-circuit before markConsumed
        // is even attempted, keeping this case distinct from the account-unusable ones below).
        String expiredConsumeRaw = "expired-token-consume";
        String expiredConsumeHash = stubToken(expiredConsumeRaw, NOW.minusSeconds(1), null);
        Account activeForExpired = usableAccount(AccountStatus.ACTIVE);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeForExpired));
        when(tokenRepository.markConsumed(eq(expiredConsumeHash), any())).thenReturn(0);
        assertThat(service.consume(expiredConsumeRaw)).isEmpty();

        // Already used - verify() path.
        String usedRaw = "used-token";
        stubToken(usedRaw, NOW.plusSeconds(3600), NOW.minusSeconds(1));
        assertThat(service.verify(usedRaw)).isEmpty();

        // Already used - consume() path (markConsumed's own WHERE usedAt IS NULL excludes it).
        String usedConsumeRaw = "used-token-consume";
        String usedConsumeHash = stubToken(usedConsumeRaw, NOW.plusSeconds(3600), NOW.minusSeconds(1));
        Account activeForUsed = usableAccount(AccountStatus.ACTIVE);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeForUsed));
        when(tokenRepository.markConsumed(eq(usedConsumeHash), any())).thenReturn(0);
        assertThat(service.consume(usedConsumeRaw)).isEmpty();

        // Account deleted - verify() path.
        String deletedRaw = "deleted-account-token";
        stubToken(deletedRaw, NOW.plusSeconds(3600), null);
        Account deletedAccount = usableAccount(AccountStatus.DELETED);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(deletedAccount));
        assertThat(service.verify(deletedRaw)).isEmpty();

        // Account deleted - consume() path: the pre-check must reject before markConsumed is ever
        // attempted, so a deleted account's token is never marked used by a rejected attempt.
        String deletedConsumeRaw = "deleted-account-token-consume";
        String deletedConsumeHash = stubToken(deletedConsumeRaw, NOW.plusSeconds(3600), null);
        Account deletedAccount2 = usableAccount(AccountStatus.DELETED);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(deletedAccount2));
        assertThat(service.consume(deletedConsumeRaw)).isEmpty();
        verify(tokenRepository, never()).markConsumed(eq(deletedConsumeHash), any());

        // Account suspended - consume() path, same pre-check-rejects-before-mutation guarantee.
        String suspendedRaw = "suspended-account-token";
        String suspendedHash = stubToken(suspendedRaw, NOW.plusSeconds(3600), null);
        Account suspendedAccount = usableAccount(AccountStatus.SUSPENDED);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(suspendedAccount));
        assertThat(service.consume(suspendedRaw)).isEmpty();
        verify(tokenRepository, never()).markConsumed(eq(suspendedHash), any());
    }

    @Test
    void shouldTreatTokenExactlyAtExpiryAsExpired() {
        String rawToken = "exact-boundary-token";
        stubToken(rawToken, NOW, null);

        assertThat(service.verify(rawToken)).isEmpty();
    }

    @Test
    void shouldTreatTokenOneTickBeforeExpiryAsValid() {
        String rawToken = "almost-expired-token";
        stubToken(rawToken, NOW.plusNanos(1), null);
        Account account = usableAccount(AccountStatus.ACTIVE);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThat(service.verify(rawToken)).contains(ACCOUNT_UUID);
    }

    @Test
    void shouldRejectSecondConsumeOfTheSameTokenAtomically() {
        String rawToken = "single-use-token";
        String hash = stubToken(rawToken, NOW.plusSeconds(3600), null);
        Account account = usableAccount(AccountStatus.ACTIVE);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        // First call marks 1 row; second call (already used, per the repository's own atomic
        // WHERE clause) marks 0 rows. This unit test proves the service correctly interprets that
        // contract - real cross-transaction atomicity is a DB-level guarantee, not testable here.
        when(tokenRepository.markConsumed(eq(hash), any())).thenReturn(1).thenReturn(0);

        Optional<UUID> first = service.consume(rawToken);
        Optional<UUID> second = service.consume(rawToken);

        assertThat(first).contains(ACCOUNT_UUID);
        assertThat(second).isEmpty();
    }

    @Test
    void shouldRejectConsumeWhenAccountBecomesUnusableBetweenTheTwoChecks() {
        // Kimi Phase 8 Finding 3 / Phase 9 fix: markConsumed succeeds, but the account is no
        // longer usable by the time of the post-consume re-check.
        String rawToken = "race-token";
        String hash = stubToken(rawToken, NOW.plusSeconds(3600), null);
        when(tokenRepository.markConsumed(eq(hash), any())).thenReturn(1);
        Account activeAccount = usableAccount(AccountStatus.ACTIVE);
        Account suspendedAccount = usableAccount(AccountStatus.SUSPENDED);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(activeAccount))
                .thenReturn(Optional.of(suspendedAccount));

        assertThat(service.consume(rawToken)).isEmpty();
    }

    @Test
    void shouldInvalidatePriorActiveTokenBeforeIssuingANewOne() {
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        when(tokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);

        verify(tokenRepository).invalidateActive(
                eq(ACCOUNT_ID), eq(VerificationToken.Purpose.EMAIL_VERIFY), eq(NOW));
    }

    @Test
    void shouldMakePriorTokenUnverifiableAfterReissue() {
        // Behavioral proof, not just a call-verification: the prior token genuinely stops
        // resolving after a second issue() for the same (account, purpose) - Kimi Phase 11 Gap 1.
        // invalidateActive is a mocked bulk UPDATE, so its real filtering effect is simulated here
        // via a stateful answer that marks the first token's usedAt, exactly as the real query
        // would do in the database.
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        when(tokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationTokenResult first = service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);

        doAnswer(invocation -> {
            setUsedAt(first.token(), NOW);
            return 1;
        }).when(tokenRepository).invalidateActive(
                eq(ACCOUNT_ID), eq(VerificationToken.Purpose.EMAIL_VERIFY), any());

        VerificationTokenResult second = service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);

        when(tokenRepository.findByTokenHash(first.token().getTokenHash())).thenReturn(Optional.of(first.token()));
        when(tokenRepository.findByTokenHash(second.token().getTokenHash())).thenReturn(Optional.of(second.token()));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThat(service.verify(first.rawToken())).isEmpty();
        assertThat(service.verify(second.rawToken())).contains(ACCOUNT_UUID);
    }

    @Test
    void shouldThrowIllegalStateExceptionOnTokenHashCollision() {
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        DataIntegrityViolationException collision = new DataIntegrityViolationException("duplicate token_hash");
        when(tokenRepository.saveAndFlush(any())).thenThrow(collision);

        assertThatThrownBy(() -> service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(collision);
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenIssuingForUnknownAccount() {
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void shouldRejectNullArgumentsWithIntentionalException() {
        assertThatThrownBy(() -> service.issue(null, VerificationToken.Purpose.EMAIL_VERIFY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.issue(ACCOUNT_UUID, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.verify(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.consume(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRoundTripBothPurposes() {
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        when(tokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationTokenResult emailVerify =
                service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);
        VerificationTokenResult passwordReset =
                service.issue(ACCOUNT_UUID, VerificationToken.Purpose.PASSWORD_RESET);

        assertThat(emailVerify.token().getPurpose()).isEqualTo(VerificationToken.Purpose.EMAIL_VERIFY);
        assertThat(passwordReset.token().getPurpose()).isEqualTo(VerificationToken.Purpose.PASSWORD_RESET);
    }

    @Test
    void shouldNeverLeakRawTokenViaResultToString() {
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        when(tokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationTokenResult issued = service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);

        assertThat(issued.toString()).doesNotContain(issued.rawToken());
    }

    @Test
    void shouldGenerateUrlSafeRawTokenOfExpectedLength() {
        Account account = usableAccount(AccountStatus.PENDING_VERIFICATION);
        when(accountRepository.findByAccountUuid(ACCOUNT_UUID)).thenReturn(Optional.of(account));
        when(tokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationTokenResult issued = service.issue(ACCOUNT_UUID, VerificationToken.Purpose.EMAIL_VERIFY);

        assertThat(issued.rawToken()).hasSize(43);
        assertThat(issued.rawToken()).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(issued.rawToken()).doesNotContain("+", "/", "=");
        assertThatCode(() -> Base64.getUrlDecoder().decode(issued.rawToken())).doesNotThrowAnyException();
    }

    /** Stubs {@code findByTokenHash} for the given raw token's real hash and returns that hash. */
    private String stubToken(String rawToken, Instant expiresAt, Instant usedAt) {
        String hash = Hashing.sha256(rawToken);
        VerificationToken token = VerificationToken.create(
                ACCOUNT_ID, VerificationToken.Purpose.EMAIL_VERIFY, hash, NOW, expiresAt);
        if (usedAt != null) {
            setUsedAt(token, usedAt);
        }
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        return hash;
    }

    /**
     * Builds a fully-stubbed {@link Account} mock. Callers must assign the result to a local
     * variable before passing it to another mock's {@code when(...).thenReturn(...)} — calling
     * this inline as a {@code thenReturn} argument starts a nested, unfinished Mockito stubbing
     * context and throws {@code UnfinishedStubbingException}.
     *
     * <p>Stubs are {@code lenient} because this shared fixture builder is reused by tests that
     * only exercise part of the mock's surface (e.g. an {@code issue}-only test never calls
     * {@code getAccountUuid()}/{@code getStatus()} on the account) — Mockito's strict-stubbing
     * mode would otherwise fail those tests on an unused stub that a *different* test does need.</p>
     */
    private Account usableAccount(AccountStatus status) {
        Account account = mock(Account.class);
        lenient().when(account.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(account.getAccountUuid()).thenReturn(ACCOUNT_UUID);
        lenient().when(account.getStatus()).thenReturn(status);
        return account;
    }

    /**
     * {@link VerificationToken} deliberately has no {@code usedAt} mutator (redemption only
     * happens through the repository's atomic update - see its javadoc). Reflection is used here,
     * test-only, purely to construct a fixture representing an already-consumed row as it would
     * be read back from the database.
     */
    private static void setUsedAt(VerificationToken token, Instant usedAt) {
        try {
            Field field = VerificationToken.class.getDeclaredField("usedAt");
            field.setAccessible(true);
            field.set(token, usedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
