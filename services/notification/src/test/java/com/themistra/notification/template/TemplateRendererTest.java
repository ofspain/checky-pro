package com.themistra.notification.template;

import com.themistra.notification.common.config.LinkProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code package.md} §8's named test ({@code shouldRenderTemplateWithEventDataAndSelectedChannel}),
 * plus Kimi Phase 8 Findings #2/#3/#4/#6 (token/PII-safe {@code toString()}, computed links
 * override caller-supplied values, version surfaced, malformed-placeholder handling). Mocked
 * repository and a fixed, non-blank {@link LinkProperties}, no Spring context, no Docker - real
 * DB-backed proof (real seeded rows, blank/`null` `baseUrl`) is
 * {@link TemplateRendererIntegrationTest}'s own job.
 */
class TemplateRendererTest {

    private final TemplateRepository repository = mock(TemplateRepository.class);
    private final LinkProperties linkProperties = new LinkProperties("https://checky.pro");
    private final TemplateRenderer renderer = new TemplateRenderer(repository, linkProperties);

    private static Template template(String subject, String body, int version) {
        Template template = mock(Template.class);
        when(template.getSubject()).thenReturn(subject);
        when(template.getBody()).thenReturn(body);
        when(template.getVersion()).thenReturn(version);
        return template;
    }

    @Test
    void shouldRenderTemplateWithEventDataAndSelectedChannel() {
        Template template = template("Hi {{displayName}}", "Body for {{displayName}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("email.verify", "EMAIL"))
                .thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("email.verify", "EMAIL",
                Map.of("displayName", "Ada"));

        assertThat(message.subject()).isEqualTo("Hi Ada");
        assertThat(message.body()).isEqualTo("Body for Ada");
    }

    @Test
    void missingPlaceholderRendersAsEmptyString() {
        Template template = template(null, "Hi {{displayName}}, {{missing}}!", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL", Map.of("displayName", "Ada"));

        assertThat(message.body()).isEqualTo("Hi Ada, !");
        assertThat(message.subject()).isNull();
    }

    /** Kimi Phase 3 Finding #9: a key present with an explicit `null` value is treated identically
     * to a missing key, not the literal string "null". */
    @Test
    void nullValueInEventDataRendersAsEmptyString() {
        Template template = template(null, "Token: {{token}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));
        Map<String, String> eventData = new HashMap<>();
        eventData.put("token", null);

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL", eventData);

        assertThat(message.body()).isEqualTo("Token: ");
    }

    /** Kimi Phase 3 Finding #1: `Matcher.quoteReplacement` protects against `$`/`\` in values that
     * would otherwise break `Matcher.appendReplacement`'s own group-reference parsing. */
    @Test
    void valuesContainingDollarAndBackslashDoNotBreakSubstitution() {
        Template template = template(null, "Hi {{displayName}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL",
                Map.of("displayName", "$100 \\backslash\\"));

        assertThat(message.body()).isEqualTo("Hi $100 \\backslash\\");
    }

    @Test
    void renderedMessageSurfacesTheFetchedTemplatesOwnVersion() {
        Template template = template(null, "body", 7);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL", Map.of());

        assertThat(message.version()).isEqualTo(7);
    }

    @Test
    void unknownTemplateThrowsIllegalArgumentException() {
        when(repository.findTopByNameAndChannelOrderByVersionDesc("does.not.exist", "EMAIL"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> renderer.render("does.not.exist", "EMAIL", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Kimi Phase 8 Finding #3 (AC6): a caller-supplied value under one of the 5 known link keys
     * must never win over the renderer's own computed value. */
    @Test
    void computedLinkPlaceholdersOverrideCallerSuppliedValues() {
        Template template = template(null, "{{verificationLink}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL",
                Map.of("verificationLink", "CALLER_SUPPLIED", "token", "abc"));

        assertThat(message.body()).isEqualTo("https://checky.pro/verify-email?token=abc");
    }

    /** Kimi Phase 8 Finding #6: malformed/invalid placeholders (empty, digit-leading, hyphenated,
     * unclosed) are left as literal text - the regex simply never matches them. */
    @Test
    void malformedPlaceholdersAreLeftAsLiteralText() {
        Template template = template(null, "{{}} {{123}} {{a-b}} {{unclosed} {{displayName}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL", Map.of("displayName", "Ada"));

        assertThat(message.body()).isEqualTo("{{}} {{123}} {{a-b}} {{unclosed} Ada");
    }

    /** Kimi Phase 8 Finding #2 (L4): the record's own {@code toString()} must never print
     * {@code subject}/{@code body} content - only the fetched token could otherwise leak through a
     * stray debug/error log. */
    @Test
    void renderedMessageToStringExcludesSubjectAndBodyContent() {
        var message = new TemplateRenderer.RenderedMessage(
                "Verify your account", "Click https://checky.pro/verify-email?token=super-secret-token", 1);

        String stringified = message.toString();

        assertThat(stringified).doesNotContain("super-secret-token");
        assertThat(stringified).doesNotContain("Verify your account");
        assertThat(stringified).contains("version=1");
    }

    /** Kimi Phase 8 Finding #5: a blank {@code baseUrl} (the `local` profile's own already-
     * established, intentional state) produces a relative link, never an exception. */
    @Test
    void blankBaseUrlProducesARelativeLink() {
        TemplateRenderer localRenderer = new TemplateRenderer(repository, new LinkProperties(""));
        Template template = template(null, "{{verificationLink}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = localRenderer.render("x", "EMAIL", Map.of("token", "abc"));

        assertThat(message.body()).isEqualTo("/verify-email?token=abc");
    }

    /** Same as above, defensively, for a {@code null} {@code baseUrl} - should not occur in
     * practice (the VERBATIM config always binds at least an empty string), but must not throw. */
    @Test
    void nullBaseUrlProducesARelativeLinkNotAnException() {
        TemplateRenderer localRenderer = new TemplateRenderer(repository, new LinkProperties(null));
        Template template = template(null, "{{getStartedLink}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = localRenderer.render("x", "EMAIL", Map.of());

        assertThat(message.body()).isEqualTo("");
    }

    @Test
    void resolveRejectsNullArguments() {
        assertThatThrownBy(() -> renderer.render(null, "EMAIL", Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> renderer.render("x", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> renderer.render("x", "EMAIL", null))
                .isInstanceOf(NullPointerException.class);
    }
}
