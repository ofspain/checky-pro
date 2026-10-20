package com.themistra.notification.routing;

import java.time.Instant;

/** A parked payment event removed by expiry: its key, its kind and when it was parked. Never the
 * payload. */
public record ExpiredParkedEvent(String eventKey, String kind, Instant parkedAt) {
}
