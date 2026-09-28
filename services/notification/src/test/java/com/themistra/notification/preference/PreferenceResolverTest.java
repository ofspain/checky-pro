package com.themistra.notification.preference;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code package.md} §8's 3 named tests
 * ({@code shouldResolveChannelPreferencesPerRecipient}, {@code shouldSuppressChannelWhenRecipientOptedOut},
 * {@code shouldFallBackToDefaultPreferenceWhenNoneSet}), plus Kimi Phase 8 Findings #1/#2/#3/#4/#6
 * (hard floor never queries the repository, default-table drift protection, unknown-pair coverage,
 * null rejection). Mocked repository, no Spring context, no Docker - real DB-backed proof (case
 * normalization against a stored row, the hard floor against an adversarial stored row) is
 * {@link PreferenceResolverIntegrationTest}'s own job.
 */
class PreferenceResolverTest {

    private final ChannelPreferenceRepository repository = mock(ChannelPreferenceRepository.class);
    private final PreferenceResolver resolver = new PreferenceResolver(repository);

    @Test
    void shouldResolveChannelPreferencesPerRecipient() {
        UUID accountUuid = UUID.randomUUID();
        ChannelPreference stored = channelPreference(true);
        when(repository.findByAccountUuidAndCategoryAndChannel(accountUuid, "PAYMENT", "IN_APP"))
                .thenReturn(Optional.of(stored));

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "IN_APP")).isTrue();
    }

    @Test
    void shouldSuppressChannelWhenRecipientOptedOut() {
        UUID accountUuid = UUID.randomUUID();
        ChannelPreference stored = channelPreference(false);
        when(repository.findByAccountUuidAndCategoryAndChannel(accountUuid, "PAYMENT", "EMAIL"))
                .thenReturn(Optional.of(stored));

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "EMAIL")).isFalse();
    }

    @Test
    void shouldFallBackToDefaultPreferenceWhenNoneSet() {
        UUID accountUuid = UUID.randomUUID();
        when(repository.findByAccountUuidAndCategoryAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "EMAIL")).isTrue();
        assertThat(resolver.resolve(accountUuid, "PAYMENT", "IN_APP")).isTrue();
        assertThat(resolver.resolve(accountUuid, "MARKETING", "EMAIL")).isFalse();
        assertThat(resolver.resolve(accountUuid, "MARKETING", "IN_APP")).isFalse();
        assertThat(resolver.resolve(accountUuid, "SECURITY", "IN_APP")).isTrue();
    }

    /** Kimi Phase 8 Finding #1: the {@code SECURITY}+{@code EMAIL} floor never even queries the
     * repository - the strongest form of the guarantee, stronger than "returns true when queried." */
    @Test
    void securityEmailNeverQueriesTheRepository() {
        UUID accountUuid = UUID.randomUUID();

        boolean result = resolver.resolve(accountUuid, "SECURITY", "EMAIL");

        assertThat(result).isTrue();
        verifyNoInteractions(repository);
    }

    /** Kimi Phase 3 Finding #2: proves normalization reaches the repository call itself, not just
     * the return value - captures the exact arguments passed. */
    @Test
    void categoryAndChannelAreUppercasedBeforeTheRepositoryQuery() {
        UUID accountUuid = UUID.randomUUID();
        when(repository.findByAccountUuidAndCategoryAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        resolver.resolve(accountUuid, "payment", "in_app");

        var categoryCaptor = forClass(String.class);
        var channelCaptor = forClass(String.class);
        verify(repository).findByAccountUuidAndCategoryAndChannel(
                org.mockito.ArgumentMatchers.eq(accountUuid), categoryCaptor.capture(), channelCaptor.capture());
        assertThat(categoryCaptor.getValue()).isEqualTo("PAYMENT");
        assertThat(channelCaptor.getValue()).isEqualTo("IN_APP");
    }

    /** Kimi Phase 8 Findings #1/#3/#4: any pair outside the 6 documented defaults resolves `false`
     * when no row exists - covers the DB-permitted-but-undefaulted channels and an entirely unknown
     * category. */
    @Test
    void unrecognizedPairsResolveFalseWhenNoRowExists() {
        UUID accountUuid = UUID.randomUUID();
        when(repository.findByAccountUuidAndCategoryAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "WEBHOOK")).isFalse();
        assertThat(resolver.resolve(accountUuid, "SECURITY", "PUSH")).isFalse();
        assertThat(resolver.resolve(accountUuid, "MARKETING", "WEBHOOK")).isFalse();
        assertThat(resolver.resolve(accountUuid, "COMPLIANCE", "EMAIL")).isFalse();
    }

    /** Kimi Phase 8 Finding #6: locks the documented "must not be null" contract - a future
     * refactor that removed the null checks would not fail any other test here. */
    @Test
    void resolveRejectsNullArguments() {
        UUID accountUuid = UUID.randomUUID();

        assertThatThrownBy(() -> resolver.resolve(null, "SECURITY", "EMAIL"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.resolve(accountUuid, null, "EMAIL"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.resolve(accountUuid, "SECURITY", null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Kimi Phase 8 Finding #3: prevents drift between the frozen brief's own VERBATIM default
     * table (design.md §4c) and {@code PreferenceResolver}'s own hardcoded {@code DEFAULTS} map -
     * parses the 3 real prose lines directly rather than duplicating their values here by hand. */
    @Test
    void defaultsMapMatchesTheDesignDocVerbatimTableExactly() throws IOException, ReflectiveOperationException {
        String designDoc = Files.readString(Path.of("../../spec/notification-service/design.md"));
        Pattern linePattern = Pattern.compile(
                "category = (\\w+).*?: email = (ON|OFF),\\s*in_app = (ON|OFF)");
        Matcher matcher = linePattern.matcher(designDoc);

        Map<String, Boolean> expected = new java.util.HashMap<>();
        int lineCount = 0;
        while (matcher.find()) {
            lineCount++;
            String category = matcher.group(1);
            expected.put(category + ":EMAIL", "ON".equals(matcher.group(2)));
            expected.put(category + ":IN_APP", "ON".equals(matcher.group(3)));
        }
        assertThat(lineCount).as("design.md's own default table must have exactly 3 category lines").isEqualTo(3);
        assertThat(expected).as("6 entries: 3 categories x 2 channels").hasSize(6);

        Field defaultsField = PreferenceResolver.class.getDeclaredField("DEFAULTS");
        defaultsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Boolean> actual = (Map<String, Boolean>) defaultsField.get(null);

        assertThat(actual).isEqualTo(expected);
    }

    private static ChannelPreference channelPreference(boolean enabled) {
        ChannelPreference preference = mock(ChannelPreference.class);
        when(preference.isEnabled()).thenReturn(enabled);
        return preference;
    }
}
