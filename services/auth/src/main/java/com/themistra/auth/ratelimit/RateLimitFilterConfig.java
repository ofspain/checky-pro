package com.themistra.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@link RateLimitFilter} as a bean and immediately disables Spring Boot's automatic
 * global servlet-filter registration for it (T31). Without the second bean, Boot would register
 * this filter twice: once inside each {@code SecurityFilterChain} (explicitly wired in
 * {@code SecurityChainsConfig}), and once more as a container-wide filter running on every
 * request regardless of chain — the standard, documented Spring Security idiom for a filter that
 * must only participate in the security chains it's explicitly added to.
 */
@Configuration
public class RateLimitFilterConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        return new RateLimitFilter(rateLimiter, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> preventAutoRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}
