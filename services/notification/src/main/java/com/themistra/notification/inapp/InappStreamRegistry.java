package com.themistra.notification.inapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The best-effort, same-replica-only SSE push registry (Kimi Phase 3 Finding #4). Not a
 * cross-replica fan-out mechanism - a missed push (the account's own connection, if any, is held by
 * a different replica, or no connection is open at all) is never a failure of anything; the
 * persisted {@code inapp_notifications} row remains the durable source of truth a client recovers
 * from via reconnect and/or {@code InappReadController}'s own unread endpoint (Phase 1's own
 * resolved reading of {@code package.md}'s explicit "reconnect from the persisted unread set"
 * language).
 *
 * <p>Backed by a {@link ConcurrentHashMap} of {@link CopyOnWriteArrayList}s - SSE connections
 * arrive and drop on arbitrary HTTP threads, independently of the Kafka-listener threads calling
 * {@code InAppChannel.send} (T06's own {@code concurrency: 2} precedent).</p>
 */
@Component
public class InappStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(InappStreamRegistry.class);

    /** No timeout ({@code 0L}) - a notification stream is meant to stay open indefinitely, unlike
     * Spring's own ~30s default. */
    private static final long NO_TIMEOUT = 0L;

    private final Map<UUID, List<SseEmitter>> emittersByAccount = new ConcurrentHashMap<>();

    public SseEmitter register(UUID accountUuid) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        List<SseEmitter> emitters = emittersByAccount.computeIfAbsent(accountUuid, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> deregister(accountUuid, emitter));
        emitter.onTimeout(() -> deregister(accountUuid, emitter));
        emitter.onError(e -> deregister(accountUuid, emitter));

        return emitter;
    }

    /**
     * Best-effort: an account with no connected emitter is the ordinary, expected case, not an
     * error. Any emitter that fails to receive the event is treated as dead and removed - its own
     * failure never propagates to the caller ({@code InAppChannel.send}).
     */
    public void push(UUID accountUuid, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByAccount.get(accountUuid);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                log.debug("Removing dead SSE emitter for accountUuid={}", accountUuid, e);
                deregister(accountUuid, emitter);
            }
        }
    }

    /**
     * Kimi Phase 8 Finding #4: removes the emitter, then atomically removes the account's own map
     * entry too if it is now empty - otherwise every account that ever connects leaves a permanent,
     * empty list behind, an unbounded memory leak. {@link ConcurrentHashMap#computeIfPresent} locks
     * per-key, so this can never race with a concurrent {@link #register} call for the same account
     * (which uses {@code computeIfAbsent} on the same key) - either the new emitter is added before
     * this remapping runs (the list is non-empty, the key survives) or after (a fresh list is
     * created for the next connection), never interleaved.
     */
    private void deregister(UUID accountUuid, SseEmitter emitter) {
        emittersByAccount.computeIfPresent(accountUuid, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
