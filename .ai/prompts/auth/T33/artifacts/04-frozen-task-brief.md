<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T33 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
6 findings). All verified against actual source before disposition — in particular, Finding 6's
endpoint count was independently recounted (`grep -c` across all 7 controllers) and confirmed
correct: **30 endpoints, 7 controllers**, not the Phase 0/2 brief's erroneous "28 across 6." femi
decided the three findings with genuine trade-off weight via human gate; the remaining three are
mechanical amendments folded in directly.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | Component-schema comparison alone never proves every endpoint is documented at all | High | **Resolved, femi's gate decision.** Named test gains an explicit completeness check (D1 below). |
| 2 | The brief's DTO list was illustrative, not exhaustive | High | **Accepted, folded in as a Phase 5 requirement** — Phase 5 must produce a real exhaustive inventory of every request/response/parameter type across all 30 endpoints before authoring `auth.yaml`, not treat D2's original short list as complete. |
| 3 | No stated plan for request bodies, path/query parameters, status codes, or error responses | Medium | **Resolved, femi's gate decision.** Test scoped to success-response schemas + status codes only, matching R47's own literal text ("service responses ... SHALL conform") — not requests. `auth.yaml` the *file* still documents request bodies/parameters/errors for human and future-codegen value; just not verified by this automated test (D2 below). |
| 4 | Generic wrappers (`Page`, `List`, `Set`) unaddressed | Medium | **Resolved, femi's gate decision.** Modeled inline per operation, not as a reusable generic component (D3 below) — only one endpoint uses `Page`, not enough repetition to justify a shared schema. |
| 5 | Hand-authoring from existing models runs opposite to `agents.md`'s stated "models generated from contracts" direction | Low | **Accepted, folded in.** Documented explicitly below as a one-time backward-documentation step. |
| 6 | Endpoint count ambiguous ("28" vs. recounted 30) | Low (but confirmed as a real error, not just ambiguity) | **Resolved.** Corrected to 30 endpoints / 7 controllers throughout; exact list included below so Phase 5 doesn't need to re-derive it. |

## Task

Author `contracts/api/auth.yaml` (all 30 non-SAS endpoints across 7 controllers),
`contracts/events/auth/email-requested.v1.schema.json`, and
`contracts/events/auth/security-audit.v1.schema.json`; add contract tests for all three.

## Purpose

Unchanged from Phase 2: make the already-implemented API/event contracts the documented,
CI-checked source of truth (R47), and close the two remaining undocumented event schemas.

## Scope

**In:** the 3 contract files; 2 event-payload contract tests; 1 OpenAPI-conformance test scoped to
success-response schemas + status codes + endpoint-completeness (D1/D2 below).

**Out:** request-body/parameter schema verification, error-response (`4xx`/`problem+json`) schema
verification by the automated test (documented in the YAML file itself, just not tested); any
change to `EventTopics`, any controller/DTO; `contracts/api/token-claims.md` (task 34); any new
Kafka topic or routing logic.

## Business Rules

- **R44.** Routing already correct/tested; this task documents + schema-verifies the payload.
- **R45.** Same situation as R44.
- **R47.** `auth.yaml`, once authored, is the contract service *responses* must conform to
  (literal wording — not requests).

## Locked Decisions

None scoped to this task.

## This Task's Own Design Decisions (D1-D3, decided at this gate)

- **D1 (Finding 1).** `shouldConformToAuthOpenApiContract` has two halves: (a) **completeness** —
  reflection-scan every `@RestController` handler in `com.themistra.auth` (excluding SAS-chain
  concerns, which have no controllers of their own — SAS's endpoints are framework-provided) and
  assert each maps to a `path`+`method` entry in `auth.yaml`, and that every `auth.yaml` operation
  maps back to a real handler (no stale/aspirational entries either direction); (b) **schema
  correctness** — for each distinct response DTO used by those endpoints, serialize a real instance
  and compare against the corresponding `components.schemas.<Name>` the same way
  `UserLifecycleEventPayloadContractTest` compares against JSON Schema.
- **D2 (Finding 3).** Scope is success-response schemas + HTTP status codes only. Request bodies,
  path/query parameters, and error (`4xx`) response shapes are documented in `auth.yaml` for human
  and future-codegen reference but are **not** verified by this automated test — an explicitly
  accepted, documented narrowing matching R47's own literal "responses" wording, not a silent gap.
- **D3 (Finding 4).** `Page<AuditEventResponse>` (the one paginated endpoint,
  `AdminAuditController.list`), `List<SessionResponse>`, `Set<String>`, and
  `List<ApiKeyService.ApiKeyMetadata>` are modeled **inline per operation** — `type: array` +
  `items: $ref` for the List/Set cases; a small inline object (not a reusable component) for
  `Page`'s own wrapper shape (`content`, `totalElements`, `totalPages`, `number`, `size` — Spring
  Data's actual default JSON shape, to be confirmed against a real serialized `Page` instance at
  Phase 5/6, not assumed). No reusable generic-style schema component, since only one endpoint uses
  `Page` at all.

## The 30 endpoints (Finding 6's correction — authoritative list for Phase 5)

| Controller | Count | Endpoints |
|---|---|---|
| `AccountController` (`/accounts`) | 10 | `POST /`, `GET /me`, `POST /verify-email`, `POST /resend-verification`, `POST /password-reset-request`, `POST /password-reset`, `POST /me/password`, `GET /me/sessions`, `DELETE /me/sessions/{familyId}`, `DELETE /me/sessions` |
| `AdminAccountController` (`/admin/accounts`) | 6 | `GET /{accountUuid}`, `POST /{accountUuid}/activate`, `POST /{accountUuid}/suspend`, `POST /{accountUuid}/reinstate`, `DELETE /{accountUuid}`, `POST /{accountUuid}/unlock` |
| `ApiKeyController` (`/api-keys`) | 4 | `POST /`, `GET /`, `DELETE /{keyUuid}`, `POST /token` |
| `AdminAccountRoleController` (`/admin/accounts/{accountUuid}`) | 5 | `GET /roles`, `POST /roles/{roleName}`, `DELETE /roles/{roleName}`, `POST /role-templates/{templateName}`, `DELETE /role-templates/{templateName}` |
| `AdminRoleController` (`/admin/roles`) | 2 | `POST /`, `GET /` |
| `AdminRoleTemplateController` (`/admin/role-templates`) | 2 | `POST /`, `GET /` |
| `AdminAuditController` (`/admin/audit`) | 1 | `GET /` |
| **Total** | **30** | |

## Dependencies

`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (new, test scope only, Phase 2 D1 —
uncontested by Kimi, confirmed as a Non-Issue). Reflection APIs for D1's completeness scan (plain
`java.lang.reflect`/Spring's own `RequestMappingHandlerMapping` introspection — exact mechanism a
Phase 5 call). No new production dependency.

## Files to Create

- `contracts/api/auth.yaml`
- `contracts/events/auth/email-requested.v1.schema.json`
- `contracts/events/auth/security-audit.v1.schema.json`
- `services/auth/src/test/java/com/themistra/auth/account/event/EmailRequestedEventPayloadContractTest.java`
- `services/auth/src/test/java/com/themistra/auth/audit/AuditMirrorPayloadContractTest.java`
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java`

## Files to Modify

- `services/auth/pom.xml` (new test-scope dependency)

## Files NOT to Modify

- Any controller, DTO, `EventTopics`, `EventTopicsTest`.
- `contracts/api/token-claims.md` (task 34).
- Any `spec/` file.

## Acceptance Criteria

- **AC1 (R44).** `email-requested.v1.schema.json` matches `EmailRequestedEventPayload`'s real
  shape; contract test proves it plus both documented `purpose` values.
- **AC2 (R45).** `security-audit.v1.schema.json` matches `AuditMirrorPayload`'s real shape,
  correctly modeling `accountUuid`/`actorUuid` as not-required; contract test proves it plus
  `AuditOutcome` enum coverage.
- **AC3 (R47).** `auth.yaml` documents all 30 endpoints (table above) with response schemas
  traceable to real DTOs (generic wrappers per D3); `shouldConformToAuthOpenApiContract` proves
  both endpoint completeness (D1a) and response-schema correctness (D1b) for every one of them.

## Required Tests

- `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` — already exists.
- `shouldConformToAuthOpenApiContract` (named) — new, per D1/D2.
- Email-requested and security-audit contract tests — new, per the existing pattern.

## Constraints

- **Performance:** none (build/test-time only).
- **Security:** audit schema must not mark genuinely-nullable fields required; email-requested's
  `token` field is a documented, already-locked-elsewhere exception, not re-litigated.
- **Thread-safety / Transaction:** N/A.
- **Module boundaries (L12):** `AuthOpenApiContractTest` in `common` (cross-module by nature); the
  two event-payload tests stay within their owning module.
- **Null handling:** audit schema's `required` list must exactly match genuine non-nullability
  (verified Phase 1, not assumed).

## Open Questions

No blockers. All 6 Phase 3 findings resolved above; Kimi's own 3 Open Questions are answered by
D1-D3 and the corrected endpoint table.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
