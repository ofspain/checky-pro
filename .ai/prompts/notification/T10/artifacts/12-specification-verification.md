# notification · T10 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `redact` masks `token=`/`secret=`/`password=`/`apikey=`/`api_key=`-shaped substrings (case-insensitive key, casing preserved) | Yes | `SecretSafeLogging.SECRET_PARAM_PATTERN` | `redactMasksASingleTokenParameter`, `...SecretParameter`, `...PasswordParameter`, `...ApiKeyAndApiUnderscoreKeyParameters`, `redactIsCaseInsensitiveOnTheKeyButPreservesItsOwnOriginalCasing`, `redactMasksAnEmptyValueToo` | No | No |
| **AC2** — multiple distinct matches in one input all masked | Yes | `matcher.replaceAll(...)` applies to every match | `redactMasksMultipleDistinctSecretsInOneInput` | No | No |
| **AC3** — non-matching text returned unchanged, byte-for-byte | Yes | No-match path returns `matcher.replaceAll`'s own no-op result | `redactLeavesNonMatchingTextCompletelyUnchanged` (special chars/unicode) | No | No |
| **AC4** — `redact(null)` returns `null` | Yes | `redact`'s own early `if (text == null) return null;` | `redactReturnsNullForNullInput` | No | No |
| **AC5** — a permanent static scan asserts every sensitive field is excluded from its own type's `toString()` | Yes | `SensitiveFieldsHaveSafeToStringTest`'s own scanning logic | 10 tests: 1 real-codebase pass + 9 synthetic-fixture proofs of the scan's own correctness (leak detection, record handling, local-variable exclusion, multi-type scoping, comment-stripping) | No | No |
| **AC6** — `SecretSafeLogging` cannot be instantiated | Yes | `private SecretSafeLogging()`, `final class` | `cannotBeInstantiated` | No | No |
| L4 (no secrets/tokens in messages or logs) | Yes | Per-class discipline already enforced since T06/T09; `redact()` is new, reusable infrastructure; the static scan is a permanent, automated lock on the per-class discipline going forward | Covered by all of the above, plus `SecretSafeLoggingIntegrationTest`'s own proof against T09's real rendered output | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T10's own literal scope (`tasks.md` task 10:
`SecretSafeLogging` redaction, assert no token/secret/full-key leaks into bodies or logs). 1
production file delivered (`SecretSafeLogging`) plus, across Phases 10-11, 3 test files (199 tests
total, up from T09's own 174 — 25 new). The `T01SkeletonRegressionTest.java` update was disclosed
and explicitly listed in the frozen brief (Finding #8), not discovered mid-implementation like the
five prior tasks' own equivalent gap.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 all hold, each with
direct evidence and automated coverage. Notably, two of Phase 11's own "gaps" (#2/#3) turned out to
be genuine, empirically-confirmed bugs in the scan's own first-draft logic — a local variable in
`TemplateRenderer.java` misidentified as a sensitive field, and that same file's own two type
declarations meaning the wrong type's `toString()` could be checked — both found via direct
inspection of the real codebase, not speculation, and both fixed with real logic changes, not mere
documentation. The re-run mutation test (adding a real `token=` reference to
`EmailRequestedEvent.toString()`, reverted after confirming the rewritten scan still caught it)
gives strong confidence the fix is genuine, not merely test-shaped.

**(3) Does it violate any LOCKED decision?** No. L4 holds — `SecretSafeLogging` introduces no new
risk; it is purely additive, reusable infrastructure plus a permanent regression guard on a
property this codebase has upheld per-class since T06.

**(4) Remaining risks?**
- **`SecretSafeLogging.redact` has zero real callers today** — no code in this module currently
  logs free-form rendered content; the only anticipated future caller, `DeliveryOrchestrator`
  (task 11), doesn't exist yet. Same "seam built ahead of its caller" shape as
  `NotificationDispatcher` (T06), `PreferenceResolver` (T08), `TemplateRenderer` (T09).
- **`redact()` is explicitly scoped to URL query-string-shaped input** — JSON (`"token": "..."`)
  and HTTP-header-shaped (`Authorization: Bearer ...`) secrets are out of its own documented scope.
  Disclosed at Phase 3/4, not a gap this task closes.
- **The static scan's field-name heuristic has three disclosed, unfixed limitations**: it cannot
  catch a semantically-sensitive-but-ambiguously-named field (`RenderedMessage.body`, T09's own
  equivalent gap, caught only by Kimi's adversarial review, not any automated scan); it only
  matches the first name in a multi-field declaration (`String token, secret;`, not used anywhere
  today); it only recognizes `String`-typed secrets, not `byte[]`/`char[]` (also not used anywhere
  today).
- **The scan's own "nearest enclosing type" scope-tracking is a lightweight text-based
  approximation, not a real Java parser** — correct for every shape actually used in this codebase
  today (verified: no file has sibling top-level types or deep nesting beyond one outer type plus
  at most one nested record), but would need real revisiting if this codebase's own style ever
  introduced multiple sibling top-level types in one file.

## `package.md` §9 whole-service checklist — items relevant to T10

- [x] No secret, token, password-reset value, or full API key ever appears in a rendered message
  body or a log line — **now genuinely "assertion-tested"** (the checklist's own parenthetical),
  not merely per-class-disciplined: the static scan is a permanent, automated proof for every
  sensitive field this heuristic can identify, and `SecretSafeLoggingIntegrationTest` proves
  `redact()` itself works against T09's real rendered output. The checklist's own literal phrasing
  ("never appears in a rendered message body") is still read through L4's own carve-out (Phase 1's
  own resolved ambiguity) — a rendered body legitimately contains the token as part of its one
  intended link; nothing should ever *log* that body verbatim.
- [ ] Every other checklist item — **out of scope**, unrelated to this task's own files.

## Cross-task regression check

Full `services/notification` suite: 199 tests, 0 failures. `T01SkeletonRegressionTest` (9 tests,
its own 27-file authorized list, updated this task) passes alongside T02's through T09's own test
classes, all unmodified by this task except the one disclosed, pre-authorized edit — confirms T10's
changes didn't regress anything the prior six implementation tasks established. (No
`NotificationBaselineMigrationIntegrationTest` change this task — `SecretSafeLogging` introduces no
new table, no new grant.)

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20,
matching the established precedent (T02-T09 all left it untouched). Not touched here.

---

**PASS** — all 6 acceptance criteria satisfied with direct evidence and automated coverage (199
tests, two of Phase 11's own findings confirmed as real, live bugs and fixed with genuine logic
changes rather than documentation, re-verified via a real-file mutation test), no LOCKED decision
violated, task boundary held throughout. Four residual risks are disclosed above, none blocking:
no real caller yet (task 11's own scope), the query-string-only redaction scope, three disclosed
field-heuristic limitations, and the scan's own lightweight (non-parser) scope-tracking.
