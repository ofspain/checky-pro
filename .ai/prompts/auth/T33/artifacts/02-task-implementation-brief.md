<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T33 · Phase 2 — Task Implementation Brief

## Task

Author `contracts/api/auth.yaml` (OpenAPI, all 28 non-SAS endpoints),
`contracts/events/auth/email-requested.v1.schema.json`, and
`contracts/events/auth/security-audit.v1.schema.json`; add contract tests for all three following
`UserLifecycleEventPayloadContractTest`'s pattern.

## Purpose

Make the API/event contracts this service already implements the documented, CI-checked source of
truth other services and clients can rely on (R47), and close the last two undocumented event
schemas (R44/R45's routing already works — this closes the documentation/verification gap around
it).

## Scope

**In:** the 3 new contract files; 2 new event-payload contract tests (email-requested,
security-audit) mirroring the existing pattern exactly; 1 new OpenAPI-conformance test
(`shouldConformToAuthOpenApiContract`, the named test).

**Out:** any change to `EventTopics`, any controller/DTO, `contracts/api/token-claims.md` (task
34), any new Kafka topic, any change to the routing logic itself (already correct, already
tested).

## Business Rules

- **R44.** `auth.email.requested` events route to the `auth.email.requested` topic — routing
  already implemented/tested; this task documents and schema-verifies the payload shape.
- **R45.** `auth.security.audit` events route to the `auth.security.audit` topic — same situation.
- **R47.** `auth.yaml`, once authored, is the contract service responses must conform to.

## Locked Decisions

None scoped to this task.

## Dependencies (tentative, D1/D2 below — Kimi/Phase 4 to challenge)

- **D1.** `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (new, **test scope only**) to
  parse `auth.yaml` into a Jackson `JsonNode` tree for the conformance test, reusing the exact same
  `JsonNode`-traversal technique `UserLifecycleEventPayloadContractTest` already uses for JSON
  Schema — no new validation library, consistent with this codebase's stated reason for avoiding
  one (target-design §17.5). SnakeYAML is already transitively present (via Spring Boot) but not a
  direct dependency; adding the small Jackson YAML module directly is judged more robust than
  relying on an undeclared transitive artifact for a real test dependency.
- **D2.** `shouldConformToAuthOpenApiContract` validates by **component schema**, not by
  live HTTP call: for each distinct response DTO the 28 endpoints use (`AccountResponse`,
  `ApiKeyMetadata`, `CreateApiKeyResult`, `AuditEventResponse`, etc. — a much smaller set than 28,
  since many endpoints share a response shape), serialize a real instance and compare it against
  `auth.yaml`'s `components.schemas.<Name>` the same way the event contract tests compare a
  payload against its JSON Schema. No Testcontainers, no live server, no Spring context — matches
  this codebase's existing contract-test style (plain JUnit) rather than introducing a new,
  heavier integration-style conformance mechanism for a documentation-correctness check.

## Inputs

None (build/test-time only; no runtime behavior change).

## Outputs

Three new contract files under version control; three new/extended test files proving they match
reality at authoring time and going forward.

## State Changes

None.

## Files to Create

- `contracts/api/auth.yaml`
- `contracts/events/auth/email-requested.v1.schema.json`
- `contracts/events/auth/security-audit.v1.schema.json`
- `services/auth/src/test/java/com/themistra/auth/account/event/EmailRequestedEventPayloadContractTest.java`
  (same package as `EmailRequestedEventPayload`, mirroring
  `UserLifecycleEventPayloadContractTest`'s placement convention)
- `services/auth/src/test/java/com/themistra/auth/audit/AuditMirrorPayloadContractTest.java` (same
  package as `AuditMirrorPayload`)
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` (holds
  `shouldConformToAuthOpenApiContract`; placed in `common` since it spans DTOs from multiple
  modules — a cross-cutting concern per L12's own framing, not owned by any single feature module)

## Files to Modify

- `services/auth/pom.xml` (D1's new test-scope dependency)

## Files NOT to Modify

- Any controller, DTO, `EventTopics`, `EventTopicsTest` (already correct, already tested).
- `contracts/api/token-claims.md` (task 34).
- Any `spec/` file.

## Acceptance Criteria

- **AC1 (R44).** `email-requested.v1.schema.json` exists and matches
  `EmailRequestedEventPayload`'s real serialized shape; a contract test proves it, following the
  existing pattern exactly (including a second test covering both documented `purpose` values,
  `verify_email`/`password_reset`, analogous to the existing enum-coverage test).
- **AC2 (R45).** `security-audit.v1.schema.json` exists and matches `AuditMirrorPayload`'s real
  serialized shape — critically, `accountUuid`/`actorUuid` modeled as **not required** (both are
  genuinely nullable, confirmed at Phase 1); a contract test proves the shape and an
  `AuditOutcome`-enum-coverage test mirrors the existing `AccountStatus` one.
- **AC3 (R47).** `auth.yaml` documents all 28 non-SAS endpoints (Phase 0's table) with
  request/response schemas traceable to real DTOs; `shouldConformToAuthOpenApiContract` proves
  every distinct component schema referenced by those endpoints matches its real DTO's serialized
  shape (D2's scope — by component, not by individual route).

## Required Tests

- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` — already exists, no new work.
- `shouldConformToAuthOpenApiContract` (named) — new, per D1/D2.
- Email-requested and security-audit contract tests (task statement's own "add contract tests,"
  plural) — new, mirroring the existing pattern exactly per file.

## Constraints

- **Performance:** none (build/test-time only).
- **Security:** the audit schema must not mark genuinely-nullable fields as required (would create
  a false contract client-side); the email-requested schema's `token` field is a documented,
  already-LOCKED-elsewhere exception to "credential appears once" — not re-litigated by this task,
  just accurately documented.
- **Thread-safety / Transaction:** N/A.
- **Module boundaries (L12):** the new OpenAPI contract test lives in `common` since it spans
  multiple modules' DTOs by nature; the two event-payload contract tests stay within their owning
  module, matching the existing pattern.
- **Null handling:** the audit schema's `required` list must exactly match genuine non-nullability,
  verified against real code (Phase 1), not assumed from field presence in the Java record.

## Open Questions

No blockers. D1/D2 above are tentative implementation choices flagged explicitly for Phase 3/4
challenge, not settled facts — in particular, D2's "by component schema, not by individual route"
scope decision for the named test is the one most likely to need a human gate call if Kimi raises a
coverage concern.

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
