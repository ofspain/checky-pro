<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T40 · Phase 1 — Specification Extraction

## Business Rules

- **R43** (indirectly implicated, not directly scoped) — "every security-relevant action... SHALL
  append an `auth_audit` row and mirror... via the outbox," explicitly naming "lock, unlock" among
  the covered actions. Phase 0 found the automatic lock/unlock path (`AccountService.lock`/`.unlock`)
  does not currently satisfy this — directly relevant to whether Q5 can honestly be called "closed."

## Locked Decisions

None scoped. No LOCKED decision directly governs a status/version bump, though L11-L13 (already
verified absent-of-violation across T36-T38) bear on whether the spec is genuinely "ready."

## Files involved

**To modify (the one sanctioned exception to "never touch spec/ files"):**
- `spec/auth-service/package.md` — header (`Status`, `Version`) and, pending Phase 4's scope
  decision, potentially §11 (marking Q2 resolved, at minimum).

**To read/verify (no changes):**
- `spec/auth-service/package.md` §11 — Q1-Q6's current text.
- `services/auth/src/main/resources/application.properties` — Q2's concrete values.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyProperties.java`,
  `ApiKeyService.java` — Q3's current state.
- `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java` —
  Q4's scope-boundary question.
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (`lock`/`unlock`) —
  Q5's gap.
- Full-suite test results (already gathered T37/T38, re-confirmed Phase 0).

## Dependencies

None new — this task depends entirely on the current, already-established state of the codebase and
test suite, not on any new mechanism.

## Acceptance Criteria

Derived from the task statement's own two literal preconditions:

| AC | Statement | Status at Phase 0 |
|---|---|---|
| AC1 | `package.md` §11's questions are closed | **Not met** — Q2 resolved-but-unmarked; Q3 ambiguous; Q4 likely out of this service's scope; Q5 genuinely open (and reveals an R43 gap) |
| AC2 | `mvn -pl services/auth verify` passes | **Not met** — Groups A/B remain, both already logged/deferred at prior human gates |
| AC3 | If AC1/AC2 are satisfied (or explicitly, knowingly waived), `package.md`'s Status becomes `READY FOR IMPL` and Version becomes `0.2` | Not yet performed — gated on AC1/AC2's resolution or an explicit human decision to proceed despite them |

## Tests required

None — this task's deliverable has no test surface. Its *preconditions* are test-outcome-dependent
(AC2), already established, not newly authored here.

## Open Questions

**Blocker-class, for Phase 4.** Both AC1 and AC2 are currently false. Three genuinely distinct
decisions are needed, not one:
1. **Q2**: cheap, unambiguous fix — mark resolved in §11, citing `auth-decisions.md` D-026 (already
   done, T39). No real judgment call.
2. **Q3/Q4/Q5 and the "tests pass" precondition**: each is a genuine, separate judgment call about
   whether to (a) treat as blocking — defer T40 entirely until each is actually resolved/fixed,
   (b) treat as an accepted, explicitly-logged gap — bump the spec status anyway with each named as a
   known exception, matching this session's established pattern for Groups A/B, or (c) some mix
   (e.g., fix the cheap ones, defer the rest).
3. **The R43 audit gap found while investigating Q5** is a genuine, real defect independent of the
   status-bump question — whether *fixing it* is in T40's own scope (a "bump spec status" task) or
   belongs to a separate follow-up task is itself a distinct scope question.

None of these are decidable from the spec alone — all carried to Phase 4.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
