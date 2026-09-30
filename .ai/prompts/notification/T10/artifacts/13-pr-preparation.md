# notification · T10 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T10: secret-safe logging (SecretSafeLogging)`

## Commit message

```
notification-service T10: secret-safe logging

Add SecretSafeLogging, a static redaction utility formalizing the
token/secret-safety property (L4/R15) this codebase has enforced
per-class since T06 (EmailRequestedEvent.toString()) and T09
(RenderedMessage.toString()) into reusable infrastructure for future
logging code that can't rely on a fixed DTO's own toString() override.
redact(String) masks the value half of any token=/secret=/password=/
apikey=/api_key=-shaped substring (case-insensitive key, casing
preserved) with ***, scoped explicitly to URL query-string-shaped
input (documented, not JSON/header-shaped secrets); a value terminates
at the next & or whitespace, matching query-string semantics exactly.
null input returns null - a deliberately lenient contract, unlike this
module's own stricter caller-contract precedent for core business-logic
methods, since this is an observability utility a caller might invoke
while already handling an error. Not yet wired into any real call site
- same "seam built ahead of its caller" shape as NotificationDispatcher
(T06), PreferenceResolver (T08), TemplateRenderer (T09).

Adds a permanent static-scan test (AC5) walking every real .java file
in this module: finds String-typed field/record-component declarations
named token/secret/password/apiKey/api_key, and asserts each such
field's own enclosing type both declares an explicit toString() and
that method's own body excludes the field. Found and fixed two real,
live bugs in the scan's own first-draft logic during test review, not
merely hypothetical edge cases: TemplateRenderer.computeLinkPlaceholders's
own local variable "String token" was being misidentified as a
sensitive field (fixed by requiring an access modifier or record-
component position); TemplateRenderer.java's own two type declarations
(the class and the nested RenderedMessage record) meant the scan could
check the wrong type's toString() (fixed by scoping each match to its
own nearest-enclosing type). Comments are stripped before scanning, so
a Javadoc sentence mentioning a field name in prose never triggers a
false positive.

199 tests total (174 T01-T09 unaffected + 25 new). Two Kimi review
rounds (Phase 8: 7 findings, 2 verified false against the actual frozen
brief; Phase 11: 7 gaps, 2 confirmed as real bugs and fixed) both fully
resolved - all 14 verified against real source before disposition. Two
real mutation tests performed and reverted clean: the redact() static
scan, and a re-run of that same mutation against the rewritten scan
logic after the Gap #2/#3 fix, confirming the fix didn't weaken
detection.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/common/SecretSafeLogging.java`
- `services/notification/src/test/java/com/themistra/notification/common/SecretSafeLoggingTest.java`
- `services/notification/src/test/java/com/themistra/notification/common/SensitiveFieldsHaveSafeToStringTest.java`
- `services/notification/src/test/java/com/themistra/notification/common/SecretSafeLoggingIntegrationTest.java`

**Modified**
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (27-file authorized list, renamed method)

**Process artifacts**
- `.ai/prompts/notification/T10/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Formalizes the token/secret-safety property into reusable infrastructure and, more importantly,
into a permanent, automated regression guard — the first task in this pipeline where "assert no
leak" is enforced by a static scan across the whole codebase, not merely by inspection at review
time. `DeliveryOrchestrator` (task 11) will be `redact()`'s own first real caller — none exists
yet, by this task's own explicit scope boundary.

## Testing performed

- `mvn -pl services/notification clean verify` — 199 tests, 0 failures, `BUILD SUCCESS`.
- Real end-to-end verification against T09's own real `TemplateRenderer` output: rendered
  `email.password_reset`, `email.verify`, and `user.verify`/`IN_APP` with real fake tokens,
  confirmed `redact()` removes each one from the real rendered body.
- Two real mutation tests, both reverted clean (`git status -s` empty afterward): (1) added a real
  `token=` reference to `EmailRequestedEvent.toString()`'s own body, confirmed the static scan
  caught it (Phase 10); (2) repeated the identical mutation after Phase 11's own scan rewrite,
  confirmed the rewritten logic still caught it (Phase 11 addendum) — proving the Gap #2/#3 fix
  didn't accidentally weaken real-leak detection while fixing the false-positive/wrong-scope bugs.
- `git status -s services/auth services/crypto services/payment` — empty throughout this task's
  own commits; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 10 ("Secret-safe rendering & logging").
- **Requirements:** R15.
- **LOCKED decision:** L4 (no secrets or tokens in messages or logs).
- **Resolved ambiguity:** `package.md` §9's own checklist phrasing ("never appears in a rendered
  message body") drops L4's own carve-out ("beyond the single intended one-time link") — resolved
  in L4's own favor at Phase 1, stated explicitly rather than silently picked, since a rendered
  `email.verify`/`email.password_reset` body legitimately contains the real token as part of its
  one intended link (T09's own design).

## Known, deliberate gaps (not this task's scope)

- **`SecretSafeLogging.redact` has zero real callers today** — `DeliveryOrchestrator` (task 11) is
  the only intended caller; same "seam built ahead of its caller" shape as T06/T08/T09's own
  equivalents.
- **`redact()` is scoped to URL query-string-shaped input only** — JSON/HTTP-header-shaped secrets
  are explicitly out of scope, documented in the class's own Javadoc.
- **The static scan's field-name heuristic has three disclosed, unfixed limitations**: cannot
  catch a semantically-sensitive-but-ambiguously-named field (`RenderedMessage.body`); only
  matches the first name in a multi-field declaration; only recognizes `String`-typed secrets.
  None of the three shapes occurs anywhere in this codebase today (verified).
- **The scan's own "nearest enclosing type" scope-tracking is a lightweight text-based
  approximation, not a real parser** — correct for every shape used in this codebase today, would
  need revisiting if a file with sibling top-level types were ever introduced.

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 8, 7, and 7
  findings/gaps respectively. **Two Phase 8 findings (#2, #3) were verified FALSE** — both
  described the superseded Phase 2 brief's own text, not the current, authoritative Phase 4 frozen
  brief, which had already resolved both concerns (the `toString()`-content check, dropping `key`
  from the field list) before Phase 6 implementation began. Confirmed by direct inspection of
  `artifacts/04-frozen-task-brief.md` and the actual shipped code — worth a reviewer's attention as
  a concrete example of the "verify against the cited artifact, not the reviewer's own claim about
  it" discipline this pipeline has followed throughout.
- **Two Phase 11 gaps (#2, #3) were the opposite case: findings that looked like hypothetical edge
  cases but turned out to be real, live bugs** in this exact codebase, confirmed via direct grep
  (`TemplateRenderer.computeLinkPlaceholders`'s own local variable; `TemplateRenderer.java`'s own
  two type declarations) — both were silently, accidentally passing before the fix (two wrongs
  cancelling out), which is exactly the kind of latent correctness a real code fix closes properly
  where documentation alone would not have. Worth a reviewer's specific attention as the strongest
  evidence in this task that adversarial review caught something real, not merely coverage
  bookkeeping.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T10.**
