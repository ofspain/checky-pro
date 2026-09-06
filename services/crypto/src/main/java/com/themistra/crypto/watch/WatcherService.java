package com.themistra.crypto.watch;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.TxObservation;
import com.themistra.crypto.quorum.QuorumReader;
import com.themistra.crypto.quorum.QuorumResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds payments for active watches and publishes an event when one is established.
 *
 * <p>Two steps, deliberately separated. <em>Discovery</em> asks one provider for candidate
 * transaction hashes — cheap, and untrusted. <em>Verification</em> puts each candidate through the
 * quorum reader, where several providers must independently agree before anything is believed. A
 * provider that invented a hash, or missed one, cannot by itself decide an invoice.
 *
 * <p>Deliberately absent for now: reorg handling, an observation log, provider-lag weighting, and
 * work sharding. Each is real, and each is cheaper to add once there is traffic to size it
 * against. What is here is the smallest thing that can actually verify a payment.
 */
@Service
public class WatcherService {

    private static final Logger log = LoggerFactory.getLogger(WatcherService.class);

    /**
     * How far back a scan reaches. Wide enough to survive a restart or a slow cycle, narrow enough
     * that providers do not reject the query — log-range caps differ between them.
     */
    private static final long SCAN_DEPTH_BLOCKS = 200;

    private final WatchRepository watchRepository;
    private final QuorumReader quorumReader;
    private final ChainAdapterRegistry registry;
    private final WatcherEventPublisher publisher;
    private final Clock clock;

    public WatcherService(WatchRepository watchRepository,
                          QuorumReader quorumReader,
                          ChainAdapterRegistry registry,
                          WatcherEventPublisher publisher,
                          Clock clock) {
        this.watchRepository = watchRepository;
        this.quorumReader = quorumReader;
        this.registry = registry;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${themistra.crypto.watcher.interval-ms:15000}", initialDelay = 5000)
    public void checkActiveWatches() {
        List<Watch> active;
        try {
            active = watchRepository.findAllActive(Instant.now(clock));
        } catch (Exception e) {
            log.error("Could not read active watches; retrying next cycle", e);
            return;
        }
        if (active.isEmpty()) {
            return;
        }

        log.debug("Checking {} active watches", active.size());
        for (Watch watch : active) {
            try {
                checkWatch(watch);
            } catch (org.springframework.dao.DataAccessException e) {
                // Not a provider hiccup. Logging this at warn once hid a bug that republished the
                // same payment every cycle, so it is an error and it carries the stack.
                log.error("Watch {} failed against the database; it will be retried and may "
                        + "re-publish until this is fixed", watch.getWatchUuid(), e);
            } catch (Exception e) {
                // One watch's provider trouble must not stop the others being checked.
                log.warn("Watch {} could not be checked this cycle: {}",
                        watch.getWatchUuid(), e.toString());
            }
        }
    }

    private void checkWatch(Watch watch) {
        ChainId chainId = ChainId.parse(watch.getChainId());
        List<ChainAdapter> adapters = registry.adaptersFor(chainId);
        if (adapters.isEmpty()) {
            // Registration rejects unconfigured chains, so this means configuration changed
            // underneath a live watch — worth saying out loud rather than silently skipping.
            log.warn("Watch {} names chain {}, which has no configured providers",
                    watch.getWatchUuid(), watch.getChainId());
            return;
        }

        for (String candidate : discoverCandidates(adapters, watch)) {
            if (verify(adapters, watch, candidate)) {
                return;
            }
        }
    }

    /**
     * Candidate hashes, gathered from every provider that answers.
     *
     * <p>Asking all of them and taking the union costs one cheap call each and means a single
     * provider missing a log cannot hide a payment. Nothing here is trusted — these are only
     * the hashes worth putting through quorum.
     */
    private Set<String> discoverCandidates(List<ChainAdapter> adapters, Watch watch) {
        Set<String> candidates = new LinkedHashSet<>();
        for (ChainAdapter adapter : adapters) {
            try {
                long head = adapter.currentBlockNumber();
                long from = Math.max(0, head - SCAN_DEPTH_BLOCKS);
                candidates.addAll(adapter.findIncomingTransfers(
                        watch.getRecipientAddress(), watch.getTokenAddress(), from, head));
            } catch (Exception e) {
                log.debug("Provider {} could not be scanned: {}", adapter.providerLabel(), e.toString());
            }
        }
        return candidates;
    }

    /** @return true when this candidate established the payment and the watch is now settled. */
    private boolean verify(List<ChainAdapter> adapters, Watch watch, String txHash) {
        QuorumResult result = quorumReader.establish(adapters, txHash);
        if (!(result instanceof QuorumResult.Agreed agreed)) {
            // Disagreement or absence: not a failure, just not yet established. Try again later.
            return false;
        }

        TxObservation fact = agreed.fact();
        if (!fact.amount().equals(watch.getExpectedAmount())) {
            // A real transfer to this address, but not the one this invoice is waiting for.
            return false;
        }

        // Settle first, publish second. The reverse order looks harmless and is not: if the
        // write fails after the event is out, the next cycle finds the same payment and publishes
        // it again, forever. This ordering trades that for the rarer, smaller failure of a
        // settled watch whose event was lost.
        //
        // Neither is correct at scale. The right answer is a transactional outbox — event and
        // status written together, drained by a separate publisher — which is what the event
        // publishing change will need anyway. Until then, at-most-once beats unbounded repeats.
        watchRepository.updateStatus(watch.getId(), WatchStatus.SATISFIED);

        publisher.publish(new PaymentVerifiedEvent(
                watch.getWatchUuid(),
                fact.txHash(),
                fact.blockNumber(),
                fact.blockHash(),
                fact.amount(),
                watch.getChainId(),
                Instant.now(clock)));

        log.info("Watch {} satisfied by {} at block {}",
                watch.getWatchUuid(), fact.txHash(), fact.blockNumber());
        return true;
    }
}
