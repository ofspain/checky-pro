package com.themistra.crypto.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * `Watcher`/`WatcherRegistry` tuning (task 16, O2/O5). {@code shardCount} is the ShedLock-leased-shard
 * count (design.md O5) - every replica computes a watch's shard independently via
 * {@code Math.floorMod(watchId.hashCode(), shardCount)}, so this value must be identical across every
 * replica in a deployment (not itself validated here - a cross-replica-consistency concern outside a
 * single process's own config binding).
 */
@ConfigurationProperties(prefix = "themistra.crypto.watcher")
@Validated
public record WatcherProperties(
        @Min(1) int shardCount,
        @Min(1) long reconciliationIntervalMs,
        @Min(1) long correlationWindowMs,
        @Min(1) long lockAtLeastForMs,
        @Min(1) long lockAtMostForMs,
        @Min(1) long finalityPollIntervalMs
) {
}
