package com.themistra.crypto.screening;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC6 (L15, module boundaries). {@code screening/} is a genuinely new top-level package with no
 * legitimate cross-module dependency at all - unlike {@code reorg/} (which needs {@code
 * events.OutboxPublisher}), this task emits no event and needs no other feature module's type.
 *
 * <p>Phase 9 (Kimi Phase 8 Finding #6): an explicit forbidden-prefix list (mirroring {@code
 * ReorgModuleBoundaryTest}'s style) is not exhaustive against a future package this list's author
 * simply forgot to add. Inverted here into a true allow-list instead: every
 * {@code com.themistra.crypto.*} import must start with {@code com.themistra.crypto.screening.} or
 * {@code com.themistra.crypto.common.} - nothing else in this codebase's own top-level namespace is
 * permitted, named or not.</p> */
class ScreeningModuleBoundaryTest {

    private static final String THEMISTRA_CRYPTO_IMPORT_PREFIX = "import com.themistra.crypto.";
    private static final List<String> ALLOWED_THEMISTRA_CRYPTO_IMPORT_PREFIXES = List.of(
            "import com.themistra.crypto.screening.",
            "import com.themistra.crypto.common.");

    @Test
    void noMainSourceFileInScreeningImportsAnyThemistraCryptoPackageOutsideScreeningOrCommon() {
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
                    if (!importLine.startsWith(THEMISTRA_CRYPTO_IMPORT_PREFIX)) {
                        continue;
                    }
                    boolean allowed = ALLOWED_THEMISTRA_CRYPTO_IMPORT_PREFIXES.stream()
                            .anyMatch(importLine::startsWith);
                    assertThat(allowed)
                            .as("%s imports %s, which is outside screening/common (L15)", javaFile,
                                    importLine)
                            .isTrue();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
