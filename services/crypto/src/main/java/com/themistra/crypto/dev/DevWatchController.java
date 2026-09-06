package com.themistra.crypto.dev;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.TxObservation;
import com.themistra.crypto.watch.PaymentVerifiedEvent;
import com.themistra.crypto.watch.Watch;
import com.themistra.crypto.watch.WatchRepository;
import com.themistra.crypto.watch.WatchService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registers a watch against a payment that has already happened, so the whole pipeline can be
 * watched working end to end.
 *
 * <p>The watcher scans a window of recent blocks, so a watch registered for a transfer inside that
 * window is discovered on the next cycle exactly as a genuinely incoming payment would be. Nothing
 * is stubbed: the discovery, the quorum, the event and the status change are all the real ones.
 *
 * <p>Local development only.
 */
@RestController
@RequestMapping("/dev/api")
@Profile("local")
public class DevWatchController {

    /** Comfortably inside the watcher's scan window, so the first cycle finds it. */
    private static final int DEMO_BLOCKS_BACK = 30;

    private final DevSampleService samples;
    private final ChainAdapterRegistry registry;
    private final WatchService watchService;
    private final WatchRepository watchRepository;
    private final RecordingWatcherEventPublisher events;

    public DevWatchController(DevSampleService samples,
                              ChainAdapterRegistry registry,
                              WatchService watchService,
                              WatchRepository watchRepository,
                              RecordingWatcherEventPublisher events) {
        this.samples = samples;
        this.registry = registry;
        this.watchService = watchService;
        this.watchRepository = watchRepository;
        this.events = events;
    }

    /**
     * Finds a recent transfer and registers a watch whose terms match it exactly.
     *
     * <p>This is the honest half of a demonstration: the watch is registered through the same
     * service a payment client would call, with the same validation, and then nothing here does
     * anything else. The watcher finds it on its own.
     */
    @PostMapping("/watch/demo")
    public Map<String, Object> registerDemoWatch(@RequestParam String chainId) {
        ChainId chain = ChainId.parse(chainId);
        String txHash = (String) samples.sample(chainId, DEMO_BLOCKS_BACK).get("txHash");

        ChainAdapter probe = registry.adaptersFor(chain).getFirst();
        TxObservation seen = probe.getTx(txHash)
                .orElseThrow(() -> new IllegalStateException("sample transaction vanished: " + txHash));

        Watch watch = watchService.register(
                "demo-" + UUID.randomUUID(),
                chainId,
                seen.toAddress(),
                seen.tokenAddress(),
                seen.amount(),
                Instant.now().plus(Duration.ofHours(1)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("watchId", watch.getWatchUuid().toString());
        body.put("recipient", seen.toAddress());
        body.put("token", seen.tokenAddress());
        body.put("amount", seen.amount().toString());
        body.put("decimals", seen.decimals());
        // Shown only so a human can check the watcher found the right one. The watcher is not
        // told this, and does not receive it — it has to find the transaction itself.
        body.put("expectedTxHash", txHash);
        return body;
    }

    /** Every watch and every event so far, newest first. */
    @GetMapping("/watches")
    public Map<String, Object> watches() {
        List<Map<String, Object>> watches = watchRepository.findAll().stream()
                .sorted(Comparator.comparing(Watch::getCreatedAt).reversed())
                .limit(20)
                .map(DevWatchController::describe)
                .toList();

        List<Map<String, Object>> published = events.recent().stream()
                .map(DevWatchController::describe)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("watches", watches);
        body.put("events", published);
        return body;
    }

    private static Map<String, Object> describe(Watch watch) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("watchId", watch.getWatchUuid().toString());
        map.put("status", watch.getStatus().name());
        map.put("recipient", watch.getRecipientAddress());
        map.put("amount", watch.getExpectedAmount().toString());
        map.put("createdAt", watch.getCreatedAt().toString());
        return map;
    }

    private static Map<String, Object> describe(PaymentVerifiedEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("watchId", event.watchId().toString());
        map.put("txHash", event.txHash());
        map.put("blockNumber", event.blockNumber());
        map.put("amount", event.observedAmount().toString());
        map.put("verifiedAt", event.verifiedAt().toString());
        return map;
    }
}
