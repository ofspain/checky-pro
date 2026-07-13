package com.themistra.auth.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Map;

/**
 * Cross-cutting beans. The SecurityFilterChain itself belongs to the authn/token modules —
 * only chain-agnostic primitives live here.
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * Delegating encoder writing {bcrypt} at strength 12 (target-design §4). The delegating
     * wrapper keeps stored hashes self-describing, so a future work-factor or algorithm bump
     * (D-006 revisit) is a config change plus lazy rehash — not a migration.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        String idForEncode = "bcrypt";
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        DelegatingPasswordEncoder delegating =
                new DelegatingPasswordEncoder(idForEncode, Map.of(idForEncode, bcrypt));
        delegating.setDefaultPasswordEncoderForMatches(bcrypt);
        return delegating;
    }

    /** Injectable clock so lockout windows and token TTLs are unit-testable (never Instant.now() inline). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
