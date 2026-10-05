package com.themistra.notification.channel;

import org.springframework.web.client.RestTemplate;

/**
 * T16 - deliberately violates L2: a declared, never-read {@link RestTemplate} field. Placed
 * inside {@code com.themistra.notification} (not the top-level {@code archtestfixtures} package
 * {@code RogueUnmappedEntity} uses) because {@code shouldMakeNoSynchronousCrossServiceCall}'s own
 * subject-side condition is a positive inclusion ({@code resideInAPackage(ANALYZED_PACKAGE +
 * "..")}) - a fixture entirely outside the service would never be a valid subject for that rule at
 * all, unlike the exclusion-shaped subject conditions {@code services/crypto}'s own
 * {@code KmsSignerArchitectureTest} rules use (a real design correction caught at Phase 5, before
 * any code was written). Still never swept into the real canary's own scan
 * ({@code ImportOption.DoNotIncludeTests} excludes every test-source class regardless of package).
 * {@code public} so {@code ArchitectureTest} (a different package) can reference it via a class
 * literal, mirroring {@code services/crypto}'s own identical rogue-fixture visibility.
 */
public class RogueHttpClientUser {

    private final RestTemplate restTemplate = null;
}
