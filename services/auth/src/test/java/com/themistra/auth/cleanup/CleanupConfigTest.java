package com.themistra.auth.cleanup;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Kimi Phase 11 Gap 3 (the bean-presence half): without this, a future removal of
 * {@code CleanupConfig}'s {@code @Bean} method would only surface as a runtime
 * "no LockProvider available" failure the first time {@code @SchedulerLock} actually tries to
 * acquire a lock — not caught by any other test, since {@code CleanupJobTest} mocks every
 * collaborator and bypasses ShedLock's AOP proxy entirely.
 */
class CleanupConfigTest {

    @Test
    void lockProviderBeanIsAJdbcTemplateLockProvider() {
        LockProvider provider = new CleanupConfig().lockProvider(mock(DataSource.class));

        assertThat(provider).isNotNull().isInstanceOf(JdbcTemplateLockProvider.class);
    }
}
