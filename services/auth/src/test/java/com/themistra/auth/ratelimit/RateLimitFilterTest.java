package com.themistra.auth.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.auth.common.Hashing;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimitFilter} — T31, R41/R42. Plain JUnit + Mockito with real
 * {@code MockHttpServletRequest}/{@code MockHttpServletResponse} (spring-test) so path/method/
 * parameter/body matching is proven against real servlet-API semantics, not a hand-rolled fake.
 * {@link RateLimiter} is mocked — its own threshold/refill behavior is {@link RateLimiterTest}'s
 * job; this file proves the filter correctly derives keys, calls the right bucket, and reacts
 * correctly to the probe.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, OBJECT_MAPPER);
    }

    private static ConsumptionProbe consumed() {
        return ConsumptionProbe.consumed(0, 0);
    }

    private static ConsumptionProbe rejected() {
        return ConsumptionProbe.rejected(0, TimeUnit.SECONDS.toNanos(30), TimeUnit.SECONDS.toNanos(60));
    }

    // -------------------------------------------------------------------
    // Login path
    // -------------------------------------------------------------------

    @Test
    void loginRequestConsumesLoginBucketWithNormalizedUsername() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("username", "  User@Example.COM  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsumeLogin("user@example.com")).thenReturn(consumed());

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).tryConsumeLogin("user@example.com");
        verify(filterChain).doFilter(any(), eq(response));
    }

    @Test
    void loginRequestWithMissingUsernameUsesEmptyStringKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsumeLogin("")).thenReturn(consumed());

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).tryConsumeLogin("");
    }

    @Test
    void getRequestToLoginIsNotRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    // -------------------------------------------------------------------
    // Password-reset path
    // -------------------------------------------------------------------

    @Test
    void passwordResetRequestConsumesPasswordResetBucketWithTokenHash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts/password-reset");
        String body = "{\"token\":\"raw-reset-token\",\"newPassword\":\"correct-horse-battery\"}";
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        String expectedHash = Hashing.sha256("raw-reset-token");
        when(rateLimiter.tryConsumePasswordReset(expectedHash)).thenReturn(consumed());

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).tryConsumePasswordReset(expectedHash);
        // The body must still be fully readable downstream (CachedBodyHttpServletRequest) - a
        // MockHttpServletRequest's own raw stream would otherwise already be exhausted by the
        // filter's own read.
        verify(filterChain).doFilter(any(CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    void passwordResetRequestWithMalformedJsonFailsOpen() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts/password-reset");
        request.setContent("not json at all".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> filter.doFilter(request, response, filterChain)).doesNotThrowAnyException();

        verify(rateLimiter, never()).tryConsumePasswordReset(any());
        verify(filterChain).doFilter(any(), eq(response));
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    // -------------------------------------------------------------------
    // /oauth2/token path
    // -------------------------------------------------------------------

    @Test
    void oauthTokenRefreshGrantConsumesOauthTokenBucketWithTokenHash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter("grant_type", "refresh_token");
        request.setParameter("refresh_token", "raw-refresh-token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String expectedHash = Hashing.sha256("raw-refresh-token-value");
        when(rateLimiter.tryConsumeOauthToken(expectedHash)).thenReturn(consumed());

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).tryConsumeOauthToken(expectedHash);
    }

    @Test // AC8 - client_credentials must never be throttled by this per-account mechanism
    void oauthTokenClientCredentialsGrantIsNeverRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter("grant_type", "client_credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test // AC8 - authorization_code exchanges are likewise unaffected
    void oauthTokenAuthorizationCodeGrantIsNeverRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter("grant_type", "authorization_code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
    }

    @Test
    void oauthTokenRequestWithNoGrantTypeIsNeverRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
    }

    // -------------------------------------------------------------------
    // Cross-cutting: unrelated paths, 429 response shape, fail-open
    // -------------------------------------------------------------------

    @Test
    void unrelatedPathIsNeverRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test // AC7 - the 429 body shape and Retry-After header
    void whenBucketIsExhaustedRequestIsRejectedWith429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("username", "throttled@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsumeLogin(any())).thenReturn(rejected());

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");

        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsByteArray());
        assertThat(body.get("type").asText()).isEqualTo("https://checky.pro/problems/rate-limit-exceeded");
        assertThat(body.get("title").asText()).isEqualTo("Too many requests");
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("instance").asText()).isEqualTo("/login");
    }

    @Test // D5 - a limiter failure must never surface to the caller nor block the request
    void rateLimiterThrowingFailsOpen() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("username", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsumeLogin(any())).thenThrow(new RuntimeException("bucket registry corrupted"));

        assertThatCode(() -> filter.doFilter(request, response, filterChain)).doesNotThrowAnyException();

        verify(filterChain).doFilter(any(), eq(response));
        assertThat(response.getStatus()).isNotEqualTo(429);
    }
}
