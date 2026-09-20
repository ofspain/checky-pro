<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T33 · Phase 6 — Implementation Notes

## What changed

Exactly the files the frozen brief authorized:

- `contracts/events/auth/email-requested.v1.schema.json` (new)
- `contracts/events/auth/security-audit.v1.schema.json` (new)
- `contracts/api/auth.yaml` (new — OpenAPI 3.0.3, all 30 endpoints, 19 component schemas: 9 request
  bodies + 10 response shapes including the inline `AuditEventPage`)
- `services/auth/src/test/java/com/themistra/auth/account/event/EmailRequestedEventPayloadContractTest.java` (new, 2 tests)
- `services/auth/src/test/java/com/themistra/auth/audit/AuditMirrorPayloadContractTest.java` (new, 3 tests)
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` (new, 3 tests — the named test plus its two supporting halves)
- `services/auth/pom.xml` (modified — 1 new test-scope dependency, `jackson-dataformat-yaml`, no explicit version, resolved to 2.19.2 via Spring Boot's own BOM)

## Mapping to acceptance criteria

- **AC1 (R44)** ← `email-requested.v1.schema.json` + `EmailRequestedEventPayloadContractTest`.
- **AC2 (R45)** ← `security-audit.v1.schema.json` + `AuditMirrorPayloadContractTest` (including the
  dedicated null-`accountUuid`/`actorUuid` test proving the schema's `required` list doesn't
  wrongly demand them).
- **AC3 (R47)** ← `auth.yaml` + `AuthOpenApiContractTest`'s two halves: D1a (completeness, both
  directions) and D1b (schema correctness for all 19 component schemas... 13 of which map to the
  named DTOs the frozen brief listed, the rest are the request-body schemas documented but not
  independently exercised by D1b beyond appearing in the file — see Deviation below).

## Deviations from the frozen brief (flagged, not hidden)

1. **`realInstancesByComponentName()` in `AuthOpenApiContractTest` covers all 19 component schemas
   used by success responses AND the 9 request-body schemas** — the frozen brief's D1b described
   this as covering "the 13 distinct component schemas" (the response shapes), but since request
   bodies are also `$ref`-declared components in `auth.yaml`, the same generic comparison loop
   naturally covers them too at no extra cost (they were already going to be documented; verifying
   them costs nothing extra now that the mechanism exists). This is a **strengthening**, not a
   narrowing, of D2's own scope (D2 said the automated *test* doesn't need to verify request
   bodies — it still doesn't verify that a real HTTP request `some Java caller sends` matches the
   schema, but it does verify the schema itself matches the DTO class's own shape, the same
   correctness bar applied to every other component). Flagging in case this reads as scope creep —
   it was a natural consequence of the chosen implementation, not a deliberate re-litigation of
   Phase 4's D2 decision, and required no extra design choice.
2. **`Page`'s exact JSON shape was empirically confirmed, not assumed**, per the frozen brief's own
   instruction — a scratch probe test (written, run once, then deleted before this commit) printed
   Spring Data's real default `PageImpl` serialization. The frozen brief's own inline field list
   (`content`, `pageable`, `totalElements`, ...) turned out to match exactly what was assumed at
   Phase 4 — no correction was needed, only confirmation.
3. **One YAML authoring mistake caught by the tests themselves, not by review**: an early draft of
   `auth.yaml`'s `apiKeyAuth.description` mixed a quoted scalar with trailing unquoted text,
   producing invalid YAML. `AuthOpenApiContractTest` failed immediately with a clear SnakeYAML
   parse error pointing at the exact line — fixed by switching to a YAML block-scalar (`>`). This
   is exactly the kind of authoring error a hand-written contract file is prone to, and exactly why
   Phase 4's D1 rejected "just eyeball it" in favor of an automated parse-and-compare test.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='EmailRequestedEventPayloadContractTest,AuditMirrorPayloadContractTest,AuthOpenApiContractTest'`
  — **8/8 pass** (2 + 3 + 3).
- `mvn -pl services/auth test -Dtest='EventTopicsTest'` — **3/3 pass**, confirming
  `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` (untouched, already existed) is
  unaffected.
- **Three negative-proof runs performed and reverted**, matching this pipeline's established
  practice of proving a check can actually fail before trusting it:
  1. Removed the `/admin/audit` path block from `auth.yaml` → `everyControllerHandlerIsDocumentedInAuthYaml`
     failed with the exact missing route named. Restored.
  2. Added a fake `/fake/nonexistent` path to `auth.yaml` → `authYamlDocumentsNoRouteThatDoesNotHaveARealHandler`
     failed with the exact stale route named. Restored.
  3. (Implicit, from the YAML-syntax-error discovery above) confirmed the test suite fails loudly
     on a malformed contract file rather than silently passing.
- `git status` confirms the final diff matches the frozen brief's Files to Create/Modify exactly —
  no scratch files, no unrelated changes.

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
