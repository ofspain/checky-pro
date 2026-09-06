package com.themistra.crypto.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A deployment that cannot authenticate anyone must not start.
 *
 * <p>The failure being guarded against is not a crash — it is a service that looks perfectly
 * healthy while accepting every caller, on the one service in the estate holding the role
 * permitted to sign attestations.
 */
class SecurityConfigTest {

    @Test
    @DisplayName("no issuer with require-issuer on refuses to start")
    void missingIssuerRefusesToStart() {
        SecurityProperties properties = new SecurityProperties();
        properties.setRequireIssuer(true);
        properties.setIssuerUri("");

        assertThatThrownBy(() -> new SecurityConfig(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to start")
                .hasMessageContaining("issuer-uri");
    }

    @Test
    @DisplayName("blank is not an issuer")
    void blankIssuerIsNotAnIssuer() {
        SecurityProperties properties = new SecurityProperties();
        properties.setRequireIssuer(true);
        properties.setIssuerUri("   ");

        assertThat(properties.hasIssuer()).isFalse();
        assertThatThrownBy(() -> new SecurityConfig(properties)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an issuer satisfies the requirement")
    void issuerConfiguredStarts() {
        SecurityProperties properties = new SecurityProperties();
        properties.setRequireIssuer(true);
        properties.setIssuerUri("https://auth.themistra.example");

        assertThat(new SecurityConfig(properties)).isNotNull();
    }

    @Test
    @DisplayName("local development may run without an issuer, explicitly")
    void localMayRunWithoutIssuer() {
        SecurityProperties properties = new SecurityProperties();
        properties.setRequireIssuer(false);
        properties.setIssuerUri("");

        assertThat(new SecurityConfig(properties)).isNotNull();
    }

    @Test
    @DisplayName("requiring an issuer is the default, so disabling it is a visible act")
    void requireIssuerDefaultsToTrue() {
        assertThat(new SecurityProperties().isRequireIssuer()).isTrue();
        assertThat(new SecurityProperties().hasIssuer()).isFalse();
    }

    @Test
    @DisplayName("the watch scope has a default so a deployment cannot forget to name one")
    void watchScopeHasDefault() {
        assertThat(new SecurityProperties().getWatchScope()).isEqualTo("internal.chain:write");
    }
}
