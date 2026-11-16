package com.themistra.payment.chainverify;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How to render an amount for a human, per token. <b>Display only.</b>
 *
 * <p>crypto-service owns the real token allowlist, with the symbol and decimals, and signs it — but
 * its lookup response returns the contract address alone, and widening that response would mean
 * changing a published contract shape for a cosmetic reason. So the symbol and decimals used to
 * print "12.50 USDC" beside the base units live here, in configuration, and are never used to
 * decide anything: the stored amount is always the base-unit string crypto-service agreed on, and a
 * token missing from this list simply shows its base units unscaled.</p>
 *
 * <p>If this list and crypto's allowlist ever disagree, crypto's is right and this one is a display
 * bug — which is the whole reason this class decides nothing.</p>
 */
@ConfigurationProperties(prefix = "themistra.payment.chain-verify")
public record TokenDisplayProperties(List<Token> tokens) {

    public TokenDisplayProperties {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }

    public record Token(String chain, String contractAddress, String symbol, int decimals) {
    }

    /** Contract addresses are compared case-insensitively: EIP-55 checksums differ only by case. */
    public Optional<Token> find(String chain, String contractAddress) {
        if (chain == null || contractAddress == null) {
            return Optional.empty();
        }
        return tokens.stream()
                .filter(token -> chain.equalsIgnoreCase(token.chain())
                        && contractAddress.equalsIgnoreCase(token.contractAddress()))
                .findFirst();
    }

    static String normaliseChain(String chain) {
        return chain == null || chain.isBlank() ? "ETHEREUM" : chain.toUpperCase(Locale.ROOT);
    }
}
