package com.themistra.payment.archtestfixtures;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * T23 — deliberately calls both {@code URI.create(String)} and the {@code URI(String)}
 * constructor directly, so {@code ArchitectureTest}'s rule (every problem-type URI must be a
 * named {@code ProblemTypes} constant, never an inline literal via either construction path)
 * can be proven to actually fail against a genuine violation, not just pass on already-clean
 * code — mirrors {@link RogueKmsAndChainClientUser}'s own identical purpose for L3.
 */
public class RogueInlineProblemTypeUser {

    public URI inlineUriViaCreate() {
        return URI.create("https://checky.pro/problems/inline-not-allowed");
    }

    public URI inlineUriViaConstructor() throws URISyntaxException {
        return new URI("https://checky.pro/problems/also-not-allowed");
    }
}
