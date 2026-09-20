package com.themistra.crypto.token;

import com.themistra.crypto.common.config.TokenAllowlistProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

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
 * DataIntegrityViolationException} is caught, not propagated. **Phase 9 (Kimi Phase 8 Issue 5):** the
 * same exception type is thrown for any integrity violation, not only the benign concurrent-duplicate
 * case, so the catch block re-verifies the row now actually exists before logging it as such - a
 * genuine, different constraint failure (which would leave the row absent) is re-thrown instead of
 * being misreported to operators as a harmless race.</p>
 *
 * <p><b>A new version's entries becoming visible non-atomically across multiple inserts is an
 * accepted, disclosed risk, not fixed here (Phase 9, Kimi Phase 8 Issue 4).</b> Each entry is seeded in
 * its own individually-transactional {@code save} call; a concurrent reader could observe a chain's new
 * version as "current" (via {@link TokenAllowlistRepository#findCurrentVersionEntry}) after only some
 * of that version's entries have committed, transiently reporting {@code UNKNOWN_TOKEN} for the
 * not-yet-inserted ones until seeding finishes. Wrapping the whole loop in one transaction was
 * considered and rejected: a single benign duplicate-key conflict on one entry (the race this class
 * already handles) would then roll back every other, non-conflicting entry in the same batch, trading
 * a narrow and transient startup-time race for a larger one.</p>
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
        Objects.requireNonNull(properties.entries(), "entries");
        for (TokenAllowlistProperties.Entry entry : properties.entries()) {
            seedIfAbsent(entry);
        }
    }

    private void seedIfAbsent(TokenAllowlistProperties.Entry entry) {
        if (alreadyExists(entry)) {
            return;
        }

        try {
            repository.save(TokenAllowlist.create(entry.chain(), entry.contractAddress(), entry.symbol(),
                    entry.decimals(), entry.version(), entry.signature(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Phase 9 (Kimi Phase 8 Issue 5): only treat this as the benign concurrent-seeding race
            // if the row is now actually present - otherwise this was a genuine, different integrity
            // violation, and swallowing it would misreport a real problem to operators as harmless.
            if (alreadyExists(entry)) {
                logger.info("Token allowlist entry already seeded concurrently: chain={} contractAddress={} version={}",
                        entry.chain(), entry.contractAddress(), entry.version());
            } else {
                throw e;
            }
        }
    }

    private boolean alreadyExists(TokenAllowlistProperties.Entry entry) {
        return repository
                .findByChainAndContractAddressAndVersion(entry.chain(), entry.contractAddress(), entry.version())
                .isPresent();
    }
}
