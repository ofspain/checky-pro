package com.themistra.notification.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimi Phase 3 Finding #1 (tightened) / Finding #7 (records) / Phase 8 Finding #2: a permanent
 * static-scan guard (AC5) - walks every real {@code .java} file in this module, finds
 * {@code String}-typed field/record-component declarations named (case-insensitively) {@code
 * token}/{@code secret}/{@code password}/{@code apiKey}/{@code api_key}, and asserts each such
 * file both declares an explicit {@code toString()} (a record's own auto-generated one is never
 * written into the source text at all, so its absence is correctly detected) and that method's own
 * body does not reference the sensitive field - a {@code toString()} that merely exists but still
 * prints the field would otherwise pass a weaker, presence-only check.
 *
 * <p><strong>Known, disclosed limitation</strong> (Phase 0/1): a field-name heuristic cannot catch
 * a semantically-sensitive-but-ambiguously-named field, e.g. {@code RenderedMessage.body}, which
 * legitimately embeds a token as rendered text under an ordinary-looking name - T09's own
 * equivalent gap was caught by Kimi's adversarial review, not any automated scan, and this test
 * does not close that class of gap either.</p>
 */
class SensitiveFieldsHaveSafeToStringTest {

    private static final Pattern SENSITIVE_FIELD_PATTERN =
            Pattern.compile("(?i)\\bString\\s+(token|secret|password|apiKey|api_key)\\b");
    // Deliberately requires "public String toString() {" as one contiguous run, not just the bare
    // text "toString()" - a Javadoc sentence mentioning toString() (as several classes in this
    // module already do, to explain why a field is excluded) would otherwise be misidentified as
    // the real method declaration.
    private static final Pattern TO_STRING_DECLARATION_PATTERN =
            Pattern.compile("public\\s+String\\s+toString\\s*\\(\\s*\\)\\s*\\{");

    @Test
    void everyRealProductionFileWithASensitiveFieldHasASafeToString() throws IOException {
        Path mainSourceDir = Path.of("src/main/java/com/themistra/notification");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(mainSourceDir)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String violation : findUnsafeToStringViolations(source)) {
                    violations.add(path + ": " + violation);
                }
            }
        }

        assertThat(violations)
                .as("every sensitive field must be excluded from its own class's toString()")
                .isEmpty();
    }

    /** Proves the scan's own logic actually enforces content, not just presence (Kimi Phase 8
     * Finding #2's own concern about the scan design, not the code - verified false as a
     * description of the current frozen brief, but the underlying property is worth locking with
     * a real test regardless). */
    @Test
    void scanCatchesAToStringThatStillPrintsTheSensitiveField() {
        String leaking = "class Fixture { private final String token; "
                + "public String toString() { return \"Fixture[token=\" + token + \"]\"; } }";

        assertThat(findUnsafeToStringViolations(leaking)).isNotEmpty();
    }

    /** Kimi Phase 3 Finding #7: a record's own auto-generated `toString()` is never written into
     * the source text, so the scan correctly treats "no explicit override" as a violation. */
    @Test
    void scanCatchesARecordWithASensitiveComponentAndNoExplicitToString() {
        String unsafeRecord = "record Fixture(String token, java.time.Instant occurredAt) {}";

        assertThat(findUnsafeToStringViolations(unsafeRecord)).isNotEmpty();
    }

    @Test
    void scanAcceptsAToStringThatExcludesTheSensitiveField() {
        String safe = "class Fixture { private final String token; "
                + "public String toString() { return \"Fixture[redacted]\"; } }";

        assertThat(findUnsafeToStringViolations(safe)).isEmpty();
    }

    @Test
    void scanIgnoresFilesWithNoSensitiveField() {
        String irrelevant = "class Fixture { private final String displayName; }";

        assertThat(findUnsafeToStringViolations(irrelevant)).isEmpty();
    }

    private static List<String> findUnsafeToStringViolations(String source) {
        List<String> violations = new ArrayList<>();
        Matcher fieldMatcher = SENSITIVE_FIELD_PATTERN.matcher(source);
        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            if (!TO_STRING_DECLARATION_PATTERN.matcher(source).find()) {
                violations.add(fieldName + ": no explicit toString() declared");
                continue;
            }
            String body = extractToStringBody(source);
            if (toStringBodyReferencesField(body, fieldName)) {
                violations.add(fieldName + ": toString() references the sensitive field");
            }
        }
        return violations;
    }

    private static String extractToStringBody(String source) {
        Matcher matcher = TO_STRING_DECLARATION_PATTERN.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        int openBraceIndex = matcher.end() - 1;
        int depth = 0;
        int i = openBraceIndex;
        for (; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        return source.substring(openBraceIndex, Math.min(i + 1, source.length()));
    }

    private static boolean toStringBodyReferencesField(String toStringBody, String fieldName) {
        Pattern fieldRef = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b");
        String capitalized = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        Pattern getterRef = Pattern.compile("\\bget" + capitalized + "\\s*\\(");
        return fieldRef.matcher(toStringBody).find() || getterRef.matcher(toStringBody).find();
    }
}
