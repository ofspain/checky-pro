package com.themistra.crypto.watch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** AC8/AC9 (L15, module boundaries) - mirrors {@code TokenModuleBoundaryTest} (T11) / {@code
 * FinalityModuleBoundaryTest} (T14)'s exact source-scan style: a plain static file scan over
 * {@code import} lines, not ArchUnit. {@code watch/} legitimately depends on several other modules'
 * stateless services/value types (T16 gave it real callers into {@code observation}/{@code
 * provider}/{@code quorum}/{@code adapter} for the first time; T17 adds {@code finality}, needed to
 * pick the right {@code FinalityPolicy} for a watch's chain, and {@code events}, needed by the new
 * {@code TxLifecyclePublisher} to reach the sole sanctioned publishing path - mirrors {@code
 * ProviderDegradedPublisher}'s (T10, {@code provider/}) identical, already-established need to import
 * {@code events.OutboxPublisher}; T18 adds {@code reorg}, needed to reach {@code ReorgDetector}) - each
 * such package gets an exact allow-list of the imports actually used, rather than being forbidden
 * outright or left unchecked. No prefix is left fully forbidden any more; every dependency
 * {@code watch/} now has is an intentional, allow-listed one. */
class WatchModuleBoundaryTest {

    private static final List<String> FULLY_FORBIDDEN_IMPORT_PREFIXES = List.of();

    /** Package prefix -> the exact import lines permitted from it. Anything else from a listed prefix
     * (in particular, any of that package's own JPA entities) fails; a prefix not listed here at all is
     * unconstrained by this map (still subject to {@link #FULLY_FORBIDDEN_IMPORT_PREFIXES} above). */
    private static final Map<String, Set<String>> ALLOWED_IMPORTS_BY_PREFIX = Map.of(
            "com.themistra.crypto.token", Set.of(
                    "import com.themistra.crypto.token.AddressValidator;"),
            "com.themistra.crypto.adapter", Set.of(
                    "import com.themistra.crypto.adapter.Chain;",
                    "import com.themistra.crypto.adapter.ProviderSet;",
                    "import com.themistra.crypto.adapter.model.FinalityStatus;",
                    "import com.themistra.crypto.adapter.model.Subscription;",
                    "import com.themistra.crypto.adapter.model.TxResult;"),
            "com.themistra.crypto.observation", Set.of(
                    "import com.themistra.crypto.observation.FactType;",
                    "import com.themistra.crypto.observation.ObservationLog;"),
            "com.themistra.crypto.provider", Set.of(
                    "import com.themistra.crypto.provider.DegradationReason;",
                    "import com.themistra.crypto.provider.ProviderHealthTracker;"),
            "com.themistra.crypto.quorum", Set.of(
                    "import com.themistra.crypto.quorum.ProviderAnswer;",
                    "import com.themistra.crypto.quorum.QuorumDecision;",
                    "import com.themistra.crypto.quorum.QuorumDecisionService;",
                    "import com.themistra.crypto.quorum.QuorumOutcome;"),
            "com.themistra.crypto.finality", Set.of(
                    "import com.themistra.crypto.finality.FinalityPolicy;"),
            "com.themistra.crypto.events", Set.of(
                    "import com.themistra.crypto.events.OutboxPublisher;"),
            "com.themistra.crypto.reorg", Set.of(
                    "import com.themistra.crypto.reorg.ReorgDetector;"));

    @Test
    void noMainSourceFileInWatchImportsBeyondItsAllowedTypesOrAnyForbiddenPackage() {
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
                    for (Map.Entry<String, Set<String>> entry : ALLOWED_IMPORTS_BY_PREFIX.entrySet()) {
                        if (importLine.contains(entry.getKey())) {
                            assertThat(entry.getValue())
                                    .as("%s imports %s, which is not one of the allow-listed types "
                                            + "from %s (L15)", javaFile, importLine, entry.getKey())
                                    .contains(importLine);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
