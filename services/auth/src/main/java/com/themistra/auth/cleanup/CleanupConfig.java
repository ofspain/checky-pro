package com.themistra.auth.cleanup;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wires ShedLock to the existing {@code shedlock} table (V5) via its default column/table naming
 * (both match this schema exactly, verified against ShedLock 7.7.0's own defaults) — without this
 * bean, {@code @SchedulerLock} fails at runtime with no lock provider available.
 */
@Configuration
public class CleanupConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
