package com.themistra.auth.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three independent, in-process, per-key token-bucket registries (R41, T31) — one each for login
 * (incl. MFA, which happens inside the same request), password-reset confirmation, and the
 * {@code /oauth2/token} refresh_token grant. Deliberately in-process and unbounded: O2's own
 * stated preference is per-replica ephemeral buckets (the durable {@code lockout_state} table
 * already covers the "must survive a restart" concern for credential-guessing specifically), and
 * an unbounded registry is an accepted backstop-only limitation (Phase 4 D6) rather than adding a
 * bounded-cache dependency for what R42 frames as a secondary defense layer.
 *
 * <p>Token bucket, not a fixed/sliding window (Phase 3 Finding 7): capacity equals the configured
 * per-minute threshold, refilling greedily at that same rate over 60 seconds — the most direct
 * mapping of "N requests per minute" onto Bucket4j's own primitives.</p>
 */
@Component
public class RateLimiter {

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> passwordResetBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> oauthTokenBuckets = new ConcurrentHashMap<>();

    private final Bandwidth loginBandwidth;
    private final Bandwidth passwordResetBandwidth;
    private final Bandwidth oauthTokenBandwidth;

    public RateLimiter(RateLimitProperties properties) {
        this.loginBandwidth = bandwidthFor(properties.loginPerMinute());
        this.passwordResetBandwidth = bandwidthFor(properties.passwordResetPerMinute());
        this.oauthTokenBandwidth = bandwidthFor(properties.oauthTokenPerMinute());
    }

    public ConsumptionProbe tryConsumeLogin(String key) {
        return tryConsume(loginBuckets, loginBandwidth, key);
    }

    public ConsumptionProbe tryConsumePasswordReset(String key) {
        return tryConsume(passwordResetBuckets, passwordResetBandwidth, key);
    }

    public ConsumptionProbe tryConsumeOauthToken(String key) {
        return tryConsume(oauthTokenBuckets, oauthTokenBandwidth, key);
    }

    private static ConsumptionProbe tryConsume(Map<String, Bucket> registry, Bandwidth bandwidth, String key) {
        Bucket bucket = registry.computeIfAbsent(key, k -> Bucket.builder().addLimit(bandwidth).build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    private static Bandwidth bandwidthFor(int perMinute) {
        return Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)));
    }
}
