package com.themistra.crypto.watch.dto;

import java.util.UUID;

/** {@code POST /internal/v1/watches} success response - VERBATIM shape (`design.md` §4c):
 * {@code { watchId, status: "REGISTERED" }}. {@code status} is a plain string, not the {@code
 * WatchStatus} enum type itself - {@code WatchController} converts explicitly
 * ({@code watch.status().name()}), keeping this wire type free of any dependency on the internal
 * entity/enum shape (which is free to change independently of the VERBATIM wire contract). */
public record RegisterWatchResponse(UUID watchId, String status) {
}
