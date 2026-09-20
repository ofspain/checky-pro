package com.themistra.crypto.token;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC6 (L15, module boundaries) — mirrors {@code ProviderModuleBoundaryTest} (T10) exactly: a simple
 * static source scan, not a new ArchUnit convention. */
class TokenModuleBoundaryTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.themistra.crypto.adapter",
            "com.themistra.crypto.observation",
            "com.themistra.crypto.provider",
            "com.themistra.crypto.quorum");

    @Test
    void noMainSourceFileInTokenImportsAdapterObservationProviderOrQuorum() {
        Path tokenMainSourceDir = Path.of("src/main/java/com/themistra/crypto/token");
        assertThat(tokenMainSourceDir).isDirectory();

        try (Stream<Path> files = Files.walk(tokenMainSourceDir)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertThat(javaFiles).isNotEmpty();

            for (Path javaFile : javaFiles) {
                List<String> importLines = Files.readAllLines(javaFile).stream()
                        .filter(line -> line.trim().startsWith("import "))
                        .toList();
                for (String importLine : importLines) {
                    for (String forbidden : FORBIDDEN_IMPORT_PREFIXES) {
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
