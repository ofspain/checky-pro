<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T26 · Phase 9 — Review Resolution

**Human Approval gate. Decided by femi, 2026-08-15.** Consumes the self-review (`artifacts/07-self-review.md`) and independent review (`artifacts/08-independent-review.md`). Kimi (Phase 8) independently confirmed my own Phase 7 finding and surfaced three new, minor ones.

---

## Resolution Log

### 1. Malformed `keyUuid` path segment → 500, not 400
*(Phase 7 Finding 1 = Phase 8 Finding 1, Medium/High)*

**ACCEPTED AS A DOCUMENTED, OUT-OF-SCOPE LIMITATION. No code change.** Both reviews independently confirmed this is real (`MethodArgumentTypeMismatchException` has no handler anywhere in the service) and pre-existing (every other `@PathVariable UUID` endpoint — `AdminAccountController`, `AdminAccountRoleController` — has the identical exposure). femi's decision (offered the option of widening scope to add a `MethodArgumentTypeMismatchException` handler in `common/ApiExceptionHandler.java`, benefiting ~10 endpoints at once): **do not widen T26's scope for this.** A fix belongs in a dedicated cross-cutting follow-up task, not smuggled into a "CRUD controller" task's diff by touching a shared framework-level file this task was never authorized to modify. The frozen brief's Constraints section statement that a malformed UUID "400s before the handler runs" is now known to be inaccurate for this codebase — noted here for the record rather than silently left wrong, though the frozen brief itself is not amended (frozen briefs aren't revised post-hoc; this resolution log is the correction of record).

### 2. `ApiKeyExceptionHandler`'s class Javadoc is stale (says "T25" only)
*(Phase 8 Finding 2, new, High confidence, cosmetic)*

**ACCEPTED.** `apikey/ApiKeyExceptionHandler.java` — class Javadoc updated from "(T25)" to "(T25, T26)". No behavioral change.

### 3. No test yet locks the `CreateApiKeyRequest`/`ApiKeyService.requireValidName` boundary in sync
*(Phase 8 Finding 3, new, Medium confidence — future-regression guard, not a present bug)*

**ACCEPTED, carried to Phase 10 as a required test — no code change now.** The bounds already agree exactly (`@Size(max = 100)` vs. `length() > 100`); this was already Required Test #5 in the frozen brief ("Blank name and a 101-character name on `POST /api-keys` → 400 each"). No shared-constant refactor attempted — `ApiKeyService.MAX_NAME_LENGTH` is a private constant in a file T26 is not authorized to modify, and extracting/exposing it would be exactly the kind of unrequested refactor this pipeline's guardrails forbid for a task this narrowly scoped.

### 4. No test yet asserts `CreateApiKeyResult`'s exact JSON shape / absence of a hash-shaped value
*(Phase 8 Finding 4, new, Medium confidence)*

**ACCEPTED, carried to Phase 10 as a required test — no code change now.** Already anticipated by the frozen brief's Required Test #6 ("`POST /api-keys` response contains no 64-hex-character string..."). Structural safety (no `keyHash` field exists on the record at all) already holds; Phase 10 will add the explicit serialization-level assertion Kimi asks for.

---

## Build Verification (post-resolution)

`mvn -q -pl services/auth -am compile` — clean, exit 0. Only file touched this phase: `apikey/ApiKeyExceptionHandler.java` (Javadoc only, no logic change) — no regression test re-run needed for a comment-only edit, but the existing `ApiKeyExceptionHandlerTest` was inspected and confirmed to assert behavior, not Javadoc content, so it remains valid and green regardless.

---

## Files Touched This Phase

- `apikey/ApiKeyExceptionHandler.java` — class Javadoc, +1/-1 lines.

No other file changed. No file under `spec/` touched. No public API, class name, or method signature changed.

---

**Phase 9 complete — human sign-off given, accepted comment applied.** Proceed to Phase 10 (Test Generation).
