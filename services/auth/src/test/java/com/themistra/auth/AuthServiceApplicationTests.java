package com.themistra.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Scaffold smoke test: context boots, Flyway migrates the auth schema against a real Postgres,
 * SAS auto-configuration loads. Module tests land alongside their modules.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
