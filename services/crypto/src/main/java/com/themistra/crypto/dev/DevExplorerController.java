package com.themistra.crypto.dev;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.TxObservation;
import com.themistra.crypto.quorum.ProviderAnswer;
import com.themistra.crypto.quorum.QuorumPolicy;
import com.themistra.crypto.quorum.QuorumReader;
import com.themistra.crypto.quorum.QuorumResult;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A window onto the quorum layer, for local development only.
 *
 * <p><strong>Profile-gated to {@code local}.</strong> Not conditionally secured — it does not
 * exist as a bean in any other profile, so there is no configuration mistake that could expose it.
 * This service holds the only role permitted to sign attestations; a debug endpoint here is not
 * something to protect with a flag someone could flip.
 *
 * <p>It reads. It registers nothing, stores nothing, and publishes nothing.
 */
@RestController
@RequestMapping("/dev/api")
@Profile("local")
public class DevExplorerController {

    private final ChainAdapterRegistry registry;
    private final QuorumReader reader;
    private final QuorumPolicy policy;

    public DevExplorerController(ChainAdapterRegistry registry, QuorumReader reader, QuorumPolicy policy) {
        this.registry = registry;
        this.reader = reader;
        this.policy = policy;
    }

    /** What chains and providers this instance is configured with. No endpoints, as ever. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("threshold", policy.threshold());
        Map<String, List<String>> chains = new LinkedHashMap<>();
        for (ChainId chainId : registry.configuredChains()) {
            chains.put(chainId.value(), registry.adaptersFor(chainId).stream()
                    .map(ChainAdapter::providerLabel).toList());
        }
        body.put("chains", chains);
        return body;
    }

    /**
     * Runs a real quorum read and reports what every provider said.
     *
     * <p>The per-provider breakdown is the point: an agreed fact is unremarkable, whereas seeing
     * one provider lag, disagree, or fail is what makes §6.1 concrete.
     */
    @GetMapping("/verify")
    public Map<String, Object> verify(@RequestParam String chainId, @RequestParam String txHash) {
        ChainId chain = ChainId.parse(chainId);
        List<ChainAdapter> adapters = registry.adaptersFor(chain);

        long startedAt = System.currentTimeMillis();
        QuorumResult result = reader.establish(adapters, txHash.trim());
        long elapsedMs = System.currentTimeMillis() - startedAt;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chainId", chain.value());
        body.put("txHash", txHash.trim());
        body.put("threshold", policy.threshold());
        body.put("providerCount", adapters.size());
        body.put("elapsedMs", elapsedMs);

        switch (result) {
            case QuorumResult.Agreed agreed -> {
                body.put("outcome", "AGREED");
                body.put("fact", observation(agreed.fact()));
            }
            case QuorumResult.Absent ignored -> {
                body.put("outcome", "ABSENT");
                body.put("reason", "providers agree this transaction is not on chain");
            }
            case QuorumResult.Disagreed disagreed -> {
                body.put("outcome", "DISAGREED");
                body.put("reason", disagreed.reason());
            }
        }

        List<Map<String, Object>> answers = new ArrayList<>();
        for (ProviderAnswer answer : result.answers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("provider", answer.providerLabel());
            entry.put("state", answer.isFailure() ? "FAILED" : answer.isAbsence() ? "ABSENT" : "SAW");
            entry.put("failure", answer.failure().orElse(null));
            entry.put("observation", answer.observation().map(DevExplorerController::observation).orElse(null));
            entry.put("observedAt", answer.observedAt().toString());
            answers.add(entry);
        }
        body.put("answers", answers);
        return body;
    }

    private static Map<String, Object> observation(TxObservation observation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("blockNumber", observation.blockNumber());
        map.put("blockHash", observation.blockHash());
        map.put("from", observation.fromAddress());
        map.put("to", observation.toAddress());
        map.put("token", observation.tokenAddress());
        // base units as a string, exactly as it travels on the wire
        map.put("amount", observation.amount().toString());
        map.put("decimals", observation.decimals());
        return map;
    }
}
