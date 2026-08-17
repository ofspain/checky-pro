<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T33 · Phase 1 — Specification Extraction

## Business Rules

- **R44.** When an `auth.email.requested` event is emitted, `EventTopics` shall route it to the
  `auth.email.requested` Kafka topic. (Routing already implemented and already tested; this task's
  work is authoring the schema file + contract test, not the routing logic itself.)
- **R45.** When an `auth.security.audit` mirror event is emitted, `EventTopics` shall route it to
  the `auth.security.audit` Kafka topic. (Same situation as R44 — routing pre-exists.)
- **R47.** Where `contracts/api/auth.yaml` is authored, the service responses and generated client
  models shall conform to it. (This is the task's actual new work: author the spec, then prove
  conformance.)

## Locked Decisions

None scoped to this task (confirmed via the header and by reading `design.md`'s full L1-L14 list —
none constrain contract authoring or event-schema documentation).

## Files involved

**Existing files to read (not modify):**
- All 6 controllers and their DTOs (`AccountController`, `AdminAccountController`,
  `ApiKeyController`, `AdminAccountRoleController`, `AdminRoleController`,
  `AdminRoleTemplateController`, `AdminAuditController`) — the 28 endpoints `auth.yaml` must
  describe (Phase 0 §2's full table).
- `account/event/EmailRequestedEventPayload.java` — `accountUuid` (UUID, non-null), `purpose`
  (String, non-null), `token` (String, non-null — the raw token, a documented LOCKED exception per
  its own Javadoc, unrelated to this task), `occurredAt` (Instant, non-null).
- `audit/AuditMirrorPayload.java` — `eventType` (String, non-null), `outcome` (`AuditOutcome` enum:
  `SUCCESS`/`FAILURE`, non-null), `accountUuid` (UUID, **nullable** — confirmed via
  `AuditService.partitionKey`'s null-fallback and `AuditEvent`'s own `account_id` FK, itself
  nullable), `actorUuid` (UUID, **nullable** — confirmed via `V1__auth_baseline_schema.sql`'s
  `actor_uuid UUID` column with no `NOT NULL`, and `AuditEvent.java`'s matching unconstrained
  `@Column`), `occurredAt` (Instant, non-null).
- `contracts/events/auth/user-lifecycle.v1.schema.json` — the exact template/pattern for the two
  new schema files (JSON Schema draft 2020-12 shape, `$id` convention, `required`/`properties`/
  `additionalProperties: false`).
- `account/event/UserLifecycleEventPayloadContractTest.java` — the exact template/pattern for both
  new contract tests (named test's own required pattern, per the task statement).
- `events/EventTopicsTest.java` — already contains
  `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic`; nothing to add here for R44's routing
  half, confirmed already correct and already tested.

**New files the spec expects** (`design.md`'s own file tree, T33's slice of it):
- `contracts/api/auth.yaml` (new)
- `contracts/events/auth/email-requested.v1.schema.json` (new)
- `contracts/events/auth/security-audit.v1.schema.json` (new)
- A new contract test for each of the two new event schemas, following
  `UserLifecycleEventPayloadContractTest`'s pattern (exact file/class names: Phase 2/5 decision).
- A new test/mechanism for `shouldConformToAuthOpenApiContract` — genuinely novel, no existing
  pattern in this codebase (Phase 0 §5); Phase 2/5 must decide its shape.

**Explicitly NOT this task's file:** `contracts/api/token-claims.md` (R48, task 34).

## Dependencies

- Jackson `ObjectMapper` (`findAndRegisterModules()`, ISO date serialization) — already the
  established contract-test dependency, no new library needed for the two event-schema tests.
- For `shouldConformToAuthOpenApiContract`: either the same plain-Jackson-traversal technique
  (parsing the YAML as a tree and checking response shapes against it) or a new OpenAPI/JSON-Schema
  validation library — undecided, a genuine Phase 2/5 design question (Phase 0 §5).
- No new Spring beans, no new persistence, no new Kafka topic (both topics already exist and are
  already routed to).

## Acceptance Criteria

- **AC1 (R44).** The `auth.email.requested` schema file exists, matches
  `EmailRequestedEventPayload`'s actual serialized shape (required fields present, no undeclared
  fields), and a contract test proves it — mirroring `UserLifecycleEventPayloadContractTest`
  exactly. (Routing itself is already proven by the pre-existing `EventTopicsTest` test — not a new
  AC for this task.)
- **AC2 (R45).** The `auth.security.audit` schema file exists, matches `AuditMirrorPayload`'s actual
  serialized shape (including correctly modeling `accountUuid`/`actorUuid` as nullable/not-required,
  not incorrectly marking them required), and a contract test proves it.
- **AC3 (R47).** `contracts/api/auth.yaml` exists, documents all 28 non-SAS endpoints (Phase 0 §2's
  table) with request/response shapes traceable to the real DTOs, and
  `shouldConformToAuthOpenApiContract` proves at least a meaningful subset of real
  responses conform to it (exact scope — every endpoint vs. a representative sample — a Phase 2/5
  decision, since design.md doesn't specify "every single endpoint must have its own dedicated
  conformance test").

## Tests required

- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` (named, `package.md` §8) — **already
  exists** in `EventTopicsTest.java`; no new work needed here, confirmed in Phase 0.
- `shouldConformToAuthOpenApiContract` (named, `package.md` §8) — does not exist yet; genuinely new
  design work, no established pattern to copy (Phase 0 §5).
- Two new contract tests (unnamed by `package.md`, but required by the task statement's own wording
  — "add contract tests" plural, one per new schema file), each following
  `UserLifecycleEventPayloadContractTest`'s exact structure: a "serialized payload matches schema"
  test and, where an enum is involved (`AuditOutcome` for the audit schema; `EmailRequestedEventPayload`
  has no enum field, so its second test — if any — would need a different boundary, e.g. covering
  both `purpose` values mentioned in its own Javadoc, `"verify_email"`/`"password_reset"`).

## Open Questions

No blockers remaining after this phase's own verification (Phase 0's nullability question is now
resolved above: both `accountUuid` and `actorUuid` on the audit mirror payload are genuinely
nullable at the schema/entity level, not just possibly-null by omission).

Genuine design questions carried forward to Phase 2 (not blockers — Phase 2 is exactly where these
get resolved, possibly via Kimi/human gate at Phase 3/4):
1. How `shouldConformToAuthOpenApiContract` should work mechanically, given no existing pattern or
   library in this codebase (Phase 0 §5).
2. Whether `auth.yaml`'s conformance test needs to cover all 28 endpoints or a representative
   subset — `package.md`/`requirements.md` don't specify a coverage bar for this named test.
3. Exact file/class names for the two new event-payload contract tests (mechanical, low-risk,
   Phase 5's call — likely `EmailRequestedEventPayloadContractTest`/`AuditMirrorPayloadContractTest`
   by direct analogy to the existing one's naming).

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
