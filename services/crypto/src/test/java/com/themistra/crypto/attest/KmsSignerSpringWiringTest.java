package com.themistra.crypto.attest;

import com.themistra.crypto.common.config.KmsProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link KmsSigner} is actually wireable as a Spring {@code @Component} - the interaction tests
 * in {@link KmsSignerTest} and the real-API test in {@link KmsSignerLocalStackIntegrationTest} both
 * construct {@code KmsSigner} directly (via its package-private test-seam constructor), so neither ever
 * exercised Spring's own constructor-resolution machinery.
 *
 * <p><b>Self-review Finding (discovered and fixed in this phase, not deferred to Phase 9).</b> {@code
 * KmsSigner} has two constructors and, in its first draft, neither was annotated {@code @Autowired}.
 * Verified directly: Spring could not resolve which constructor to use and threw {@code
 * BeanInstantiationException: No default constructor found} - the bean would have failed to start in
 * any real deployment. {@code services/auth}'s own {@code MfaSeedEncryption} (the precedent this class's
 * shape mirrors) already annotates its own public constructor {@code @Autowired} for exactly this
 * reason; the omission here was this class's own draft, not something inherited from that precedent.
 * Fixed by adding {@code @Autowired} to the public constructor - re-verified to wire correctly, which
 * this test now locks in permanently.</p>
 */
class KmsSignerSpringWiringTest {

    @BeforeAll
    static void setResolvableRegion() {
        // KmsSigner.resolveKmsClient() calls the real KmsClient.builder().build(), which resolves a
        // region eagerly - this test is about constructor selection, not region resolution (already
        // separately verified), so a resolvable region is provided via system property exactly as the
        // AWS SDK's own default provider chain supports.
        System.setProperty("aws.region", "us-east-1");
    }

    @AfterAll
    static void clearResolvableRegion() {
        System.clearProperty("aws.region");
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void kmsSignerWiresUpAsASpringComponentWithOnlyPropertiesAndClockBeansPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KmsSigner.class)).isNotNull();
        });
    }

    @Configuration
    @ComponentScan(basePackageClasses = KmsSigner.class)
    static class TestConfig {

        @Bean
        KmsProperties kmsProperties() {
            return new KmsProperties("fake-key-id");
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
