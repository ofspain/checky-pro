<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T33 · Phase 3 — Design Challenge

Consumed `artifacts/02-task-implementation-brief.md`, `agents.md`, the existing contract test
(`UserLifecycleEventPayloadContractTest`), the existing schema
(`user-lifecycle.v1.schema.json`), `EventTopics.java`, and all auth-service controllers. No
conflicts with LOCKED decisions (none scoped to this task). Findings only — no implementation.

---

## Finding 1 · D2's component-schema-only test does not prove `auth.yaml` documents all 28 endpoints

**Severity:** High

**Evidence:** The brief states `shouldConformToAuthOpenApiContract` will "validate by component
schema, not by live HTTP call: for each distinct response DTO ... serialize a real instance and
compare it against `auth.yaml`'s `components.schemas.<Name>`" (D2). This verifies that the schemas
that *are* present match reality, but it does not verify that every one of the 28 non-SAS
endpoints is actually documented in `auth.yaml` with the correct path, method, parameters, and
response status.

A missing endpoint (e.g., `DELETE /admin/accounts/{accountUuid}`) would not fail the component-schema
test as long as `AccountResponse` is still correctly described. That defeats R47's intent that
"`auth.yaml`, once authored, is the contract service responses must conform to."

**Recommended brief amendment:** Add a second assertion (or broaden the named test) that scans all
`@RestController` classes in `com.themistra.auth` and asserts each handler maps to a path/method
operation in `auth.yaml`, and vice versa. This can be reflection-based and test-scope-only, still
avoiding Testcontainers. At minimum, list the expected 28 endpoint paths explicitly in the brief
so completeness can be verified by inspection.

---

## Finding 2 · The brief's enumerated response-DTO list is incomplete

**Severity:** High

**Evidence:** D2 names `AccountResponse`, `ApiKeyMetadata`, `CreateApiKeyResult`,
`AuditEventResponse`. The actual response/request shapes include many more distinct types:
`RegistrationAcknowledgement`, `ApiKeyTokenResponse`, `RoleResponse`, `RoleTemplateResponse`,
`SessionResponse`, `CreateRoleRequest`, `CreateRoleTemplateRequest`, `CreateApiKeyRequest`,
`RegisterAccountRequest`, `VerifyEmailRequest`, `ResendVerificationRequest`,
`PasswordResetRequest`, `PasswordResetConfirmRequest`, `ChangePasswordRequest`, generic wrappers
(`Page<AuditEventResponse>`, `List<SessionResponse>`, `List<ApiKeyMetadata>`, `Set<String>`), and
plain `Void`/`204` responses.

If the OpenAPI spec is authored from the brief's partial list, it will be incomplete, and the
component-schema test will not catch the omission (per Finding 1).

**Recommended brief amendment:** Require Phase 5 to produce an exhaustive inventory of every
request body, response body, path-variable, and query-parameter type used by the 28 endpoints
before authoring `auth.yaml`. The test must verify every distinct schema component in that
inventory, not just the four DTOs named in D2.

---

## Finding 3 · No plan to verify request bodies, path parameters, status codes, or error responses

**Severity:** Medium

**Evidence:** The brief frames the named test around response-DTO component schemas. It does not
mention verifying that request bodies match `RegisterAccountRequest` etc., that path variables are
typed as UUID, that `POST` returns `201`, `DELETE` returns `204`, or that error responses conform
to the service's `application/problem+json` shape. A contract that only covers successful response
bodies is a partial contract.

**Recommended brief amendment:** Explicitly scope the named test to successful response schemas and
status codes only, and document request-body, parameter, and error-response coverage as a follow-up
(or out of scope). Alternatively, extend the test to also compare each operation's request/response
metadata against the controller annotations.

---

## Finding 4 · Generic response wrappers (`Page`, `List`, `Set`) are not addressed

**Severity:** Medium

**Evidence:** `AdminAuditController.list` returns `Page<AuditEventResponse>`;
`AccountController.listSessions` returns `List<SessionResponse>`;
`AdminAccountRoleController.effectiveRoles` returns `Set<String>`;
`ApiKeyController.list` returns `List<ApiKeyService.ApiKeyMetadata>`. The component-schema test
must either model these wrappers in `auth.yaml` or unwrap them and compare the inner element type.
Spring's `Page` has its own fixed structure (`content`, `pageable`, `totalElements`, etc.) that
should be represented consistently.

**Recommended brief amendment:** Decide in Phase 4 whether `auth.yaml` will model `Page<T>` as an
explicit reusable schema component or inline the wrapper shape per operation, and ensure the
test's comparison logic handles both wrapped and unwrapped collections.

---

## Finding 5 · Hand-authoring schemas from existing Java models reverses `agents.md`'s stated flow

**Severity:** Low

**Evidence:** `agents.md` says "models generated from `contracts/`" and "schemas in
`contracts/events/`, versioned, backward-compatible only; models generated from `contracts/`."
This task instead writes contracts to match existing hand-written Java models because the models
already exist. That is a pragmatic documentation-gap closure, but it creates a risk: if the Java
models drift after this task, the contracts become stale unless the tests catch the drift.

**Recommended brief amendment:** Add an explicit note that T33 is a one-time backward-documentation
step, and that future DTO changes must update `auth.yaml`/event schemas first (or simultaneously)
to preserve the "contracts are the source of truth" direction in `agents.md`.

---

## Finding 6 · The count of "28 non-SAS endpoints" is ambiguous against the current code

**Severity:** Low

**Evidence:** The brief refers to "Phase 0's table" for the 28 endpoints. Counting the current
`@RestController` handlers in non-SAS paths yields 30:

- `AccountController`: 10
- `AdminAccountController`: 6
- `AdminAccountRoleController`: 5
- `AdminRoleController`: 2
- `AdminRoleTemplateController`: 2
- `ApiKeyController`: 4
- `AdminAuditController`: 1

This may mean two endpoints are excluded in Phase 0, or the current code has grown by two since
Phase 0. Either way, the brief should not assume the reader has access to the Phase 0 table.

**Recommended brief amendment:** Include the exact list of 28 endpoint paths and methods in the
brief, or reference a file in the repo that contains it. This is needed for both authoring and
review.

---

## Non-Issues Confirmed

- **Test dependency:** `jackson-dataformat-yaml` in test scope is a reasonable, contained choice
  for parsing OpenAPI YAML without adding a heavy validation library.
- **Event schema tests:** Mirroring `UserLifecycleEventPayloadContractTest` for
  `EmailRequestedEventPayload` and `AuditMirrorPayload` is consistent with the codebase's existing
  contract-test style.
- **Nullable audit fields:** Modeling `accountUuid`/`actorUuid` as not required in
  `security-audit.v1.schema.json` matches the genuine nullability of `AuditMirrorPayload`.
- **Token field in email-requested:** Documenting the raw `token` field as the already-locked
  exception is correct and necessary.
- **File placement:** `contracts/api/auth.yaml` aligns with `contracts/README.md`;
  `AuthOpenApiContractTest` in `common` is appropriate for a cross-module contract test.
- **No runtime behavior change:** the task is build/test-time only, consistent with the brief's
  scope.

---

## Open Questions

1. Is the named test intentionally scoped to successful response schemas only, or should it also
   verify request bodies, path/query parameters, status codes, and error response shapes?
2. Which two endpoints are excluded from the "28 non-SAS endpoints" count, and where is the
   authoritative Phase 0 table stored?
3. Does the project intend to move toward "models generated from contracts" after this task, or
   will contracts continue to be authored to match Java models?

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (human approval / brief fold) on approval.
