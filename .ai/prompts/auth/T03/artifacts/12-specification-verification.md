# auth · T03 — Phase 12: Specification Verification

Verifying the final implementation (`06-implementation-notes.md`, `09-review-resolution.md`) and
tests (`10-test-generation.md`, Phase 11 addendum) against `requirements.md`, `design.md`, and
`tasks.md` for T03 only.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R8 — reject password < 12 or > 128 chars | Yes | `PasswordPolicy.java:46-53` (`validateLength`); bounds sourced from `PasswordPolicyProperties.java:24-25` (`@Min(12) @Max(128)`), config `application.properties:58-59` | `PasswordPolicyTest.shouldRejectPasswordShorterThan12OrLongerThan128`, `.shouldAcceptPasswordAtExactly12And128CharacterBoundaries`; `PasswordPolicyPropertiesTest.shouldRejectMinLengthBelowL2Bound`, `.shouldRejectMaxLengthAboveL2Bound`, `.shouldRejectMaxLengthBelowL2Bound` | No | None |
| R9 — query HIBP with 5-char uppercase SHA-1 prefix; reject if suffix count > 0 | Yes | `BreachCheckClient.java:42-61` (`isBreached`), `:79-87` (`sha1UppercaseHex`), `:89-113` (`responseContainsSuffix`), `:74` (User-Agent header); `PasswordPolicy.java:55-66` (`validateNotBreached`) | `BreachCheckClientTest` (13 tests: prefix/header, positive/zero/absent count, case-insensitivity, blank-line and malformed-line tolerance, trailing-slash normalization); `PasswordPolicyTest.shouldRejectBreachedPasswordUsingHibpRange` | No | None |
| R10 — allow + audit `password.breach_check_failed` on range-API failure | Yes | `PasswordPolicy.java:63-65` (catch `BreachCheckUnavailableException`), `:68-75` (`recordBreachCheckFailedAudit`, `AuditOutcome.FAILURE`, audit-write failure swallowed) | `PasswordPolicyTest.shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`, `.shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen`; `BreachCheckClientTest.shouldThrowBreachCheckUnavailableExceptionOnServerError`, `.shouldThrowBreachCheckUnavailableExceptionOnConnectionFailure`, `.shouldThrowBreachCheckUnavailableExceptionWhenServerExceedsConfiguredTimeout` | No | None |
| L2 — NIST 800-63B: 12/128, no composition rules, no forced rotation, HIBP fail-open+audit | Yes | Same evidence as R8–R10 above; no composition-rule or rotation logic exists anywhere in `PasswordPolicy`/`PasswordPolicyProperties` (satisfied by correct omission) | Covered by the R8–R10 tests above | No | None |
| "Add unit tests" (task 3 statement) | Yes | — | 27 tests total: 7 `PasswordPolicyTest` + 7 `PasswordPolicyPropertiesTest` + 13 `BreachCheckClientTest`, all passing (`10-test-generation.md` + addendum) | No | None |

**Spec-consistency note (not an implementation deviation):** `package.md` §8 labels these three
named tests' requirement IDs as R11/R12/R13; `requirements.md`'s actual R11–R13 describe unrelated
endpoints (change-own-password, reset-request, reset-email). This was identified and deferred at
the Phase 4 human-approval gate (owner: spec author, outside `spec/`-file-editing scope for this
task) and does not affect the correctness of the mapping used above, which is `requirements.md`'s
own R8/R9/R10 text.

## Principal-engineer assessment

**(1) Is the task fully complete?**
Yes. `PasswordPolicyProperties`, `PasswordPolicy`, and `BreachCheckClient` are implemented exactly
per the frozen brief's Files to Create, with the config block added verbatim (plus the
Phase-9-approved `timeout-ms` addition). Task 4's audit-wiring intent ("wire `AuditService.record`
for `password.breach_check_failed`, unit-test the fail-open path") is fully absorbed into T03, as
decided and documented at the Phase 4 gate.

**(2) Does it satisfy every acceptance criterion?**
Yes. Every acceptance criterion in the frozen brief — R8/R9/R10, the config-derived
enabled/disabled behavior, the response-parsing rules, and null/blank handling — has a
name-matched, currently-passing test. The two HIGH-severity gaps carried through Phases 7–11
(no tests existed; HIBP URI construction was unverified) are both closed: 27/27 tests pass, and
the URI construction is now proven against a real local HTTP server rather than assumed correct.

**(3) Does it violate any LOCKED decision?**
No. L2 is implemented exactly as written: 12/128 bounds (now enforced at the config-validation
level, not just the domain-logic level, per the Phase 8/9 hardening), HIBP k-anonymity with a
5-character uppercase SHA-1 prefix, and fail-open-with-audit on range-API failure. No composition
rules or forced rotation were added, correctly honoring L2's explicit prohibition of both.

**(4) Remaining risks?**
- The pre-existing, unrelated `token` package compile failure (`services/auth/token/
  SecurityChainsConfig.java`, `ReuseDetectingAuthorizationService.java` — dated 2026-07-13, not
  touched by T03) still blocks a real `mvn -pl services/auth test` run. Tests here were verified by
  direct `javac` + JUnit Platform `Launcher` execution against the actual resolved classpath and
  matching platform version — the same engine Surefire delegates to — but a Surefire-mediated
  confirmation is still pending on that unrelated fix.
- `account.PasswordPolicy` and `authn.BreachCheckClient` form a two-way package dependency
  (flagged Self-review Finding 3 / Kimi Finding 4, accepted-with-no-code-change at Phase 9). Not a
  defect, but a standing architectural note for future cleanup.
- `account.dto.RegisterAccountRequest`'s hardcoded `@Size(12,128)` is untouched and will need
  reconciling with the now-config-driven `PasswordPolicy` when task 9 wires this into endpoints.
- SHA-1 is used in `BreachCheckClient` — this is correct and required (HIBP's k-anonymity protocol
  is defined around SHA-1; it is an external protocol requirement, not a security choice made
  here), noted so a future reviewer doesn't re-flag it without this context.
- `package.md` §8's stale R11/R12/R13 labels for these named tests remain unreconciled in the spec
  itself (owner: spec author, per the Phase 4 disposition).

## Verdict

**PASS** — every R8/R9/R10 acceptance criterion and LOCKED decision L2 is implemented exactly as
specified, backed by 27 passing, deterministic tests (including empirical proof of the previously
unverified HIBP URI construction); remaining items are either pre-existing infrastructure issues
outside T03's scope or forward-looking notes for later tasks, none of which block this task.
