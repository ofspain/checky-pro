package com.themistra.auth.authn;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoginSuccessHandler} — R18, AC4. Mocks
 * {@code HttpServletRequest}/{@code HttpServletResponse}/{@code HttpSession} to satisfy the
 * inherited {@code SavedRequestAwareAuthenticationSuccessHandler}'s internal session/request-cache
 * lookups, none of which this test asserts on directly.
 */
@ExtendWith(MockitoExtension.class)
class LoginSuccessHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private LockoutService lockoutService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LoginSuccessHandler(lockoutService, clock);
        lenient().when(request.getSession(false)).thenReturn(session);
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(response.encodeRedirectURL(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldResetLockoutCounterOnSuccessfulLogin() throws Exception {
        when(authentication.getName()).thenReturn(ACCOUNT_UUID.toString());

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(lockoutService).recordSuccessfulAttempt(ACCOUNT_UUID, NOW);
    }

    @Test
    void stillDelegatesToTheInheritedRedirectBehavior() throws Exception {
        // Phase 11 Gap 3: proves super.onAuthenticationSuccess(...) actually runs, not just that
        // recordSuccessfulAttempt was called - a handler that recorded the attempt and then
        // returned without delegating would still pass every other test in this file.
        when(authentication.getName()).thenReturn(ACCOUNT_UUID.toString());

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendRedirect(anyString());
    }

    @Test
    void lockoutFailureDoesNotPreventLoginCompleting() throws Exception {
        when(authentication.getName()).thenReturn(ACCOUNT_UUID.toString());
        doThrow(new RuntimeException("db down")).when(lockoutService).recordSuccessfulAttempt(any(), any());

        assertThatCode(() -> handler.onAuthenticationSuccess(request, response, authentication))
                .doesNotThrowAnyException();
    }

    @Test
    void nonUuidPrincipalNameDoesNotPreventLoginCompleting() throws Exception {
        // Defensive hardening (Phase 9 Finding 8): unreachable via this task's own wiring
        // (AccountUserDetailsService always sets a UUID as the username), but must not turn an
        // already-authenticated login into an error if it were ever violated.
        when(authentication.getName()).thenReturn("not-a-uuid");

        assertThatCode(() -> handler.onAuthenticationSuccess(request, response, authentication))
                .doesNotThrowAnyException();

        verify(lockoutService, org.mockito.Mockito.never()).recordSuccessfulAttempt(any(), any());
    }
}
