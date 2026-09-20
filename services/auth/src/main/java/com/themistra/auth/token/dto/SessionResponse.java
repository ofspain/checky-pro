package com.themistra.auth.token.dto;

import com.themistra.auth.token.RefreshTokenFamily;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound view of one active session — a refresh-token family (T28, R36). {@code deviceLabel} is
 * {@code null} for every session today: {@code design.md} §4b's O3 (device-label source) remains
 * unresolved by the spec author, and nothing in this codebase populates the column yet
 * (frozen brief D6) — a {@code null} value here is expected, not a defect.
 */
public record SessionResponse(UUID familyId, String deviceLabel, Instant createdAt, Instant rotatedAt) {

    public static SessionResponse from(RefreshTokenFamily family) {
        return new SessionResponse(
                family.getFamilyId(), family.getDeviceLabel(), family.getCreatedAt(), family.getRotatedAt());
    }
}
