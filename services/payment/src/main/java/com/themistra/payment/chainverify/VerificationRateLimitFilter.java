package com.themistra.payment.chainverify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A per-client request budget for the two public verification paths.
 *
 * <p>This exists because those paths are the only unauthenticated ones on this service, and the
 * POST behind one of them fans out to three chain providers. Without a budget, one script turns a
 * public page into a bill.
 *
 * <p><b>It lives in the application rather than in the Caddyfile on purpose.</b> The Caddyfile is
 * a local-development artifact; a public deployment may sit behind ngrok, a cloud load balancer or
 * nothing at all, and a limit written into one edge disappears the moment the edge changes. Here it
 * travels with the service.
 *
 * <p><b>What this is not.</b> The counters are in memory, so each instance has its own budget and
 * N instances permit N times the traffic. For one demo instance that is the whole job; for a real
 * deployment behind several replicas, this wants a shared store, and that is a deliberate,
 * recorded limitation rather than an oversight.
 *
 * <p>A fixed window is used rather than a token bucket because the failure it guards against is a
 * loop hammering the endpoint, which a fixed window stops just as well and which can be read off
 * the response headers by whoever is debugging it at the time.</p>
 */
public class VerificationRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VerificationRateLimitFilter.class);
    private static final String PATH = "/api/v1/verifications";

    private final VerificationRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public VerificationRateLimitFilter(VerificationRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith(PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        boolean write = "POST".equalsIgnoreCase(request.getMethod());
        int limit = write ? properties.verifyRequestsOrDefault() : properties.readRequestsOrDefault();
        Duration window = properties.windowOrDefault();
        Instant now = clock.instant();
        String key = (write ? "W|" : "R|") + clientOf(request);

        Decision decision = admit(key, limit, window, now);

        response.setHeader("RateLimit-Limit", String.valueOf(limit));
        response.setHeader("RateLimit-Remaining", String.valueOf(Math.max(0, limit - decision.used())));
        response.setHeader("RateLimit-Reset", String.valueOf(Math.max(0, decision.resetSeconds())));

        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        log.info("Rate limit reached for a {} on {} ({} in {})",
                request.getMethod(), PATH, limit, window);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, decision.resetSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Written by hand: this filter runs before Spring MVC, so no ProblemDetail writer is in play.
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429,"
                + "\"detail\":\"Too many verification requests. Try again in "
                + Math.max(1, decision.resetSeconds()) + " seconds.\"}");
    }

    private Decision admit(String key, int limit, Duration window, Instant now) {
        purgeIfCrowded(now);
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.isExpired(now) ? new Window(now.plus(window)) : existing);
        int used = current.count().incrementAndGet();
        long reset = Math.max(0, Duration.between(now, current.expiresAt()).toSeconds());
        return new Decision(used <= limit, used, reset);
    }

    /**
     * Bound the map. Expired windows are dropped first; if the cap still holds after that, the
     * map is left alone and {@link #admit} keeps counting against whatever is already there -
     * new clients share the existing entries rather than the heap growing without end.
     */
    private void purgeIfCrowded(Instant now) {
        if (windows.size() < properties.maxTrackedClientsOrDefault()) {
            return;
        }
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired(now)) {
                it.remove();
            }
        }
        if (windows.size() >= properties.maxTrackedClientsOrDefault()) {
            log.warn("Rate limit tracking is at its cap of {} clients", properties.maxTrackedClientsOrDefault());
        }
    }

    /** The caller's address: the first hop in {@code X-Forwarded-For} when the proxy is trusted. */
    private String clientOf(HttpServletRequest request) {
        if (properties.trustsForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                String first = (comma < 0 ? forwarded : forwarded.substring(0, comma)).strip();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        String direct = request.getRemoteAddr();
        return direct == null ? "unknown" : direct;
    }

    private record Window(Instant expiresAt, AtomicInteger count) {
        Window(Instant expiresAt) {
            this(expiresAt, new AtomicInteger());
        }

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private record Decision(boolean allowed, int used, long resetSeconds) {
    }
}
