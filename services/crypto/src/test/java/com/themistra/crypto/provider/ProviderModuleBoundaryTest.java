package com.themistra.crypto.provider;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC5 (L15, module boundaries) — a simple static source scan, not a new ArchUnit convention (T09
 * Phase 11 deferred introducing ArchUnit itself to a future dedicated task; this mirrors that same
 * "simple static/reflection check" framing the frozen brief itself asked for). */
class ProviderModuleBoundaryTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.themistra.crypto.adapter",
            "com.themistra.crypto.observation",
            "com.themistra.crypto.quorum");

    @Test
    void noMainSourceFileInProviderImportsAdapterObservationOrQuorum() {
        Path providerMainSourceDir = Path.of("src/main/java/com/themistra/crypto/provider");
        assertThat(providerMainSourceDir).isDirectory();

        try (Stream<Path> files = Files.walk(providerMainSourceDir)) {
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
