package com.themistra.crypto.finality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC6 (L15, module boundaries; Phase 3 Finding 2) - mirrors {@code TokenModuleBoundaryTest} (T11) /
 * {@code ProviderModuleBoundaryTest} (T10)'s exact source-scan style: a plain static file scan over
 * {@code import} lines, not ArchUnit. One difference from those two precedents: {@code token}'s and
 * {@code provider}'s own boundary tests forbid {@code com.themistra.crypto.adapter} entirely, but
 * {@code finality} legitimately needs two specific types from it - so the {@code adapter} prefix is
 * allow-listed to exactly those two imports rather than forbidden outright. */
class FinalityModuleBoundaryTest {

    private static final List<String> FULLY_FORBIDDEN_IMPORT_PREFIXES = List.of(
            "com.themistra.crypto.observation",
            "com.themistra.crypto.provider",
            "com.themistra.crypto.quorum",
            "com.themistra.crypto.token",
            "com.themistra.crypto.events");

    private static final String ADAPTER_IMPORT_PREFIX = "com.themistra.crypto.adapter";

    private static final Set<String> ALLOWED_ADAPTER_IMPORTS = Set.of(
            "import com.themistra.crypto.adapter.Chain;",
            "import com.themistra.crypto.adapter.model.FinalityStatus;");

    @Test
    void noMainSourceFileInFinalityImportsBeyondItsAllowedAdapterTypesOrAnyForbiddenPackage() {
        Path finalityMainSourceDir = Path.of("src/main/java/com/themistra/crypto/finality");
        assertThat(finalityMainSourceDir).isDirectory();

        try (Stream<Path> files = Files.walk(finalityMainSourceDir)) {
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
                    if (importLine.contains(ADAPTER_IMPORT_PREFIX)) {
                        assertThat(ALLOWED_ADAPTER_IMPORTS)
                                .as("%s imports %s from adapter, which is not one of the two allowed "
                                        + "types (Chain, FinalityStatus) (L15)", javaFile, importLine)
                                .contains(importLine);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
