package com.themistra.notification.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reusable redaction utility for L4/R15 - "rendered bodies and log lines never contain access/
 * refresh tokens, raw secrets, full API keys, or reset-token values beyond the single intended
 * one-time link." The underlying per-class discipline this class formalizes already exists since
 * T06 ({@code EmailRequestedEvent.toString()}) and T09 ({@code RenderedMessage.toString()}); this
 * class is new, reusable infrastructure for future logging code (e.g. {@code DeliveryOrchestrator},
 * task 11) that logs free-form content a fixed DTO's own {@code toString()} override can't cover.
 * Not yet wired into any real call site - same "seam built ahead of its caller" shape as
 * {@code NotificationDispatcher} (T06), {@code PreferenceResolver} (T08), {@code TemplateRenderer}
 * (T09).
 */
public final class SecretSafeLogging {

    private static final Pattern SECRET_PARAM_PATTERN = Pattern.compile(
            "(?i)(token|secret|password|api_?key)=([^&\\s]*)");

    private SecretSafeLogging() {
        // static utility - never instantiated (Kimi Phase 3 Finding #3)
    }

    /**
     * Masks the value half of any {@code token=}/{@code secret=}/{@code password=}/
     * {@code apikey=}/{@code api_key=}-shaped substring (case-insensitive key) with {@code ***},
     * preserving the key's own original casing.
     *
     * <p>Scoped to URL query-string-shaped input only (Kimi Phase 3 Finding #4) - JSON
     * ({@code "token": "..."}) and HTTP-header-shaped ({@code Authorization: Bearer ...}) secrets
     * are out of this method's own scope. A matched value terminates at the next {@code &} or
     * whitespace character (Kimi Phase 3 Finding #6), matching query-string semantics exactly -
     * {@code redact("password=hello world")} masks only {@code "hello"}, leaving {@code "world"}
     * exposed, since a space is not a valid query-string value character in the first place.</p>
     *
     * @param text the text to redact. {@code null} returns {@code null} - a deliberately lenient
     *             contract for an observability utility a caller might invoke while already
     *             handling an error, unlike this module's own stricter caller-contract precedent
     *             for core business-logic arguments ({@code PreferenceResolver.resolve},
     *             {@code TemplateRenderer.render}).
     */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SECRET_PARAM_PATTERN.matcher(text);
        return matcher.replaceAll(match -> match.group(1) + "=***");
    }
}
