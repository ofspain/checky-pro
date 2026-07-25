package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.dto.RegistrationAcknowledgement;
import com.themistra.auth.account.dto.ResendVerificationRequest;
import com.themistra.auth.account.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Self-service account endpoints. {@code POST /accounts} is the only public route this service
 * exposes on this path (PublicEndpoints.METHOD_SCOPED) — everything else here requires a token.
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Always returns the same acknowledgement regardless of whether the email was already
     * registered (target-design §4 enumeration-safety) — {@link DuplicateEmailException} is
     * caught here, not surfaced, and never reaches {@link AccountExceptionHandler}.
     */
    @PostMapping
    public ResponseEntity<RegistrationAcknowledgement> register(
            @Valid @RequestBody RegisterAccountRequest request) {
        try {
            accountService.register(request);
        } catch (DuplicateEmailException ignored) {
            // enumeration-safe: caller learns nothing beyond "an email may have been registered"
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(RegistrationAcknowledgement.standard());
    }

    /**
     * Derives the account from the authenticated principal rather than a path variable — the
     * 'sub' claim is the account UUID by construction (AccountUserDetailsService), so there is
     * no path parameter for a caller to substitute another account's identifier into.
     */
    @GetMapping("/me")
    public AccountResponse me(Authentication authentication) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        return accountService.getByUuid(accountUuid);
    }

    /**
     * Public — token possession is the credential (R4). Success and failure are allowed to
     * differ (204 vs. 400); only failure reasons must be uniform among themselves (R5), which
     * {@link AccountExceptionHandler}'s single mapping for
     * {@link AccountService.VerificationTokenRejectedException} guarantees. No local catch here,
     * unlike {@link #register} — this endpoint's two outcomes are meant to be distinguishable.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountService.activateFromVerificationToken(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Public, email-identified (R6, as modified at the Phase 0 human-approval gate). Always
     * returns the same acknowledgement — {@link AccountService#resendVerificationIfPending}
     * never throws for a non-match, so there is nothing here to distinguish outcomes on.
     */
    @PostMapping("/resend-verification")
    public RegistrationAcknowledgement resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        accountService.resendVerificationIfPending(request.email());
        return RegistrationAcknowledgement.standard();
    }
}
