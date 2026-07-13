package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.LoginView;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.event.UserLifecycleEventPayload;
import com.themistra.auth.events.OutboxPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Account lifecycle operations. Every transition that matters to other services publishes
 * through the outbox in the same transaction (D-009) — no state change here is ever silent.
 */
@Service
@Validated
public class AccountService {

    private static final String AGGREGATE_TYPE = "account";
    private static final int SCHEMA_VERSION = 1;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                          OutboxPublisher outboxPublisher, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    /**
     * Registers a new account in PENDING_VERIFICATION. The unique constraint on email is the
     * real duplicate guard — the existsByEmail pre-check only provides the friendlier path;
     * a concurrent insert between check and save still surfaces as {@link DuplicateEmailException}.
     *
     * No event is published here: per target-design §9, {@code auth.user.registered} fires at
     * email-confirmation time ({@link #activateEmail}), not at initial signup. The
     * {@code auth.email.requested} event that would trigger the verification email belongs to
     * the not-yet-built verification-token flow (account module, email-verification stage).
     */
    @Transactional
    public AccountResponse register(@Valid RegisterAccountRequest request) {
        String email = normalize(request.email());

        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Account account = Account.register(email, passwordEncoder.encode(request.password()));
        try {
            return AccountResponse.from(accountRepository.saveAndFlush(account));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException();
        }
    }

    /** Marks the email verified and activates the account (token validation happens upstream). */
    @Transactional
    public AccountResponse activateEmail(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        account.activateEmail();
        publishLifecycleEvent(account, "user.registered");
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse suspend(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        account.suspend();
        publishLifecycleEvent(account, "user.suspended");
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse reinstate(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        account.reinstate();
        publishLifecycleEvent(account, "user.reinstated");
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse delete(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        account.markDeleted();
        publishLifecycleEvent(account, "user.deleted");
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByUuid(UUID accountUuid) {
        return AccountResponse.from(getAccount(accountUuid));
    }

    /**
     * Credential lookup for interactive login (authn module). Deleted accounts are
     * indistinguishable from unknown emails — both return empty.
     */
    @Transactional(readOnly = true)
    public Optional<LoginView> findLoginView(String email) {
        return accountRepository.findByEmail(normalize(email))
                .filter(account -> account.getStatus() != AccountStatus.DELETED)
                .map(account -> new LoginView(
                        account.getAccountUuid(), account.getPasswordHash(), account.getStatus()));
    }

    private void publishLifecycleEvent(Account account, String eventType) {
        outboxPublisher.publish(
                AGGREGATE_TYPE,
                account.getAccountUuid().toString(),
                eventType,
                SCHEMA_VERSION,
                new UserLifecycleEventPayload(account.getAccountUuid(), account.getStatus(), clock.instant()));
    }

    private Account getAccount(UUID accountUuid) {
        return accountRepository.findByAccountUuid(accountUuid)
                .orElseThrow(() -> new AccountNotFoundException(accountUuid));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
