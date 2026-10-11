# payment · T01 · Phase 11 — Test Review

Reviewing `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`
(8 tests, all passing per Phase 10's artifact) against the frozen brief's own Required Tests
section and the acceptance criteria. Produced directly (no separate Kimi process available this
round) — held to the same adversarial standard: hunting for weaknesses in my own prior work, not
confirming it.

---

### 1. The "Testcontainers postgresql" check is satisfied even if that dependency is removed

- **Gap:** `paymentPomDeclaresTheExactFoundationDependencySet`'s own Testcontainers-postgres check
  (lines 94-99) is: `pom.lines().filter(line -> line.contains("org.testcontainers") ||
  line.contains("<artifactId>postgresql</artifactId>")).count() > 0`. The ordinary JDBC driver
  dependency (`org.postgresql:postgresql`, asserted separately two lines above) shares the exact
  same bare artifactId string `postgresql` as the Testcontainers module
  (`org.testcontainers:postgresql`) this check is meant to verify. Because the condition is an
  **OR**, and the JDBC driver's own `<artifactId>postgresql</artifactId>` is already guaranteed
  present by an earlier assertion, this check would still pass even if
  `org.testcontainers:postgresql` were deleted outright — any other `org.testcontainers` line
  (kafka, junit-jupiter) keeps it green regardless.
- **Why it matters:** AC2 explicitly requires `org.testcontainers:postgresql` in test scope. A
  future edit that accidentally drops it (e.g. during the Testcontainers version bump some later
  task will eventually need) would not be caught by this suite at all.
- **Suggested test:** replace the OR-based line scan with a groupId+artifactId pair match —
  `doesNotContain` is wrong here; instead assert a pattern requiring `<groupId>org.testcontainers</groupId>`
  immediately followed by `<artifactId>postgresql</artifactId>` (the pom's own compact single-line
  dependency style makes this a simple adjacent-tag regex, the same technique `dependencyVersion`
  already uses for artifactId+version pairs).

### 2. The main-method regex is vulnerable to the exact comment-ambiguity bug already found once in this file

- **Gap:** `applicationClassIsBareWithStandardMainMethod`'s method count
  (`Pattern.compile("\\bpublic\\s+static\\s+void\\s+main\\b")`) runs against the raw `.java`
  source, comments included. This test file itself already proved, twice, that a comment
  explaining *why* something is present can accidentally contain the literal text a negative/
  count-based check is scanning for (`stripXmlComments` exists specifically because of it, applied
  to the pom checks) — but no equivalent stripping was applied here, in the one Java-source check
  this suite has.
- **Why it matters:** today's `PaymentServiceApplication.java` has no such comment, so this passes
  cleanly now — but it is one documentation edit away from the identical class of false failure
  already hit twice in this same PR, and nothing here would warn a future editor they're about to
  trip it.
- **Suggested test:** add a small `stripJavaComments` helper (strip `/* ... */` and `// ...`)
  mirroring `stripXmlComments`, and run the main-method count and the class-level-annotation count
  against the stripped source instead of the raw one.

### 3. No protection against an accidentally duplicated dependency entry

- **Gap:** Every dependency check in this suite is a `contains`/`containsPattern` — true for "at
  least one occurrence," never "exactly one." A copy-paste error that declares the same
  `<dependency>` block twice would pass every single assertion in this file unchanged (Maven
  itself tolerates the duplicate silently too, so `mvn test` wouldn't catch it either).
- **Why it matters:** low risk at today's size (12 production dependencies), but this pom gains
  more dependencies across the next 28 tasks, and nothing in this suite would ever flag a
  duplicate introduced along the way.
- **Suggested test:** for the dependency coordinates this task itself owns (the twelve listed in
  AC2), assert each `<artifactId>…</artifactId>` tag string appears in the pom exactly once via a
  count-based assertion, not `contains`.

---

## Resolution (applied directly — no dedicated gate after Phase 11 in this pipeline)

Mirrors the established pattern from `auth-service`'s own T09 Phase 11 ("when Phase 11 surfaces
genuinely valuable, low-risk, in-scope findings, apply them directly... fold a resolution-log-style
addendum into the [relevant] artifact"). Findings 1–3 were all genuinely valuable and low-risk, so
applied directly, each verified by a real mutation (introduce the exact defect the fix targets,
confirm the suite now catches it, revert, confirm it's clean again):

- **Finding 1 (Testcontainers postgres false-negative):** replaced the OR-based line scan with a
  `<groupId>org\.testcontainers</groupId>\s*<artifactId>postgresql</artifactId>` pair match.
  Mutation: deleted the real `org.testcontainers:postgresql` dependency → **1 failure** (was 0).
  Reverted → 8/8 pass.
- **Finding 2 (comment-ambiguity in the Java-source check):** added `stripJavaComments`, applied
  to both the main-method regex and the annotation count. Mutation: added a `//` comment to
  `PaymentServiceApplication.java` containing the literal text `public static void main` →
  **0 failures** (the exact false-positive this fix exists to prevent — confirmed it does not
  fire). Reverted.
- **Finding 3 (no duplicate-dependency protection):** added `assertDeclaredExactlyOnce`, applied
  to every unambiguous AC2/AC5 artifactId tag (the bare `postgresql` tag is deliberately excluded —
  it legitimately matches twice, the JDBC driver and the Testcontainers module, now asserted as
  exactly 2 plus the groupId-paired check from finding 1). Mutation: duplicated the
  `spring-boot-starter-web` dependency block → **1 failure** (was 0). Reverted.
- **Finding 4:** no test change, as recommended — a documentation comment was added to the
  annotation-count assertion instead, noting its file-wide (not position-scoped) nature for
  whoever next extends this class.

**Final state:** `mvn -pl services/payment test` → 8/8 passing after all four fixes. `git status`/
`git diff --stat` confirm every mutation was fully reverted before this state was reached — no
backup file or stray edit survives.

---

### 4. (Minor, no test change recommended) `classLevelAnnotations`'s count is file-wide, not position-scoped

- **Gap:** The AC4 annotation count (lines 138-144) counts any line in the whole file starting
  with `@`, not specifically the line immediately preceding `class PaymentServiceApplication`. For
  today's file this is accidentally equivalent (there is exactly one `@`-starting line and it is
  the class annotation), so it is not a false positive right now.
- **Why it matters:** low priority — flagged for the record, not as something to fix. If a later
  task ever needs to add an annotated method to this same class, this assertion's current, blunt
  scope would start failing for a reason unrelated to its own stated intent ("no annotation beyond
  `@SpringBootApplication`"), and nothing today documents that caveat for whoever hits it.
- **Suggested test:** none needed now; a one-line comment addition would be enough, not a behavior
  change.
