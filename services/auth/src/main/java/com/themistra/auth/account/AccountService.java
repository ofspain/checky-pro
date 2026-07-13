package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;

/**
 * Account lifecycle operations. Event emission (auth.user.lifecycle) attaches here via the
 * events module in a later pass — inside these same transactions, per the outbox rule (D-009).
 */
@Service
@Validated
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new account in PENDING_VERIFICATION. The unique constraint on email is the
     * real duplicate guard — the existsByEmail pre-check only provides the friendlier path;
     * a concurrent insert between check and save still surfaces as {@link DuplicateEmailException}.
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
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse suspend(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        account.suspend();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse reinstate(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        account.reinstate();
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByUuid(UUID accountUuid) {
        return AccountResponse.from(getAccount(accountUuid));
    }

    private Account getAccount(UUID accountUuid) {
        return accountRepository.findByAccountUuid(accountUuid)
                .orElseThrow(() -> new AccountNotFoundException(accountUuid));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
