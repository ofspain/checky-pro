package com.themistra.payment.common;

/**
 * The exhaustive list of unauthenticated paths (agents.md Security rule; frozen brief AC3). A
 * sweep test asserts no {@code permitAll} exists outside this list.
 *
 * <p><b>{@code /api/v1/verifications} is the one public application path</b> (demo branch
 * {@code demo/onchain-verification}). Until it existed this list held actuator paths only, and
 * this comment said so. It is public because the landing page it serves is public: a visitor
 * pastes a transaction hash and gets the chain's agreed answer without an account. It can cause
 * one crypto-service read and one append-only row, and nothing else — the table grants
 * {@code INSERT} and {@code SELECT} only, and no other resource is reachable from it.</p>
 */
public final class PublicEndpoints {

    public static final String[] PATTERNS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/api/v1/verifications"
    };

    private PublicEndpoints() {
    }
}
