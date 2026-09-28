package com.themistra.notification.template;

import com.themistra.notification.common.config.LinkProperties;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a template name/channel to its currently-versioned, rendered subject/body (L9, R14):
 * fetches the highest-{@code version} row for {@code (name, channel)}, substitutes every
 * {@code {{key}}} placeholder from a merged values map, and surfaces the version used so a future
 * {@code DeliveryLog} write (task 11) can record it.
 *
 * <p><strong>Link placeholders</strong> (Kimi Phase 3 Finding #2, {@code design.md} §11 Q4):
 * {@code verificationLink}/{@code resetLink}/{@code getStartedLink}/{@code invoiceLink}/
 * {@code receiptLink} are computed by this class itself from {@link LinkProperties#baseUrl()} plus
 * a fixed, disclosed-as-provisional path convention - never read directly from the caller's own
 * {@code eventData}, even if a caller happened to supply a value under one of those 5 keys. The
 * raw source keys for the two invoice/receipt links ({@code invoiceUuid}/{@code receiptUuid})
 * match {@code spec/payment-service/design.md}'s own {@code receipt.issued} event schema - the
 * real, already-pinned future producer of that data - and are deliberately distinct from
 * {@code {{invoiceId}}}, a separate, ordinary display placeholder already seeded in T02's own
 * {@code V3__seed_launch_templates.sql} (substituted directly from {@code eventData.get("invoiceId")}
 * like any other placeholder, no special computation). This naming inconsistency between
 * {@code invoiceId} (display) and {@code invoiceUuid} (link source) predates this task and is not
 * fixed here - {@code V3} is a prior task's own committed file, out of this task's own scope.</p>
 */
@Service
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)}}");

    private final TemplateRepository repository;
    private final LinkProperties linkProperties;

    public TemplateRenderer(TemplateRepository repository, LinkProperties linkProperties) {
        this.repository = repository;
        this.linkProperties = linkProperties;
    }

    public record RenderedMessage(String subject, String body, int version) {
    }

    /**
     * @param name the template's own {@code name} column value. Must not be {@code null}.
     * @param channel {@code EMAIL} or {@code IN_APP} (case-sensitive - matches the real seeded
     *                values exactly, unlike {@code PreferenceResolver}'s own case-insensitive
     *                {@code category}/{@code channel}, since this value always originates from
     *                this codebase's own literal string constants, never external caller input).
     *                Must not be {@code null}.
     * @param eventData raw, event-specific values to substitute. Must not be {@code null}; may
     *                  contain {@code null} values (treated identically to a missing key).
     * @throws IllegalArgumentException if no template exists for {@code (name, channel)}.
     */
    public RenderedMessage render(String name, String channel, Map<String, String> eventData) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(eventData, "eventData must not be null");

        Template template = repository.findTopByNameAndChannelOrderByVersionDesc(name, channel)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no template for name=" + name + ", channel=" + channel));

        Map<String, String> values = new HashMap<>(eventData);
        values.putAll(computeLinkPlaceholders(eventData));

        String subject = template.getSubject() == null ? null : substitute(template.getSubject(), values);
        String body = substitute(template.getBody(), values);
        return new RenderedMessage(subject, body, template.getVersion());
    }

    private Map<String, String> computeLinkPlaceholders(Map<String, String> eventData) {
        String baseUrl = normalizeBaseUrl(linkProperties.baseUrl());
        Map<String, String> links = new HashMap<>();

        String token = eventData.get("token");
        if (token != null) {
            String encodedToken = urlEncode(token);
            links.put("verificationLink", baseUrl + "/verify-email?token=" + encodedToken);
            links.put("resetLink", baseUrl + "/reset-password?token=" + encodedToken);
        }

        links.put("getStartedLink", baseUrl);

        String invoiceUuid = eventData.get("invoiceUuid");
        if (invoiceUuid != null) {
            links.put("invoiceLink", baseUrl + "/invoices/" + urlEncode(invoiceUuid));
        }

        String receiptUuid = eventData.get("receiptUuid");
        if (receiptUuid != null) {
            links.put("receiptLink", baseUrl + "/receipts/" + urlEncode(receiptUuid));
        }

        return links;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String substitute(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
