<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T33 · Phase 0 — Repository Understanding

## 1. Architecture summary

`auth-service` (Spring Boot 3.5.4/Java 21) is feature-modularized under `com.themistra.auth.*`
(`account`, `authn`, `authz`, `apikey`, `audit`, `token`, `cleanup`, `ratelimit`, `mfa`, `events`,
`common`). Two security chains (`token/SecurityChainsConfig.java`): SAS's own protocol chain
(`/oauth2/**`, `/.well-known/**`, `/userinfo`, `/login`) and this service's own application chain
(everything else — `/accounts/**`, `/admin/**`, `/api-keys/**`). Persistence is PostgreSQL via
Flyway (currently `V8`) + Spring Data JPA. Cross-service events go through a Kafka outbox
(`events` package): domain code writes an `OutboxEvent` row in the same transaction as its own
state change; `OutboxRelay` polls and publishes via `OutboxPublisher`, routing by aggregate type
through `EventTopics.forAggregateType(...)` to one of three topics.

The monorepo has a top-level `contracts/` directory (`contracts/README.md`): `events/` holds Kafka
JSON Schemas (one file per topic version, e.g. the existing
`contracts/events/auth/user-lifecycle.v1.schema.json`), `api/` holds OpenAPI specs per service
(currently only `contracts/api/.gitkeep` — empty). Per that README, this directory is meant to be
the generation SOURCE for Java models and the TypeScript client — but no `springdoc`/OpenAPI
codegen tooling exists anywhere in `services/auth/pom.xml` today (confirmed via `grep`), and every
DTO/controller in this service was hand-written well before this task. This task is therefore
retroactively documenting already-existing, already-implemented endpoints in a hand-authored YAML
file, not driving new codegen — a real gap between the README's stated intent and this task's
actual starting position, flagged below rather than silently assumed away.

## 2. Existing code this task touches

**All non-SAS REST endpoints** (28 total across 6 controllers, all under the application security
chain, none under SAS's own protocol chain):

| Controller | Base path | Endpoints |
|---|---|---|
| `AccountController` | `/accounts` | `POST /`, `GET /me`, `POST /verify-email`, `POST /resend-verification`, `POST /password-reset-request`, `POST /password-reset`, `POST /me/password`, `GET /me/sessions`, `DELETE /me/sessions/{familyId}`, `DELETE /me/sessions` |
| `AdminAccountController` | `/admin/accounts` | `GET /{accountUuid}`, `POST /{accountUuid}/activate`, `POST /{accountUuid}/suspend`, `POST /{accountUuid}/reinstate`, `DELETE /{accountUuid}`, `POST /{accountUuid}/unlock` |
| `ApiKeyController` | `/api-keys` | `POST /`, `GET /`, `DELETE /{keyUuid}`, `POST /token` |
| `AdminAccountRoleController` | `/admin/accounts/{accountUuid}` | `GET /roles`, `POST /roles/{roleName}`, `DELETE /roles/{roleName}`, `POST /role-templates/{templateName}`, `DELETE /role-templates/{templateName}` |
| `AdminRoleController` | `/admin/roles` | `POST /`, `GET /` |
| `AdminRoleTemplateController` | `/admin/role-templates` | `POST /`, `GET /` |
| `AdminAuditController` | `/admin/audit` | `GET /` |

Existing DTOs already define most response/request shapes (e.g. `AccountResponse`,
`ApiKeyMetadata`, `CreateApiKeyResult`, `AuditEventResponse`, `CreateApiKeyRequest`,
`ChangePasswordRequest`, `RegisterAccountRequest`, etc.) — the OpenAPI spec's schemas should trace
to these, not invent new shapes.

**Event payloads for the two new schema files:**
- `account/event/EmailRequestedEventPayload.java` (record: `accountUuid`, `purpose`, `token`,
  `occurredAt`) — routed to `auth.email.requested` via `EventTopics.forAggregateType("verification-token")`,
  already correct and already tested (`EventTopicsTest.shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic`
  — **this named test already exists**, added in an earlier task).
- `audit/AuditMirrorPayload.java` (record: `eventType` (String), `outcome` (`AuditOutcome` enum:
  `SUCCESS`/`FAILURE`), `accountUuid` (UUID, **nullable** — confirmed via `AuditService.partitionKey`'s
  own null-fallback), `actorUuid` (UUID, nullability not yet confirmed — Phase 1 to check call
  sites), `occurredAt`) — routed to `auth.security.audit` via `EventTopics.forAggregateType("audit")`.

**`EventTopics.java`** — both routings already correct and already implemented; this task does not
need to change it, only document + schema-verify what it already does.

## 3. Established patterns to follow

- **Event schema files** (`contracts/events/auth/*.v1.schema.json`): JSON Schema draft 2020-12,
  `$id` under `https://checky.pro/contracts/events/auth/...`, `title` naming the topic +
  `(schema_version 1)`, `description` naming the publishing module/mechanism and partition key,
  `required` + `properties` + `additionalProperties: false`. One file exists today
  (`user-lifecycle.v1.schema.json`) as the exact template to follow.
- **Contract tests** (`UserLifecycleEventPayloadContractTest`, the named pattern this task must
  reuse): plain JUnit, no Spring context, reads the schema file via a `../../contracts/...`
  relative path (Maven/Surefire's working directory is the module root, so this resolves to the
  monorepo-root `contracts/`), serializes a real payload instance via a plain `ObjectMapper`
  (`findAndRegisterModules()`, dates as ISO strings not epoch timestamps), then asserts: every
  schema-required field is present, every serialized field is declared in the schema
  (`additionalProperties: false` enforcement done in test code, not a JSON Schema validator
  library — target-design §17.5's own stated reason: not worth adding a validation library for a
  handful of contract files), and enum values match. A second test per payload
  (`everyXValueIsCoveredBySchemaEnum`) proves the schema's enum doesn't silently fall behind a Java
  enum that grows new values.
- **No JSON-Schema-validation library** is used anywhere in this codebase — confirmed via `pom.xml`
  (no `everit`/`networknt`/`json-schema-validator` dependency). Structural checks are done by hand
  via Jackson `JsonNode` traversal, matching the existing contract test's own documented rationale.
- **DTOs already exist** for essentially every endpoint's request/response shape; the OpenAPI spec
  should reference these, not redesign them.

## 4. Testing conventions

- Contract tests: plain JUnit, no Spring context, no Testcontainers (as above).
- No existing precedent in this codebase for an **OpenAPI-conformance test**
  (`shouldConformToAuthOpenApiContract`, this task's other named test) — nothing currently validates
  a real HTTP response against an OpenAPI schema. This will need a genuinely new testing approach
  (Phase 2/5 decision), not an established pattern to just copy.
- Unit tests elsewhere in this codebase: plain JUnit, fixed `Clock` where time matters.
- Integration tests elsewhere: Testcontainers (Postgres + Kafka), `@SpringBootTest`.

## 5. Known gaps / unknowns

- **`package.md`'s named-test table has stale cross-references, same class of issue found during
  T32.** It maps `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic → R38` and
  `shouldConformToAuthOpenApiContract → R40 / R41`, but `requirements.md`'s actual matching
  requirements are **R44** (email-requested routing) and **R47** (auth.yaml conformance) — R38/R40/R41
  are unrelated (R40/R41 are the T31 rate-limiting requirements). This task's own Phase 0 prompt
  header already correctly cites R44/R45/R47, which is what I'll follow; flagging per the standing
  guardrail rather than silently using either number set.
- **No OpenAPI authoring/validation tooling exists in this project at all** (no springdoc, no
  swagger-codegen, no `openapi-generator-maven-plugin`, no JSON-Schema-for-HTTP validation library).
  Authoring `auth.yaml` by hand for 28 endpoints and then building
  `shouldConformToAuthOpenApiContract` will require Phase 2/5 to either (a) hand-write the
  conformance check via the same plain-Jackson-traversal technique as the event contract tests
  (checking response shape against the YAML's own schema definitions, parsed as YAML/JSON), or (b)
  introduce a new library (e.g. `com.atlassian.oai:swagger-request-validator` or similar) — a real
  design decision, not something to assume. I do not know which approach the spec author intended;
  `target-design.md`/`auth-decisions.md` weren't found to name one specifically (only `design.md`'s
  own file tree lists the contract files' paths, not the testing mechanism).
- **`AuditMirrorPayload.actorUuid`'s nullability** is not yet confirmed against every call site —
  Phase 1 will need to check this before the schema's `required` list can be finalized (a nullable
  field must not be in `required`, matching how `accountUuid`'s confirmed nullability already
  informs this).
- **Scope boundary with T34**: `design.md`'s own file tree lists `contracts/api/token-claims.md`
  immediately alongside `auth.yaml` and both event schemas under one "Contract files (new)" block,
  but `tasks.md` splits them: T33 owns `auth.yaml` + the two event schemas; T34 (Token claims doc)
  owns `token-claims.md` separately. Confirmed this task's own scoped IDs (R44/R45/R47, not R48)
  match the T33-only split — `token-claims.md`/R48 is explicitly out of this task's scope, not an
  oversight.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
