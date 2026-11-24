package com.themistra.payment.chainverify;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

/**
 * Registers {@link VerificationRateLimitFilter} against the public verification paths only.
 *
 * <p>It is registered explicitly rather than annotated as a component so that the URL patterns are
 * stated here: a bare filter bean would run on every request in the service, including the
 * merchant API that has its own authentication and does not want this one's counters.</p>
 */
@Configuration
public class VerificationRateLimitConfig {

    @Bean
    FilterRegistrationBean<VerificationRateLimitFilter> verificationRateLimit(
            VerificationRateLimitProperties properties, Clock clock) {
        FilterRegistrationBean<VerificationRateLimitFilter> registration = new FilterRegistrationBean<>(
                new VerificationRateLimitFilter(properties, clock));
        registration.addUrlPatterns("/api/v1/verifications", "/api/v1/verifications/*");
        // Ahead of Spring Security: a request that is over budget should cost as little as possible.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
