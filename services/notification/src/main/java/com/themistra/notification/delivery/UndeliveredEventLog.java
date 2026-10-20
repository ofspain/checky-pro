package com.themistra.notification.delivery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Writes the dispute-grade record of an event that was never delivered on any channel (Kimi sweep
 * review, Finding 7, 2026-10-20). Today that is one case: a parked payment event whose route never
 * arrived, removed by {@code routing.ParkedEventExpiryJob}.
 *
 * <p>The row has outcome {@code EXPIRED} and no channel, recipient, account or template (V19 allows
 * a null channel only for {@code EXPIRED}). {@code error_detail} holds the event kind, when it was
 * parked and the age limit, never the payload.</p>
 *
 * <p>{@link Propagation#MANDATORY}: the record is written only inside the transaction that deletes
 * the parked event, so an event is never removed without its record, nor recorded without being
 * removed.</p>
 */
@Service
public class UndeliveredEventLog {

    static final String EXPIRED = "EXPIRED";

    private final DeliveryLogRepository deliveryLogRepository;
    private final Clock clock;

    UndeliveredEventLog(DeliveryLogRepository deliveryLogRepository, Clock clock) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordExpired(String sourceEventKey, String kind, Instant parkedAt, long maxAgeHours) {
        Objects.requireNonNull(sourceEventKey, "sourceEventKey must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(parkedAt, "parkedAt must not be null");
        String detail = kind + " parked at " + parkedAt + " expired after " + maxAgeHours
                + "h: its merchant route never arrived, so it was never notified";
        deliveryLogRepository.save(new DeliveryLog(null, null, null, sourceEventKey, null, null,
                (short) 1, EXPIRED, detail, clock.instant()));
    }
}
