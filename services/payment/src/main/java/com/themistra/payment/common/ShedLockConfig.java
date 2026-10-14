package com.themistra.payment.common;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * The one {@link LockProvider} bean {@code InvoiceExpirySweepJob} (T08) needs. Uses the default
 * {@code "shedlock"} table name - the already-provisioned {@code payments.shedlock} table
 * (`V1__payments_baseline.sql`), reachable without extra configuration since this service's own
 * datasource connection already sets {@code search_path} to {@code payments} (T03). Mirrors
 * {@code crypto-service}'s own identical one-bean template.
 */
@Configuration
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
