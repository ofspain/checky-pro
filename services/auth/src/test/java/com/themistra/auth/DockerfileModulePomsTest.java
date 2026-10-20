package com.themistra.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FU-T27-1 (open-issues sweep, 2026-10-19). Maven validates the whole reactor even with
 * {@code -pl}, so the Dockerfile must copy the pom of every module the root pom declares.
 * Leaving one out broke this image's build once already, when a sibling module was added. The
 * module list is read from the root pom, so adding a module fails this test until it is copied.
 */
class DockerfileModulePomsTest {

    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path ROOT_POM = Path.of("../../pom.xml");

    @Test
    void dockerfileCopiesThePomOfEveryReactorModule() throws IOException {
        String rootPom = Files.readString(ROOT_POM).replaceAll("(?s)<!--.*?-->", "");
        List<String> modules = new ArrayList<>();
        Matcher matcher = Pattern.compile("<module>([^<]+)</module>").matcher(rootPom);
        while (matcher.find()) {
            modules.add(matcher.group(1).trim());
        }
        assertThat(modules).as("the root pom's module list").contains("services/auth");

        List<String> copies = Files.readAllLines(DOCKERFILE).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("COPY ") && line.contains("pom.xml"))
                .toList();
        assertThat(copies).as("the root pom is copied").contains("COPY pom.xml ./");
        for (String module : modules) {
            assertThat(copies).as("%s/pom.xml is copied", module)
                    .contains("COPY " + module + "/pom.xml " + module + "/");
        }
    }

    @Test
    void dockerfileBuildsOnlyThisModule() throws IOException {
        List<String> runs = Files.readAllLines(DOCKERFILE).stream()
                .map(String::trim).filter(line -> line.startsWith("RUN mvn")).toList();
        assertThat(runs).isNotEmpty().allSatisfy(line -> assertThat(line).contains("-pl services/auth "));
    }
}
