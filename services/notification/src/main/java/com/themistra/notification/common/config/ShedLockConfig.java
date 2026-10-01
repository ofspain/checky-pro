package com.themistra.notification.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * The {@link LockProvider} {@code RetryScheduler}'s own {@code @SchedulerLock} needs (T14, L7,
 * {@code agents.md}'s "multi-replica scheduled jobs are ShedLock-guarded" rule). The simplest
 * {@link JdbcTemplateLockProvider} constructor is correct here: it defaults to the {@code shedlock}
 * table with the exact column names {@code V1}'s own schema already uses, and the app's own
 * datasource already narrows {@code search_path} to {@code notifications} first
 * (connection-init-sql), so no schema-qualified table name is needed.
 */
@Configuration
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
