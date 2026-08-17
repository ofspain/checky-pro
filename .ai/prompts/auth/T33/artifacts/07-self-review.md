<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T33 · Phase 7 — Self Review

Reviewed the full diff (3 contract files, 3 test files, 1 pom.xml dependency) against the frozen
brief and `agents.md`. Thread-safety, transaction boundaries, and money types don't apply to
build/test-time contract files — noted as N/A rather than silently skipped.

## Finding 1 — `controllerRoutes()`'s completeness check silently skips a handler that uses a bare `@RequestMapping` with no explicit HTTP method

**Severity:** Medium

**Evidence:** `AuthOpenApiContractTest.java`, `controllerRoutes()`:
```java
for (var httpMethod : mapping.method()) {
    routes.add(new Route(httpMethod.name(), fullPath));
}
```
Every handler in this codebase today uses a method-fixing shorthand (`@GetMapping`,
`@PostMapping`, etc.), each of which meta-annotates `@RequestMapping(method = ...)` with exactly
one method — so `mapping.method()` always has exactly one element today. But a *bare*
`@RequestMapping(path = "/x")` with no `method` attribute defaults to an **empty** `method()`
array (meaning "matches any HTTP method" at the Spring MVC level). If a future handler were ever
written that way, this loop would add **zero** `Route` entries for it — not flag it as
undocumented, not error, just silently omit it from both completeness checks entirely. The gap
would go undetected precisely because nothing fails.

**Recommendation:** Not fixing now — no handler in this codebase triggers it today, and the frozen
brief's scope is documenting what exists, not hardening the test harness against every
hypothetical future annotation style. Flagging so a future PR adding a bare `@RequestMapping`
handler doesn't get a false sense of "the completeness test would have caught this."

## Correctness — verified, not merely inspected

- Both halves of the named test were proven non-vacuous via real negative-proof runs (Phase 6):
  removing a real path from `auth.yaml` fails the forward check; adding a fake path fails the
  reverse check.
- `everyComponentSchemaMatchesItsRealDtoShape`'s sample instances already exercise the genuinely
  nullable fields as null where they matter (`ApiKeyMetadata.lastUsedAt/expiresAt/revokedAt`,
  `SessionResponse.deviceLabel`) — not an oversight, deliberately chosen when writing the
  instances, though not restated in a comment; documented here for a future reader.
- The YAML-authoring error caught by the tests themselves during Phase 6 (a malformed quoted
  scalar) is exactly the class of defect this task exists to prevent — treating it as evidence the
  approach works, not as a mistake to note as a finding.

## Boundary conditions considered

- Nullable fields across every DTO were traced to their actual source (Phase 1/5's inventory), not
  assumed from record component names — `SessionResponse.deviceLabel`, `ApiKeyMetadata`'s three
  timestamp fields, `AuditEventResponse`'s five optional fields, and the event schemas'
  `accountUuid`/`actorUuid` are all correctly excluded from their respective `required` lists.
- `Page`'s exact wire shape was empirically confirmed (a real serialized instance), not assumed
  from general Spring Data knowledge — this specifically guards against the class of documentation
  drift this whole task exists to prevent.

## Module boundaries (L12)

`AuthOpenApiContractTest` (in `common`) imports DTOs from `account`, `apikey`, `audit`, `authz`,
and `token` — a deliberate, already-gated Phase 4 decision (cross-cutting contract test), not an
oversight. Confirmed this doesn't trip any existing `ArchitectureTest` rule: none of the 10 rules
restrict inbound dependencies *into* `common`, and `@AnalyzeClasses`'s `DoNotIncludeTests` import
option excludes this file from ArchUnit's own analysis population entirely regardless (the same
reason `PublicEndpointsTest`'s existing cross-module-ish import was never previously flagged).

## Readability / complexity

- `realInstancesByComponentName()` is a long, flat map-building method (19 entries) with no
  branching — high line count, low actual complexity. Acceptable given the alternative (splitting
  across multiple smaller methods) would add indirection without reducing risk.
- The reflection-based route-extraction logic (`controllerRoutes()`) is denser than most test code
  in this file, but each step has a clear purpose and Finding 1 above documents its one known
  limitation rather than leaving it implicit.

## No findings on

Enumeration-safety/secret-handling (the email-requested schema's raw `token` field and the API-key
`plaintextKey` field are both documented with explicit descriptions naming them as intentional,
already-decided exceptions, not new leaks); idempotency (N/A, no state mutation); null-safety
(covered above).

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
