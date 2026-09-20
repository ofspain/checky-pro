package com.themistra.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who this service believes, and whether it insists on being able to check (ARCHITECTURE §8).
 *
 * <p>{@code requireIssuer} defaults to <em>true</em>. The failure being guarded against is a
 * deployment that looks healthy while silently having no authentication — the same failure D-011
 * guards in auth by refusing to boot without real signing keys. Defaulting to required means
 * turning it off is a visible line in a diff rather than an omission nobody notices.
 */
@ConfigurationProperties(prefix = "themistra.crypto.security")
public class SecurityProperties {

    /** The platform issuer. Empty is only tolerable in local development. */
    private String issuerUri = "";

    /** Whether a missing issuer should stop the service starting. */
    private boolean requireIssuer = true;

    /** The scope a caller must hold to use the internal API. */
    private String watchScope = "internal.chain:write";

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri == null ? "" : issuerUri.trim();
    }

    public boolean isRequireIssuer() {
        return requireIssuer;
    }

    public void setRequireIssuer(boolean requireIssuer) {
        this.requireIssuer = requireIssuer;
    }

    public String getWatchScope() {
        return watchScope;
    }

    public void setWatchScope(String watchScope) {
        this.watchScope = watchScope;
    }

    public boolean hasIssuer() {
        return !issuerUri.isBlank();
    }
}
