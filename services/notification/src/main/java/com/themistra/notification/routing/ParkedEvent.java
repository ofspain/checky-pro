package com.themistra.notification.routing;

/** A parked payment event as plain values (L11: the seam never hands out entities). */
public record ParkedEvent(String eventKey, String kind, String payload) {
}
