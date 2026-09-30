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
 * Kimi Phase 3 Finding #1 (tightened) / Finding #7 (records) / Phase 8 Finding #2 / Phase 11 Gaps
 * #2-#4: a permanent static-scan guard (AC5) - walks every real {@code .java} file in this
 * module, finds {@code String}-typed field/record-component declarations named
 * (case-insensitively) {@code token}/{@code secret}/{@code password}/{@code apiKey}/
 * {@code api_key}, and asserts each such file's own *enclosing type* (not merely "some type
 * somewhere in the file") both declares an explicit {@code toString()} and that method's own body
 * does not reference the sensitive field.
 *
 * <p>Strips line and block comments before scanning (Phase 11 Gap #4) - a Javadoc sentence
 * mentioning a field or type name in prose must never trigger a false positive.
 * Distinguishes an actual field/record-component declaration from a local variable of the same
 * shape (Phase 11 Gap #2's own empirically-confirmed concern -
 * {@code TemplateRenderer.computeLinkPlaceholders}'s own {@code String token = eventData.get(...)}
 * local variable would otherwise be misidentified as a sensitive field) by requiring an access
 * modifier (field) or a preceding {@code (}/{@code ,} (record component). Tracks each match's own
 * enclosing type by scope (Phase 11 Gap #3's own empirically-confirmed concern -
 * {@code TemplateRenderer.java} has two type declarations, the outer class and the nested
 * {@code RenderedMessage} record; a sensitive field in one must never be checked against the
 * other's own {@code toString()}), by finding the nearest preceding type-declaration start and the
 * next one as that scope's own boundary - a lightweight approximation, not a real parser, but
 * correct for every shape actually used in this module (one outer type, at most one nested type,
 * never siblings needing true nesting-depth tracking).
 *
 * <p><strong>Known, disclosed limitations</strong> (Phase 0/1, Phase 11 Gap #2): (1) a field-name
 * heuristic cannot catch a semantically-sensitive-but-ambiguously-named field, e.g.
 * {@code RenderedMessage.body}; (2) a multi-field declaration on one line
 * ({@code String token, secret;}) only matches the first name; (3) a non-{@code String} secret
 * type ({@code byte[] password}) is not matched at all. None of these three shapes occurs anywhere
 * in this codebase today (verified) - not fixed here.</p>
 */
class SensitiveFieldsHaveSafeToStringTest {

    private static final Pattern TYPE_DECLARATION_PATTERN =
            Pattern.compile("\\b(?:class|record|interface)\\s+\\w+");
    private static final Pattern FIELD_DECLARATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:private|protected|public)\\s+(?:final\\s+|static\\s+)*(?:java\\.lang\\.)?"
                    + "String\\s+(token|secret|password|apiKey|api_key)\\b");
    private static final Pattern RECORD_COMPONENT_PATTERN = Pattern.compile(
            "(?i)[(,]\\s*(?:java\\.lang\\.)?String\\s+(token|secret|password|apiKey|api_key)\\b");
    // Deliberately requires "public String toString() {" as one contiguous run, not just the bare
    // text "toString()" - a Javadoc sentence mentioning toString() (as several classes in this
    // module already do, to explain why a field is excluded) would otherwise be misidentified as
    // the real method declaration. (Comment-stripping below closes most of this already, but the
    // tightened pattern is kept as a second, independent defense.)
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
                .as("every sensitive field must be excluded from its own type's toString()")
                .isEmpty();
    }

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

    /** Kimi Phase 11 Gap #6: a record-shaped positive fixture, not just a class-shaped one. */
    @Test
    void scanAcceptsARecordWithAnExplicitToStringThatExcludesTheSensitiveComponent() {
        String safeRecord = "record Fixture(String token, java.time.Instant occurredAt) { "
                + "public String toString() { return \"Fixture[redacted]\"; } }";

        assertThat(findUnsafeToStringViolations(safeRecord)).isEmpty();
    }

    @Test
    void scanIgnoresFilesWithNoSensitiveField() {
        String irrelevant = "class Fixture { private final String displayName; }";

        assertThat(findUnsafeToStringViolations(irrelevant)).isEmpty();
    }

    /** Kimi Phase 11 Gap #2 (empirically confirmed against this exact codebase -
     * {@code TemplateRenderer.computeLinkPlaceholders}'s own local variable): a local variable of
     * the same shape as a sensitive field must never be mistaken for one. */
    @Test
    void scanIgnoresALocalVariableThatHappensToShareASensitiveName() {
        String withLocalVariable = "class Fixture { void method() { String token = fetch(); use(token); } }";

        assertThat(findUnsafeToStringViolations(withLocalVariable)).isEmpty();
    }

    /** Kimi Phase 11 Gap #4: a comment mentioning a sensitive-looking name in prose must never
     * trigger a false positive. */
    @Test
    void scanIgnoresSensitiveNamesMentionedOnlyInComments() {
        String commentOnly = "/** This class never stores a String token anywhere. */\n"
                + "class Fixture { private final String displayName; }";

        assertThat(findUnsafeToStringViolations(commentOnly)).isEmpty();
    }

    /** Kimi Phase 11 Gap #3 (empirically confirmed against this exact codebase -
     * {@code TemplateRenderer.java} has two type declarations, the outer class and the nested
     * {@code RenderedMessage} record): a sensitive field in one type must be checked against that
     * same type's own {@code toString()}, not whichever one happens to appear first in the file. */
    @Test
    void scanChecksEachTypesOwnToStringNotTheFirstOneInTheFile() {
        String twoTypesOneFileSecondLeaks = "class Safe { private final String displayName; "
                + "public String toString() { return \"Safe[redacted]\"; } } "
                + "class Unsafe { private final String token; "
                + "public String toString() { return \"Unsafe[token=\" + token + \"]\"; } }";

        assertThat(findUnsafeToStringViolations(twoTypesOneFileSecondLeaks)).isNotEmpty();
    }

    @Test
    void scanDoesNotFalselyFlagATypeWhoseSensitiveFieldIsSafelyExcludedEvenWhenAnotherTypeInTheSameFileIsAlsoSafe() {
        String twoTypesOneFileBothSafe = "class First { private final String token; "
                + "public String toString() { return \"First[redacted]\"; } } "
                + "record Second(String displayName) { public String toString() { return \"Second[redacted]\"; } }";

        assertThat(findUnsafeToStringViolations(twoTypesOneFileBothSafe)).isEmpty();
    }

    private static List<String> findUnsafeToStringViolations(String rawSource) {
        String source = stripComments(rawSource);
        List<String> violations = new ArrayList<>();

        List<Integer> typeStarts = new ArrayList<>();
        Matcher typeMatcher = TYPE_DECLARATION_PATTERN.matcher(source);
        while (typeMatcher.find()) {
            typeStarts.add(typeMatcher.start());
        }

        List<Integer> fieldPositions = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        for (Pattern pattern : List.of(FIELD_DECLARATION_PATTERN, RECORD_COMPONENT_PATTERN)) {
            Matcher fieldMatcher = pattern.matcher(source);
            while (fieldMatcher.find()) {
                fieldPositions.add(fieldMatcher.start());
                fieldNames.add(fieldMatcher.group(1));
            }
        }

        for (int i = 0; i < fieldPositions.size(); i++) {
            int position = fieldPositions.get(i);
            String fieldName = fieldNames.get(i);

            int scopeStart = 0;
            int scopeEnd = source.length();
            for (int typeStart : typeStarts) {
                if (typeStart <= position) {
                    scopeStart = typeStart;
                } else {
                    scopeEnd = typeStart;
                    break;
                }
            }
            String scope = source.substring(scopeStart, scopeEnd);

            Matcher toStringMatcher = TO_STRING_DECLARATION_PATTERN.matcher(scope);
            if (!toStringMatcher.find()) {
                violations.add(fieldName + ": no explicit toString() declared in its own type's scope");
                continue;
            }
            String body = extractBraceBody(scope, toStringMatcher.end() - 1);
            if (toStringBodyReferencesField(body, fieldName)) {
                violations.add(fieldName + ": toString() references the sensitive field");
            }
        }
        return violations;
    }

    private static String stripComments(String source) {
        String withoutBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        return withoutBlockComments.replaceAll("//[^\n]*", "");
    }

    private static String extractBraceBody(String source, int openBraceIndex) {
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
