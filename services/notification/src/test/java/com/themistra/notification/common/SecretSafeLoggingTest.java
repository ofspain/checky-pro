package com.themistra.notification.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code SecretSafeLogging.redact}'s own correctness (AC1-AC4, AC6). Plain JUnit, no Spring
 * context, no Docker - the real-`TemplateRenderer`-output scenario (Kimi Phase 3 Finding #5) is
 * {@link SecretSafeLoggingIntegrationTest}'s own job.
 */
class SecretSafeLoggingTest {

    @Test
    void redactMasksASingleTokenParameter() {
        assertThat(SecretSafeLogging.redact("https://checky.pro/verify-email?token=abc123"))
                .isEqualTo("https://checky.pro/verify-email?token=***");
    }

    @Test
    void redactMasksASingleSecretParameter() {
        assertThat(SecretSafeLogging.redact("secret=xyz")).isEqualTo("secret=***");
    }

    @Test
    void redactMasksASinglePasswordParameter() {
        assertThat(SecretSafeLogging.redact("password=hunter2")).isEqualTo("password=***");
    }

    @Test
    void redactMasksApiKeyAndApiUnderscoreKeyParameters() {
        assertThat(SecretSafeLogging.redact("apikey=abc")).isEqualTo("apikey=***");
        assertThat(SecretSafeLogging.redact("api_key=abc")).isEqualTo("api_key=***");
    }

    @Test
    void redactMasksMultipleDistinctSecretsInOneInput() {
        assertThat(SecretSafeLogging.redact("token=abc&secret=xyz&password=hunter2"))
                .isEqualTo("token=***&secret=***&password=***");
    }

    /** Kimi Phase 8 Finding #7: byte-for-byte passthrough, including special characters and
     * unicode, for text with no secret-shaped substring at all. */
    @Test
    void redactLeavesNonMatchingTextCompletelyUnchanged() {
        String text = "Hi Ada! Your invoice is $100 (café ☕) — no secrets here: 100% done.";
        assertThat(SecretSafeLogging.redact(text)).isEqualTo(text);
    }

    @Test
    void redactReturnsNullForNullInput() {
        assertThat(SecretSafeLogging.redact(null)).isNull();
    }

    @Test
    void redactIsCaseInsensitiveOnTheKeyButPreservesItsOwnOriginalCasing() {
        assertThat(SecretSafeLogging.redact("TOKEN=abc")).isEqualTo("TOKEN=***");
        assertThat(SecretSafeLogging.redact("Token=abc")).isEqualTo("Token=***");
    }

    /** Kimi Phase 8 Finding #6: the documented `&`/whitespace value boundary - `redact` is scoped
     * to URL query-string-shaped input (Kimi Phase 3 Finding #4/#6), so a plain-text value
     * containing a space is only masked up to the space. */
    @Test
    void redactOnlyMasksUpToTheNextAmpersandOrWhitespace() {
        assertThat(SecretSafeLogging.redact("password=hello world")).isEqualTo("password=*** world");
        assertThat(SecretSafeLogging.redact("token=abc&next=value")).isEqualTo("token=***&next=value");
    }

    @Test
    void redactMasksAnEmptyValueToo() {
        assertThat(SecretSafeLogging.redact("token=&next=value")).isEqualTo("token=***&next=value");
    }

    /** Kimi Phase 8 Finding #4 (AC6): the sole constructor exists only to prevent instantiation. */
    @Test
    void cannotBeInstantiated() throws NoSuchMethodException {
        Constructor<SecretSafeLogging> constructor = SecretSafeLogging.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(SecretSafeLogging.class.getModifiers())).isTrue();
    }
}
