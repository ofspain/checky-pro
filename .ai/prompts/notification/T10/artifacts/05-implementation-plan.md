# notification · T10 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/common/SecretSafeLogging.java`

## Files to modify

None this phase (`T01SkeletonRegressionTest.java` remains a Phase 6 disclosed deviation per the
frozen brief's own note — exact content unknown until the file exists).

## Public methods (signatures)

**`SecretSafeLogging`** (final utility class, `common/`)
```java
public final class SecretSafeLogging {

    private static final Pattern SECRET_PARAM_PATTERN = Pattern.compile(
            "(?i)(token|secret|password|api_?key)=([^&\\s]*)");

    private SecretSafeLogging() {
        // static utility - never instantiated (Kimi Phase 3 Finding #3)
    }

    /**
     * Masks the value half of any {@code token=}/{@code secret=}/{@code password=}/
     * {@code apikey=}/{@code api_key=}-shaped substring (case-insensitive key) with {@code ***},
     * preserving the key's own original casing. Scoped to URL query-string-shaped input only
     * (Kimi Phase 3 Finding #4) - a value terminates at the next {@code &} or whitespace
     * character (Finding #6), matching query-string semantics exactly; JSON/HTTP-header-shaped
     * secrets are out of this method's own scope. {@code null} input returns {@code null}
     * (AC4) - a deliberately lenient contract for an observability utility a caller might invoke
     * while already handling an error.
     */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SECRET_PARAM_PATTERN.matcher(text);
        return matcher.replaceAll(match -> match.group(1) + "=***");
    }
}
```

## Private methods

- None beyond the constructor — `redact`'s own logic is a single expression, not worth extracting
  further.

## Entities used

None.

## Repositories used

None.

## Services used

None — `SecretSafeLogging` has no dependency on any other module's own class.

## Unit / integration tests required

Deferred to Phase 10 (per this module's own established rule) — no Phase 6 carve-out this task.
Planned Phase 10 coverage (all in a new `common/SecretSafeLoggingTest.java`, plain JUnit, no Spring
context, no Docker, except where noted):

1. `redact` masks a single `token=`/`secret=`/`password=`/`apikey=`/`api_key=`-shaped substring,
   one test per key name (AC1).
2. `redact` masks multiple distinct secret-shaped substrings in one input (AC2).
3. `redact` leaves text with no secret-shaped substring unchanged, byte-for-byte (AC3).
4. `redact(null)` returns `null` (AC4).
5. `redact` is case-insensitive on the key name, preserving the matched key's own original casing
   in the output (AC1).
6. `redact("password=hello world")` redacts only up to the space, leaving `"world"` exposed and
   documenting that boundary explicitly (Finding #6).
7. Instantiation is blocked by the private constructor (AC6) — a reflection-based test asserting
   the sole constructor is private, mirroring how other "cannot be instantiated" utility classes
   are conventionally proven, or simply omitted if a private constructor alone is judged
   sufficient without a dedicated runtime test (Phase 6's own call).
8. A **new, separate test file** (likely `common/SensitiveFieldsHaveSafeToStringTest.java`) — the
   static scan (AC5): walks every `.java` file under `src/main/java/com/themistra/notification`,
   finds `String`-typed field/record-component declarations named (case-insensitively) `token`,
   `secret`, `password`, or `apiKey`/`api_key`, and for each match, asserts (a) the same file
   declares an explicit `toString()` method (its literal text must appear — for a `record`, this
   also correctly rejects reliance on the auto-generated `toString()`, since that text is never
   written into the `.java` source at all, per Finding #7's own confirmation), and (b) that
   `toString()`'s own body text (from its own opening to matching closing brace) does not contain
   the sensitive field's own name as a whole-word identifier, nor a `get<Name>` getter call
   (Finding #1's own tightening).
   - Includes a synthetic positive fixture (a sensitive field with a leaking `toString()`) proving
     the tightened scan fails it, and a synthetic record fixture with a sensitive component and no
     explicit `toString()` proving the scan fails that too (Finding #7).
9. `redact()` against a real `TemplateRenderer`-rendered `email.password_reset` body (Finding #5)
   — a `@Testcontainers`/`@SpringBootTest` test (the module's own established pattern for anything
   needing `TemplateRenderer`), asserting the raw token is absent and `token=***` is present after
   redaction.

## Execution order

1. `SecretSafeLogging` (no dependencies on anything else new).
2. Phase 10's own planned tests (depend on step 1, plus, for test #9 above, T09's own already-built
   `TemplateRenderer`).
