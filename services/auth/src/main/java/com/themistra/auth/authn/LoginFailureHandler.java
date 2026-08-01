package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.LoginView;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SAS password-login failure hook (R16, R43). Extends {@link SimpleUrlAuthenticationFailureHandler}
 * with the same {@code "/login?error"} target {@code .formLogin(Customizer.withDefaults())}
 * already used — the redirect behavior is never touched and never branches on the concrete
 * {@link AuthenticationException} subclass, so every failure (unknown email, wrong password,
 * still-locked, disabled) produces an identical response (L5/R21).
 *
 * <p>Only the internal bookkeeping differs per resolved account state: {@link LockoutService} is
 * called only for an {@code ACTIVE} account, or a {@code LOCKED} one whose interval has already
 * elapsed — matching {@link LockoutService}'s own documented precondition. Every other case
 * (unknown email, {@code PENDING_VERIFICATION}, {@code SUSPENDED}, {@code DELETED}, still
 * {@code LOCKED}) only records the audit event.</p>
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AccountService accountService;
    private final LockoutService lockoutService;
    private final AuditService auditService;
    private final Clock clock;

    public LoginFailureHandler(AccountService accountService, LockoutService lockoutService,
                               AuditService auditService, Clock clock) {
        super("/login?error");
        this.accountService = accountService;
        this.lockoutService = lockoutService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        recordFailure(request);
        super.onAuthenticationFailure(request, response, exception);
    }

    private void recordFailure(HttpServletRequest request) {
        String email = request.getParameter("username");
        Optional<LoginView> loginView = email == null
                ? Optional.empty()
                : accountService.findLoginView(email);

        if (loginView.isEmpty()) {
            auditFailure(request, null, null);
            return;
        }

        LoginView view = loginView.get();
        UUID accountUuid = view.accountUuid();
        Instant now = clock.instant();
        if (isLockoutEligible(view, now)) {
            lockoutService.recordFailedAttempt(accountUuid, now);
        }
        auditFailure(request, accountUuid, accountUuid);
    }

    private boolean isLockoutEligible(LoginView view, Instant now) {
        return view.status() == AccountStatus.ACTIVE
                || (view.status() == AccountStatus.LOCKED && !lockoutService.isCurrentlyLocked(view.accountUuid(), now));
    }

    private void auditFailure(HttpServletRequest request, UUID accountUuid, UUID actorUuid) {
        auditService.record(new RecordAuditEventRequest(
                "login.failed", AuditOutcome.FAILURE, accountUuid, actorUuid,
                request.getRemoteAddr(), request.getHeader("User-Agent"), null, Map.of()));
    }
}
