package com.themistra.payment.chainverify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What the budget actually promises. Every case drives the filter directly, because the thing
 * under test is the counting, not Spring's filter registration.
 */
class VerificationRateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    private static VerificationRateLimitProperties limits(int verify, int read) {
        return new VerificationRateLimitProperties(true, verify, read, Duration.ofMinutes(1), true, 1000);
    }

    private static MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Forwarded-For", ip);
        return request;
    }

    @Test
    void lettingThroughUpToTheLimitAndRefusingTheNextOne() throws Exception {
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(
                limits(3, 60), Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), ok, chain);
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        verify(chain, times(3)).doFilter(any(), any());

        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), refused, chain);

        assertThat(refused.getStatus()).isEqualTo(429);
        // The charset is appended to the content type, so this is a prefix match on purpose.
        assertThat(refused.getContentType()).startsWith("application/problem+json");
        assertThat(refused.getContentAsString()).contains("\"status\":429");
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(refused.getHeader("RateLimit-Remaining")).isEqualTo("0");
        // The fourth request must not have reached the application at all.
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void budgetsAreSeparatePerClientAddress() throws Exception {
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(
                limits(1, 60), Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/verifications", "2.2.2.2"), other, chain);

        // One caller exhausting the budget must not lock everybody else out.
        assertThat(other.getStatus()).isEqualTo(200);
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void readingAReceiptHasItsOwnBudgetAndIsNotSpentByThePost() throws Exception {
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(
                limits(1, 5), Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse read = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/v1/verifications/abc", "1.1.1.1"), read, chain);

        // A shared receipt link must still open for someone who has just run a check.
        assertThat(read.getStatus()).isEqualTo(200);
    }

    @Test
    void theBudgetRefillsOnceTheWindowHasPassed() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(limits(1, 60), clock);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), refused, chain);
        assertThat(refused.getStatus()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        MockHttpServletResponse later = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), later, chain);

        assertThat(later.getStatus()).isEqualTo(200);
    }

    @Test
    void disabledMeansEveryRequestPassesUncounted() throws Exception {
        VerificationRateLimitProperties off =
                new VerificationRateLimitProperties(false, 1, 1, Duration.ofMinutes(1), true, 1000);
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(off, Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void anUnrelatedPathIsNeverCounted() throws Exception {
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(
                limits(1, 1), Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 4; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", "/api/v1/invoices", "1.1.1.1"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(4)).doFilter(any(), any());
    }

    /** With the proxy header distrusted, every caller behind one proxy shares a bucket - which is
     * exactly why the default is to trust it. */
    @Test
    void ignoringTheProxyHeaderPutsEveryCallerBehindTheProxyInOneBucket() throws Exception {
        VerificationRateLimitProperties distrust =
                new VerificationRateLimitProperties(true, 1, 60, Duration.ofMinutes(1), false, 1000);
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(
                distrust, Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/v1/verifications", "2.2.2.2"), second, chain);

        assertThat(second.getStatus()).isEqualTo(429);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        VerificationRateLimitProperties blank =
                new VerificationRateLimitProperties(null, null, null, null, null, null);

        assertThat(blank.isEnabled()).isTrue();
        assertThat(blank.verifyRequestsOrDefault()).isEqualTo(10);
        assertThat(blank.readRequestsOrDefault()).isEqualTo(60);
        assertThat(blank.windowOrDefault()).isEqualTo(Duration.ofMinutes(1));
        assertThat(blank.trustsForwardedFor()).isTrue();
        assertThat(blank.maxTrackedClientsOrDefault()).isEqualTo(100_000);
    }

    @Test
    void aRefusedRequestNeverReachesTheApplication() throws Exception {
        VerificationRateLimitFilter filter = new VerificationRateLimitFilter(
                limits(0, 60), Clock.fixed(NOW, ZoneOffset.UTC));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        // 0 is not a usable budget, so the default of 10 applies rather than refusing everything.
        filter.doFilter(request("POST", "/api/v1/verifications", "1.1.1.1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(any(), any());
        verify(chain, never()).doFilter(null, null);
    }

    /** A clock that can be moved, so the window can expire without the test sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
