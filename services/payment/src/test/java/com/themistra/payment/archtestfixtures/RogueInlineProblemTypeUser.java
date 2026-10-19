package com.themistra.payment.archtestfixtures;

import java.net.URI;

/**
 * T23 — deliberately calls {@code URI.create(String)} directly, so {@code ArchitectureTest}'s
 * new rule (every problem-type URI must be a named {@code ProblemTypes} constant, never an
 * inline literal) can be proven to actually fail against a genuine violation, not just pass on
 * already-clean code — mirrors {@link RogueKmsAndChainClientUser}'s own identical purpose for L3.
 */
public class RogueInlineProblemTypeUser {

    public URI inlineUri() {
        return URI.create("https://checky.pro/problems/inline-not-allowed");
    }
}
