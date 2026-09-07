package com.themistra.crypto.common;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * The one {@link LockProvider} bean {@code WatcherRegistry} (task 16, O5) needs - not named in
 * `design.md` §6's own package map, the same "functionally necessary, not spec-named" situation {@code
 * ObservationLog} (T08), {@code QuorumDecisionService} (T09), and {@code ProviderHealthTracker} (T10)
 * were each in. Uses the default {@code "shedlock"} table name - the already-provisioned {@code
 * chain.shedlock} table (`V1__chain_baseline.sql`), reachable without extra configuration since this
 * service's own datasource connection already sets {@code search_path} to {@code chain} (agents.md).
 */
@Configuration
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
