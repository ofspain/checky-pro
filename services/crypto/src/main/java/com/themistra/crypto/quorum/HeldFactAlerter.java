package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ops alert on {@code HELD} (L2, R2). Implemented as a structured, distinctly-leveled log line - an
 * explicitly interim implementation: no external paging/webhook integration exists anywhere in this
 * codebase yet, and {@code agents.md}'s "paged metrics" observability aspiration is a platform-level
 * concern for a future metrics/alerting task, not something this task claims to satisfy alone. Kept as
 * its own class specifically so a real paging integration can replace its internals later without
 * touching {@link QuorumDecisionService} or {@link QuorumEvaluator}.
 *
 * <p><b>Logs at {@code error} level by design (Phase 9, Kimi Phase 8 Issue 6).</b> This is the "ops
 * alert" itself (L2/R2 require disagreement to be surfaced, not buried); downgrading to
 * {@code debug}/{@code trace} would defeat that purpose. {@code answer} values are logged verbatim -
 * every value type this task's own callers ever supply is a typed blockchain fact ({@code Boolean},
 * {@code BigDecimal}, a contract address {@code String}), never a secret or PII. Callers must never
 * pass a {@code T} whose {@code toString()} could contain secret or personal data.</p>
 */
@Component
public class HeldFactAlerter {

    private static final Logger logger = LoggerFactory.getLogger(HeldFactAlerter.class);

    public <T> void alert(String chain, String txHash, FactType factType, List<ProviderAnswer<T>> answers) {
        logger.error("Quorum HELD: providers disagreed - chain={} txHash={} factType={} answers={}",
                chain, txHash, factType, answers);
    }
}
