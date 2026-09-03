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
 */
@Component
public class HeldFactAlerter {

    private static final Logger logger = LoggerFactory.getLogger(HeldFactAlerter.class);

    public <T> void alert(String chain, String txHash, FactType factType, List<ProviderAnswer<T>> answers) {
        logger.error("Quorum HELD: providers disagreed - chain={} txHash={} factType={} answers={}",
                chain, txHash, factType, answers);
    }
}
