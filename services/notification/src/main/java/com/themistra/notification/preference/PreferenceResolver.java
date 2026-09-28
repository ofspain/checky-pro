package com.themistra.notification.preference;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves whether a given recipient should be notified on a given channel for a given category
 * (L6, R9/R10/R18): honours a stored opt-out when one exists, falls back to the documented default
 * (design.md §4c) when none exists, and never fails or defaults to "send everywhere."
 *
 * <p><strong>{@code SECURITY}+{@code EMAIL} is a hard floor</strong> (design.md §4c's own
 * parenthetical: "email cannot be disabled"): this pair resolves {@code true} unconditionally,
 * without ever querying the repository, regardless of any stored row (Phase 1's own resolved open
 * question, option (a)).</p>
 *
 * <p>{@code category}/{@code channel} are uppercased before every lookup (Kimi Phase 3 Finding #2)
 * to match the DB's own {@code CHECK}-constrained uppercase value space
 * ({@code SECURITY}/{@code PAYMENT}/{@code MARKETING}, {@code EMAIL}/{@code IN_APP}/
 * {@code WEBHOOK}/{@code PUSH}). Any {@code (category, channel)} pair outside the 6 documented
 * defaults - including {@code WEBHOOK}/{@code PUSH}, which the DB permits but §4c never defaults -
 * resolves {@code false} when no stored row exists (Kimi Phase 3 Findings #1/#3).</p>
 */
@Service
public class PreferenceResolver {

    private static final Map<String, Boolean> DEFAULTS = Map.of(
            "SECURITY:EMAIL", true,
            "SECURITY:IN_APP", true,
            "PAYMENT:EMAIL", true,
            "PAYMENT:IN_APP", true,
            "MARKETING:EMAIL", false,
            "MARKETING:IN_APP", false);

    private final ChannelPreferenceRepository repository;

    public PreferenceResolver(ChannelPreferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * @param accountUuid the recipient's own external identifier. Must not be {@code null}.
     * @param category one of {@code SECURITY}/{@code PAYMENT}/{@code MARKETING} (case-insensitive).
     *                 Must not be {@code null}.
     * @param channel one of {@code EMAIL}/{@code IN_APP}/{@code WEBHOOK}/{@code PUSH}
     *                (case-insensitive). Must not be {@code null}.
     */
    public boolean resolve(UUID accountUuid, String category, String channel) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(channel, "channel must not be null");

        String normalizedCategory = category.toUpperCase(Locale.ROOT);
        String normalizedChannel = channel.toUpperCase(Locale.ROOT);

        if ("SECURITY".equals(normalizedCategory) && "EMAIL".equals(normalizedChannel)) {
            return true;
        }

        return repository.findByAccountUuidAndCategoryAndChannel(accountUuid, normalizedCategory, normalizedChannel)
                .map(ChannelPreference::isEnabled)
                .orElseGet(() -> defaultFor(normalizedCategory, normalizedChannel));
    }

    private boolean defaultFor(String category, String channel) {
        return DEFAULTS.getOrDefault(category + ":" + channel, false);
    }
}
