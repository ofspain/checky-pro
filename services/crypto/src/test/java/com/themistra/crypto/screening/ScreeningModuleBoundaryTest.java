package com.themistra.crypto.screening;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC6 (L15, module boundaries) - mirrors {@code ReorgModuleBoundaryTest}'s exact source-scan style.
 * {@code screening/} is a genuinely new top-level package with no legitimate cross-module dependency at
 * all - unlike {@code reorg/} (which needs {@code events.OutboxPublisher}), this task emits no event and
 * needs no other feature module's type, so every other feature-module prefix is fully forbidden with no
 * allow-listed exception. */
class ScreeningModuleBoundaryTest {

    private static final List<String> FULLY_FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.themistra.crypto.watch",
            "com.themistra.crypto.adapter",
            "com.themistra.crypto.observation",
            "com.themistra.crypto.provider",
            "com.themistra.crypto.quorum",
            "com.themistra.crypto.token",
            "com.themistra.crypto.finality",
            "com.themistra.crypto.reorg",
            "com.themistra.crypto.events");

    @Test
    void noMainSourceFileInScreeningImportsAnyForbiddenPackage() {
        Path screeningMainSourceDir = Path.of("src/main/java/com/themistra/crypto/screening");
        assertThat(screeningMainSourceDir).isDirectory();

        try (Stream<Path> files = Files.walk(screeningMainSourceDir)) {
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
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
