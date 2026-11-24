package com.themistra.payment.chainverify;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Per-client limits for the two public verification paths.
 *
 * <p>Every field is boxed and read through an accessor with a default, deliberately: this binds
 * from whatever is present and never fails a context that did not set it, which is what keeps the
 * existing configuration-binding fixtures honest instead of needing a line added to each one.</p>
 *
 * <p><b>{@code trustForwardedFor} defaults to true because this service always runs behind the
 * edge</b>, and behind a proxy {@code getRemoteAddr()} is the proxy - every visitor in the world
 * would share one bucket. The header is only trustworthy because the proxy sets it; if this is
 * ever exposed directly to the internet, set it to false, because a client can otherwise spoof the
 * header and give itself a fresh bucket per request.</p>
 */
@ConfigurationProperties(prefix = "themistra.payment.chain-verify.rate-limit")
public record VerificationRateLimitProperties(
        Boolean enabled,
        Integer verifyRequests,
        Integer readRequests,
        Duration window,
        Boolean trustForwardedFor,
        Integer maxTrackedClients
) {

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** The POST: it fans out to three providers, so it is the one that costs real money. */
    public int verifyRequestsOrDefault() {
        return verifyRequests == null || verifyRequests <= 0 ? 10 : verifyRequests;
    }

    /** The receipt read: one indexed row, so it is cheap - but not free. */
    public int readRequestsOrDefault() {
        return readRequests == null || readRequests <= 0 ? 60 : readRequests;
    }

    public Duration windowOrDefault() {
        return window == null || window.isZero() || window.isNegative() ? Duration.ofMinutes(1) : window;
    }

    public boolean trustsForwardedFor() {
        return trustForwardedFor == null || trustForwardedFor;
    }

    /** A bound on the tracking map, so a flood of distinct addresses cannot exhaust the heap. */
    public int maxTrackedClientsOrDefault() {
        return maxTrackedClients == null || maxTrackedClients <= 0 ? 100_000 : maxTrackedClients;
    }
}
