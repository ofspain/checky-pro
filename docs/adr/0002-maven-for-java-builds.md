# ADR-0002: Maven for all Java builds

- **Status:** accepted · 2026-07-13 · supersedes the Gradle multi-project layout from the initial scaffold

## Decision

All Java services and libraries in the monorepo build with **Maven**, as a multi-module build
rooted at `/pom.xml` (aggregator + parent, inheriting `spring-boot-starter-parent`). Modules are
added to `<modules>` as they gain code. `settings.gradle` is removed.

## Context

The initial scaffold assumed Gradle. The team's tooling familiarity is Maven (the reference
identity project and prior work are Maven), and build-tool fluency matters more than Gradle's
configuration flexibility for a four-service fleet with conventional Spring Boot builds.

## Consequences

- Per-service builds: `mvn -pl services/<name> verify`; CI path filters unchanged.
- Convention sharing happens through the parent POM (plugin/dependency management) rather than
  Gradle convention plugins.
- Trade-off accepted: slower incremental builds than Gradle's caching; acceptable at this scale.
  Revisit only if build times become a measured bottleneck.
