package com.themistra.notification.preference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code findByAccountUuidAndCategoryAndChannel} is the only sanctioned call
 * (Kimi Phase 3 Finding #4). {@code extends JpaRepository}, mirroring T04's {@code ProcessedEventRepository}
 * and T05's {@code ContactProjectionRepository}, even though the inherited {@code save}/{@code saveAll}/
 * {@code delete} mutators are never called by anything in this codebase - {@code channel_preferences}
 * has no write API anywhere in this spec, and {@code V6}'s own grant (SELECT only) makes any
 * accidental mutator call fail at the DB layer regardless of what this interface exposes in Java.
 */
interface ChannelPreferenceRepository extends JpaRepository<ChannelPreference, Long> {

    Optional<ChannelPreference> findByAccountUuidAndCategoryAndChannel(
            UUID accountUuid, String category, String channel);
}
