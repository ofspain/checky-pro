package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.LoginView;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginFailureHandler} — R16, R43, L5, AC1-AC3, AC7, AC9. Plain JUnit +
 * Mockito, no real servlet container — {@code HttpServletRequest}/{@code HttpServletResponse}/
 * {@code HttpSession} are mocked. {@link SimpleUrlAuthenticationFailureHandler}'s inherited
 * {@code saveException}/redirect logic touches {@code request.getSession()}/
 * {@code request.getContextPath()}/{@code response.encodeRedirectURL(...)} internally, so those
 * are stubbed in {@code setUp} even though this test never asserts on them directly — otherwise
 * every test would NPE inside the superclass before reaching any of this handler's own logic.
 */
@ExtendWith(MockitoExtension.class)
class LoginFailureHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final String EMAIL = "merchant@example.com";

    @Mock
    private AccountService accountService;

    @Mock
    private LockoutService lockoutService;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LoginFailureHandler(accountService, lockoutService, auditService, clock);
        lenient().when(request.getSession()).thenReturn(session);
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(response.encodeRedirectURL(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldAppendRowAndMirrorAuditEventForLoginFailure() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        verify(lockoutService).recordFailedAttempt(ACCOUNT_UUID, NOW);
        RecordAuditEventRequest recorded = captureAuditRequest();
        assertThat(recorded.eventType()).isEqualTo("login.failed");
        assertThat(recorded.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(recorded.accountUuid()).isEqualTo(ACCOUNT_UUID);
        assertThat(recorded.actorUuid()).isEqualTo(ACCOUNT_UUID);
        assertThat(recorded.ip()).isEqualTo("203.0.113.5");
        assertThat(recorded.rawUserAgent()).isEqualTo("test-agent");
        assertThat(recorded.traceId()).isNull();
        verify(response).sendRedirect("/login?error");
    }

    @Test
    void unknownEmailAuditsWithNullUuidsAndNeverCallsLockoutService() throws Exception {
        when(request.getParameter("username")).thenReturn("nobody@example.com");
        when(accountService.findLoginView("nobody@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationFailure(request, response, new UsernameNotFoundException("Bad credentials"));

        verifyNoInteractions(lockoutService);
        RecordAuditEventRequest recorded = captureAuditRequest();
        assertThat(recorded.accountUuid()).isNull();
        assertThat(recorded.actorUuid()).isNull();
        verify(response).sendRedirect("/login?error");
    }

    @Test
    void nullUsernameParameterAuditsWithNullUuidsAndNeverCallsLockoutService() throws Exception {
        when(request.getParameter("username")).thenReturn(null);

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        verifyNoInteractions(lockoutService);
        verify(accountService, never()).findLoginView(any());
        assertThat(captureAuditRequest().accountUuid()).isNull();
        verify(response).sendRedirect("/login?error");
    }

    @Test
    void pendingVerificationAccountAuditsOnlyNeverCallsLockoutService() throws Exception {
        assertAuditOnlyForStatus(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void suspendedAccountAuditsOnlyNeverCallsLockoutService() throws Exception {
        assertAuditOnlyForStatus(AccountStatus.SUSPENDED);
    }

    // No deletedAccountAuditsOnlyNeverCallsLockoutService test: Phase 11 Gap 1, verified against
    // AccountService.findLoginView (AccountService.java:339), which already filters DELETED
    // accounts to Optional.empty() - a DELETED email is indistinguishable from an unknown one by
    // the time it reaches this handler, so that scenario is exercised by
    // unknownEmailAuditsWithNullUuidsAndNeverCallsLockoutService, not a status-gated branch.
    // Asserting a LoginView(status=DELETED) here would test a production-unreachable input.

    @Test
    void stillLockedAccountAuditsOnlyNeverCallsRecordFailedAttempt() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.LOCKED)));
        when(lockoutService.isCurrentlyLocked(ACCOUNT_UUID, NOW)).thenReturn(true);

        handler.onAuthenticationFailure(request, response, new LockedException("locked"));

        verify(lockoutService, never()).recordFailedAttempt(any(), any());
        assertThat(captureAuditRequest().accountUuid()).isEqualTo(ACCOUNT_UUID);
    }

    @Test
    void expiredLockAccountCallsRecordFailedAttempt() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.LOCKED)));
        when(lockoutService.isCurrentlyLocked(ACCOUNT_UUID, NOW)).thenReturn(false);

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        verify(lockoutService).recordFailedAttempt(ACCOUNT_UUID, NOW);
    }

    @Test
    void everyExceptionSubclassProducesTheSameRedirect() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        AuthenticationException[] exceptions = {
                new BadCredentialsException("bad"),
                new UsernameNotFoundException("Bad credentials"),
                new LockedException("locked"),
                new DisabledException("disabled")
        };

        ArgumentCaptor<String> redirectUrls = ArgumentCaptor.forClass(String.class);
        for (AuthenticationException exception : exceptions) {
            handler.onAuthenticationFailure(request, response, exception);
        }

        verify(response, times(exceptions.length)).sendRedirect(redirectUrls.capture());
        assertThat(redirectUrls.getAllValues()).containsOnly("/login?error");
    }

    @Test
    void shouldReturnIndistinguishableResponseForLockedAndBadCredentials() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        ArgumentCaptor<String> redirectUrls = ArgumentCaptor.forClass(String.class);

        // Still-locked LOCKED -> LockedException
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.LOCKED)));
        when(lockoutService.isCurrentlyLocked(ACCOUNT_UUID, NOW)).thenReturn(true);
        handler.onAuthenticationFailure(request, response, new LockedException("locked"));

        // SUSPENDED -> DisabledException
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.SUSPENDED)));
        handler.onAuthenticationFailure(request, response, new DisabledException("disabled"));

        // DELETED -> filtered to Optional.empty() by AccountService.findLoginView, same as unknown
        when(accountService.findLoginView(EMAIL)).thenReturn(Optional.empty());
        handler.onAuthenticationFailure(request, response, new UsernameNotFoundException("Bad credentials"));

        // Non-existent email -> Optional.empty()
        handler.onAuthenticationFailure(request, response, new UsernameNotFoundException("Bad credentials"));

        // Baseline: ACTIVE + wrong password -> BadCredentialsException (also stands in for an
        // expired-lock LOCKED account, which produces this same exception type)
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        verify(response, times(5)).sendRedirect(redirectUrls.capture());
        assertThat(redirectUrls.getAllValues()).containsOnly("/login?error");
    }

    @Test
    void bookkeepingFailureDoesNotPreventTheRedirect() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL)).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad")))
                .doesNotThrowAnyException();

        verify(response).sendRedirect("/login?error");
        verifyNoInteractions(auditService);
    }

    @Test
    void lockoutFailureStillAllowsAuditToFireAndRedirectToProceed() throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        doThrow(new RuntimeException("db down")).when(lockoutService).recordFailedAttempt(any(), any());

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        // Phase 9 Finding 3, made explicit: audit fires (with the real accountUuid, not
        // suppressed by the lockout failure) strictly before the redirect proceeds.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(auditService, response);
        inOrder.verify(auditService).record(any());
        inOrder.verify(response).sendRedirect("/login?error");
        assertThat(captureAuditRequest().accountUuid()).isEqualTo(ACCOUNT_UUID);
    }

    private void assertAuditOnlyForStatus(AccountStatus status) throws Exception {
        when(request.getParameter("username")).thenReturn(EMAIL);
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", status)));

        handler.onAuthenticationFailure(request, response, new DisabledException("disabled"));

        verifyNoInteractions(lockoutService);
        assertThat(captureAuditRequest().accountUuid()).isEqualTo(ACCOUNT_UUID);
    }

    private RecordAuditEventRequest captureAuditRequest() {
        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }
}
