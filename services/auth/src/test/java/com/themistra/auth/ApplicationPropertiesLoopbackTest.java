package com.themistra.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimi sweep test review, Gap 9 (2026-10-20): no value in {@code application.properties} may
 * point at a loopback address unless an environment variable can replace it. In a container,
 * loopback is the container itself (FU-T27-2 fixed one such value in the other services). Reads
 * the real file from the classpath; no Spring context, no Docker. The same check runs in crypto,
 * notification and payment.
 */
class ApplicationPropertiesLoopbackTest {

    private static final Pattern LOOPBACK = Pattern.compile("localhost|127\\.0\\.0\\.1|\\[::1]");
    private static final Pattern ENV_OVERRIDABLE = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*:[^}]*}");

    @Test
    void everyLoopbackValueIsOverridableByEnvironment() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(in).as("application.properties must be on the test classpath").isNotNull();
            properties.load(in);
        }
        List<String> loopbackKeys = properties.stringPropertyNames().stream().sorted()
                .filter(key -> LOOPBACK.matcher(properties.getProperty(key)).find())
                .toList();
        assertThat(loopbackKeys).as("the check sees the datasource URL, so it is not vacuous")
                .contains("spring.datasource.url");
        assertThat(loopbackKeys.stream()
                .filter(key -> !ENV_OVERRIDABLE.matcher(properties.getProperty(key)).matches())
                .toList())
                .as("loopback values that no environment variable can replace")
                .isEmpty();
    }
}
