package com.themistra.notification.template;

import com.themistra.notification.common.config.LinkProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    /** Kimi Phase 11 Gap #1: a permanent, cheap static guard for the exact merge-order shape the
     * whole "computed links win" guarantee rests on - a future edit that reversed the two
     * `putAll` calls would otherwise only be caught by re-running Phase 10's own manual mutation
     * test by hand. */
    @Test
    void renderMergesEventDataBeforeOverlayingComputedLinkPlaceholders() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/template/TemplateRenderer.java"));

        int eventDataCopyIndex = source.indexOf("new HashMap<>(eventData)");
        int computedOverlayIndex = source.indexOf("values.putAll(computeLinkPlaceholders(eventData))");

        assertThat(eventDataCopyIndex).as("values must start as a copy of eventData").isGreaterThan(-1);
        assertThat(computedOverlayIndex).as("computed links must be overlaid afterward").isGreaterThan(-1);
        assertThat(eventDataCopyIndex)
                .as("eventData must be copied first, then computed links overlaid on top - not the reverse")
                .isLessThan(computedOverlayIndex);
    }

    /** Kimi Phase 11 Gap #3: locks the {@code resetLink} path convention specifically - the URL-
     * encoding test alone would still pass if a future edit accidentally reused
     * {@code /verify-email} for both link types. */
    @Test
    void resetLinkUsesTheResetPasswordPathNotVerifyEmail() {
        Template template = template(null, "{{resetLink}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL", Map.of("token", "abc"));

        assertThat(message.body()).isEqualTo("https://checky.pro/reset-password?token=abc");
    }

    /** Kimi Phase 11 Gap #4: a naive {@code toString()} could still leak the literal string
     * "null" for a null subject/body, or format differently than the non-null case - proves the
     * override handles both uniformly. */
    @Test
    void toStringHandlesNullSubjectAndBodyWithoutTheLiteralNullString() {
        var message = new TemplateRenderer.RenderedMessage(null, null, 1);

        String stringified = message.toString();

        assertThat(stringified).contains("version=1");
        assertThat(stringified).doesNotContain("null");
    }

    /** Kimi Phase 11 Gap #5: {@code normalizeBaseUrl} only strips a trailing slash - a configured
     * path prefix (a plausible real deployment shape) must survive untouched. */
    @Test
    void baseUrlWithAPathPrefixIsPreservedInTheComputedLink() {
        TemplateRenderer prefixedRenderer = new TemplateRenderer(repository, new LinkProperties("https://checky.pro/app"));
        Template template = template(null, "{{verificationLink}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = prefixedRenderer.render("x", "EMAIL", Map.of("token", "abc"));

        assertThat(message.body()).isEqualTo("https://checky.pro/app/verify-email?token=abc");
    }

    /** Kimi Phase 11 Gap #6: an explicit empty string is a valid value, distinct from a missing
     * key or an explicit `null` - must render as empty, not literally "{{key}}" or anything else. */
    @Test
    void emptyStringEventDataValueRendersAsEmptyString() {
        Template template = template(null, "Hi {{displayName}}!", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));

        TemplateRenderer.RenderedMessage message = renderer.render("x", "EMAIL", Map.of("displayName", ""));

        assertThat(message.body()).isEqualTo("Hi !");
    }

    /** Kimi Phase 11 Gap #7: {@code render} must never attempt to write into the caller's own
     * {@code eventData} map - passing a genuinely immutable map proves this, since any write
     * attempt would throw {@code UnsupportedOperationException}. */
    @Test
    void renderNeverMutatesTheCallersEventDataMap() {
        Template template = template(null, "{{verificationLink}} {{displayName}}", 1);
        when(repository.findTopByNameAndChannelOrderByVersionDesc("x", "EMAIL")).thenReturn(Optional.of(template));
        Map<String, String> immutableEventData = Map.of("displayName", "Ada", "token", "abc");

        assertThatCode(() -> renderer.render("x", "EMAIL", immutableEventData)).doesNotThrowAnyException();
        assertThat(immutableEventData).containsOnlyKeys("displayName", "token");
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
