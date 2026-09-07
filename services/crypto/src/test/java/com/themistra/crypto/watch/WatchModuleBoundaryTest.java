package com.themistra.crypto.watch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC8 (L15, module boundaries) - mirrors {@code TokenModuleBoundaryTest} (T11) / {@code
 * FinalityModuleBoundaryTest} (T14)'s exact source-scan style: a plain static file scan over
 * {@code import} lines, not ArchUnit. {@code watch/}'s one legitimate cross-module dependency is
 * {@code token.AddressValidator} (T15 Phase 3/4 Finding 1) - a stateless predicate, not an entity - so
 * that single import is allow-listed rather than {@code token} being forbidden outright. */
class WatchModuleBoundaryTest {

    private static final List<String> FULLY_FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.themistra.crypto.observation",
            "com.themistra.crypto.provider",
            "com.themistra.crypto.quorum",
            "com.themistra.crypto.finality",
            "com.themistra.crypto.events",
            "com.themistra.crypto.adapter");

    private static final String TOKEN_IMPORT_PREFIX = "com.themistra.crypto.token";

    private static final Set<String> ALLOWED_TOKEN_IMPORTS = Set.of(
            "import com.themistra.crypto.token.AddressValidator;");

    @Test
    void noMainSourceFileInWatchImportsBeyondItsAllowedTokenTypeOrAnyForbiddenPackage() {
        Path watchMainSourceDir = Path.of("src/main/java/com/themistra/crypto/watch");
        assertThat(watchMainSourceDir).isDirectory();

        try (Stream<Path> files = Files.walk(watchMainSourceDir)) {
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
                    if (importLine.contains(TOKEN_IMPORT_PREFIX)) {
                        assertThat(ALLOWED_TOKEN_IMPORTS)
                                .as("%s imports %s from token, which is not the one allowed type "
                                        + "(AddressValidator) (L15)", javaFile, importLine)
                                .contains(importLine);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
