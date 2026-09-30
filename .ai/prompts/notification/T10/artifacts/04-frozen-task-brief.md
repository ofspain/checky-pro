STATUS: FROZEN

# notification · T10 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings independently verified against source before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Static scan only checks `toString()` presence, not that it excludes the sensitive field | High | **ACCEPTED** | Tightened: after locating a sensitive field named `foo` in a file, the scan also extracts that file's own `toString()` method body (source text between its own opening/closing braces) and asserts it does not reference `foo` as a whole-word identifier (nor a matching getter, `getFoo`/`foo()`) — a `toString()` that exists but still prints the field now correctly fails the scan. |
| 2 | `key` as a heuristic term is a false-positive risk (Kafka/cache/map keys) | Medium | **ACCEPTED** | Dropped `key` from the scan's own field-name list — verified via a repo-wide grep that no production field is currently literally named `key` (all matches are Javadoc/comments), so this changes nothing about today's scan results, only future false-positive risk. Remaining terms: `token`, `secret`, `password`, `apiKey`/`api_key`. |
| 3 | `SecretSafeLogging` should be non-instantiable | Low | **ACCEPTED** | Private constructor, `final` class. |
| 4 | `redact()`'s regex doesn't cover JSON/header-shaped secrets | Medium | **ACCEPTED (documented, not expanded)** | `redact()`'s own Javadoc states explicitly: scoped to URL query-string-shaped (`key=value`) input only; JSON (`"token": "..."`) and HTTP-header-shaped (`Authorization: Bearer ...`) secrets are out of this method's own scope, matching Kimi's own "documentation is sufficient" concession — no real JSON/header-logging call site exists anywhere in this codebase today to justify broadening it speculatively. |
| 5 | No test closes the loop between T09's real rendered output and `redact()` | Medium | **ACCEPTED** | Added to Required Tests: render `email.password_reset` (T09's own real `TemplateRenderer`, real seeded row, real Testcontainers Postgres) with a known token, pass the resulting body through `redact()`, assert the raw token is gone and `token=***` is present. |
| 6 | `redact()`'s value terminator (`[^&\s]*`) stops at the first space, documented as a real, disclosed limitation | Low | **ACCEPTED (documented, not broadened)** | Same disclosure as Finding #4 — `redact()`'s own Javadoc states the value terminates at `&`/whitespace, by design (matches URL query-string semantics exactly); a test proves `password=hello world` redacts only up to `hello`, documenting the boundary rather than silently leaving it as an implicit surprise. |
| 7 | Records with a sensitive-named component need explicit test coverage, not just design confidence | Low | **ACCEPTED** | Added to Required Tests: a synthetic record fixture with a sensitive-named component and no explicit `toString()` override, proving the scan's own text-presence check correctly fails it (the auto-generated `toString()` is never written into the `.java` source text at all, so the existing text-based scan design already catches this — confirmed by adding the fixture test, not by changing the scan's own logic). |
| 8 | `T01SkeletonRegressionTest` not explicitly named in "Files to Modify" | Low | **ACCEPTED** | Added explicitly below, superseding the brief's own prior "historical note" phrasing with a direct listing, consistent with how Findings #5/#7 of T08's own Phase 3 and Finding #7 of T09's own Phase 3 were each explicitly listed once raised. |

## Task

Unchanged from Phase 2, with all 8 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- The static scan verifies `toString()`'s own body excludes the sensitive field, not merely that
  a `toString()` exists (Finding #1).
- The field-name heuristic drops `key`, keeping `token`/`secret`/`password`/`apiKey` (Finding #2).
- `SecretSafeLogging` has a private constructor (Finding #3).
- `redact()`'s own Javadoc documents its query-string-only scope and its whitespace/`&` value
  boundary explicitly (Findings #4/#6).

**Out:** Unchanged from Phase 2. Explicitly still out: JSON/header-shaped secret redaction
(Finding #4), broadening the value-terminator class (Finding #6).

## Business Rules

Unchanged from Phase 2.

## Locked Decisions

Unchanged from Phase 2: L4.

## Dependencies

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 2's AC1-5, with AC5 now explicit that the scan verifies `toString()`'s own
body content (Finding #1), using the narrowed 4-term field-name list (Finding #2), plus:
6. **AC6.** `SecretSafeLogging` cannot be instantiated (private constructor).

## Files to Create

Unchanged from Phase 2:
- `services/notification/src/main/java/com/themistra/notification/common/SecretSafeLogging.java`

## Files to Modify

- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (Finding #8) — not pre-authorized with exact content here (same reasoning as prior tasks: exact
  file list unknown until Phase 6), but now explicitly listed rather than left to the brief's own
  prior "historical note" phrasing alone.

## Files NOT to Modify

Unchanged from Phase 2.

## Required Tests

Unchanged from Phase 2 (AC1-AC5's own correctness + the reflection-free static scan), plus:
- AC6: instantiation via reflection (or a direct `new SecretSafeLogging()` if the constructor is
  merely private, not blocked further) throws/fails to compile — a private constructor alone is
  sufficient and idiomatic; no runtime-throwing constructor body is required.
- Finding #1's own tightened scan: a synthetic positive fixture (a sensitive field with a
  `toString()` that still prints it) proving the scan now fails it, alongside the existing
  safe-class fixtures.
- Finding #5: `redact()` against a real `TemplateRenderer`-rendered `email.password_reset` body
  (real Testcontainers Postgres, real seeded row).
- Finding #6: `redact("password=hello world")` redacts only up to the space, documented and
  tested explicitly.
- Finding #7: a record fixture with a sensitive-named component and no explicit `toString()`
  override, proving the scan correctly fails it.

## Constraints

Unchanged from Phase 2, plus: `redact()`'s own Javadoc explicitly documents its query-string-only
scope and whitespace/`&` value boundary (Findings #4/#6) — not merely implied by test coverage.

## Open Questions

No blockers. All 8 Phase 3 findings resolved above.
