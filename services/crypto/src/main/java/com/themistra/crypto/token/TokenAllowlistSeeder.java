package com.themistra.crypto.token;

import com.themistra.crypto.common.config.TokenAllowlistProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Config-driven allowlist seeding (Phase 3 Kimi Issue 1) - replaces what would otherwise have been a
 * Flyway DML migration, which agents.md's "Flyway, DDL-only migrations" rule forbids. Runs once at
 * startup, idempotently upserting {@link TokenAllowlistProperties#entries()} into {@code
 * chain.token_allowlist} (skip-if-already-present, keyed by {@code (chain, contractAddress, version)}).
 *
 * <p><b>Concurrent-seeding race (multi-replica startup) is caught, not fatal.</b> Two replicas
 * rolling out together can both see an entry absent and both attempt to insert it; the loser's insert
 * violates {@code UNIQUE (chain, contract_address, version)}. Unlike an analogous race elsewhere in
 * this codebase (e.g. {@code ProviderHealthTracker}, T10) that only affects one runtime request, an
 * uncaught exception here would fail application startup itself - so the resulting {@link
 * DataIntegrityViolationException} is caught and logged, not propagated. The data ends up correct
 * either way.</p>
 */
@Component
public class TokenAllowlistSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TokenAllowlistSeeder.class);

    private final TokenAllowlistRepository repository;
    private final TokenAllowlistProperties properties;
    private final Clock clock;

    public TokenAllowlistSeeder(TokenAllowlistRepository repository, TokenAllowlistProperties properties,
                                 Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (TokenAllowlistProperties.Entry entry : properties.entries()) {
            seedIfAbsent(entry);
        }
    }

    private void seedIfAbsent(TokenAllowlistProperties.Entry entry) {
        boolean alreadySeeded = repository
                .findByChainAndContractAddressAndVersion(entry.chain(), entry.contractAddress(), entry.version())
                .isPresent();
        if (alreadySeeded) {
            return;
        }

        try {
            repository.save(TokenAllowlist.create(entry.chain(), entry.contractAddress(), entry.symbol(),
                    entry.decimals(), entry.version(), entry.signature(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            logger.info("Token allowlist entry already seeded concurrently: chain={} contractAddress={} version={}",
                    entry.chain(), entry.contractAddress(), entry.version());
        }
    }
}
