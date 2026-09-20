package com.themistra.crypto.reorg;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC6 (L15, module boundaries) - mirrors {@code FinalityModuleBoundaryTest} (T14)'s exact source-scan
 * style: a plain static file scan over {@code import} lines, not ArchUnit. {@code reorg/} is a
 * genuinely new top-level package (T18) with no feature-module entity dependency at all - it only ever
 * needs {@code events.OutboxPublisher} to reach the sole sanctioned publishing path (T18 Phase 3
 * Finding #4: {@code ReorgDetector}'s own API is deliberately primitive-typed so it never needs to
 * import {@code watch.Watch}/{@code watch.ChainCursor}). Every other feature-module prefix is therefore
 * fully forbidden, not merely allow-listed. */
class ReorgModuleBoundaryTest {

    private static final List<String> FULLY_FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.themistra.crypto.watch",
            "com.themistra.crypto.adapter",
            "com.themistra.crypto.observation",
            "com.themistra.crypto.provider",
            "com.themistra.crypto.quorum",
            "com.themistra.crypto.token",
            "com.themistra.crypto.finality");

    private static final String EVENTS_IMPORT_PREFIX = "com.themistra.crypto.events";

    private static final Set<String> ALLOWED_EVENTS_IMPORTS = Set.of(
            "import com.themistra.crypto.events.OutboxPublisher;");

    @Test
    void noMainSourceFileInReorgImportsBeyondItsAllowedEventsTypeOrAnyForbiddenPackage() {
        Path reorgMainSourceDir = Path.of("src/main/java/com/themistra/crypto/reorg");
        assertThat(reorgMainSourceDir).isDirectory();

        try (Stream<Path> files = Files.walk(reorgMainSourceDir)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(javaFiles).isNotEmpty();

            for (Path javaFile : javaFiles) {
                List<String> importLines = Files.readAllLines(javaFile).stream()
                        .map(String::trim)
                        .filter(line -> line.startsWith("import "))
                        .toList();
                for (String importLine : importLines) {
                    for (String forbidden : FULLY_FORBIDDEN_IMPORT_PREFIXES) {
                        assertThat(importLine)
                                .as("%s must not import from %s (L15)", javaFile, forbidden)
                                .doesNotContain(forbidden);
                    }
                    if (importLine.contains(EVENTS_IMPORT_PREFIX)) {
                        assertThat(ALLOWED_EVENTS_IMPORTS)
                                .as("%s imports %s from events, which is not the one allowed type "
                                        + "(OutboxPublisher) (L15)", javaFile, importLine)
                                .contains(importLine);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
