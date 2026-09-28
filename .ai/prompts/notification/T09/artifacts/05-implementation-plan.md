# notification · T09 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/template/Template.java`
2. `.../template/TemplateRepository.java`
3. `.../template/TemplateRenderer.java`
4. `services/notification/src/main/resources/db/migration/V7__notification_app_templates_grant.sql`

## Files to modify

1. `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
   (Finding #7) — Flyway-history expectation `"1".."7"`; new grant-proof test for `templates`.

No files outside this list this phase (`T01SkeletonRegressionTest.java` remains a Phase 6 disclosed
deviation per the frozen brief's own note).

## Public methods (signatures)

**`Template`** (`@Entity`, `template/`)
```java
@Entity
@Table(name = "templates", schema = "notifications")
public class Template {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "name", nullable = false) private String name;
    @Column(name = "channel", nullable = false) private String channel;
    @Column(name = "version", nullable = false) private int version;
    @Column(name = "subject") private String subject; // nullable - IN_APP rows have none
    @Column(name = "body", nullable = false) private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Template() {} // JPA only
    // getters only — no write path in this task
}
```

**`TemplateRepository`** (interface, package-private, `template/`)
```java
interface TemplateRepository extends JpaRepository<Template, Long> {
    // Kimi Phase 3 Finding #6: extends JpaRepository per T04/T05/T08's own established precedent -
    // save/delete are inherited but never called; V7 grants SELECT only, so any accidental call
    // fails at the DB layer regardless.
    Optional<Template> findTopByNameAndChannelOrderByVersionDesc(String name, String channel);
}
```

**`TemplateRenderer`** (`@Service`, `template/`)
```java
@Service
public class TemplateRenderer {

    public record RenderedMessage(String subject, String body, int version) {}

    public TemplateRenderer(TemplateRepository repository, LinkProperties linkProperties);

    public RenderedMessage render(String name, String channel, Map<String, String> eventData);
}
```

Logic (in order):
1. Require non-null `name`/`channel`/`eventData` (`Objects.requireNonNull`, same caller-contract
   precedent as T08's `PreferenceResolver.resolve`).
2. `repository.findTopByNameAndChannelOrderByVersionDesc(name, channel)` — throw
   `IllegalArgumentException` if absent (AC7).
3. Build the merged values map: start from `eventData` (copied, never mutated), overlay the 5
   computed link placeholders on top (Finding #2's own Javadoc note: `invoiceLink`/`receiptLink`
   are computed from `invoiceUuid`/`receiptUuid` — the real field names per
   `spec/payment-service/design.md`'s own `receipt.issued` schema — distinct from the ordinary,
   directly-substituted `{{invoiceId}}` display placeholder already seeded in `V3`, which needs no
   special handling at all).
4. Substitute `subject` (if non-null) and `body` via the shared `substitute` helper; return
   `RenderedMessage(subject, body, template.getVersion())`.

## Private methods

- `computeLinkPlaceholders(Map<String, String> eventData)` — returns the up-to-5-entry link map:
  `verificationLink`/`resetLink` (both, whenever `eventData.get("token")` is non-null — computed
  together since a given template body only ever references one of them, Finding #2's own
  precedent applied here too); `getStartedLink` (always, needs no raw source); `invoiceLink`
  (whenever `eventData.get("invoiceUuid")` is non-null); `receiptLink` (whenever
  `eventData.get("receiptUuid")` is non-null). Every raw value is `URLEncoder.encode(value,
  StandardCharsets.UTF_8)`-encoded before appending (Finding #3).
- `normalizeBaseUrl(String baseUrl)` — `null` → `""`; strips exactly one trailing `/` if present
  (Findings #4/#5).
- `substitute(String text, Map<String, String> values)` — the shared placeholder engine: matches
  `\{\{([a-zA-Z][a-zA-Z0-9_]*)\}\}` (Finding #8), looks up each captured key in `values`, treats a
  `null` lookup result (whether from a missing key or an explicit `null` value stored under an
  existing key — Finding #9) as `""`, and passes every replacement through
  `Matcher.quoteReplacement(...)` before `appendReplacement` (Finding #1).

## Entities used

`Template` (new, this task).

## Repositories used

`TemplateRepository` (new, this task).

## Services used

None — `TemplateRenderer` depends only on its own new `TemplateRepository` and the pre-existing
`LinkProperties` (T03's own config record, first real consumer here). No dependency on T04/T05/T06/
T08's own services.

## Unit / integration tests required

Deferred to Phase 10 (per this module's own established rule) — no Phase 6 carve-out this task.
Planned Phase 10 coverage:

1. `TemplateRenderer` renders the named event/channel using the currently-versioned template
   (`shouldRenderTemplateWithEventDataAndSelectedChannel`, AC2/AC3).
2. Highest-`version` wins when two rows exist for the same `(name, channel)` (AC2).
3. A missing `eventData` key/an explicit `null` value both substitute to empty string (AC3, Finding
   #9).
4. `RenderedMessage.version()` matches the fetched row's own version (AC4).
5. Each of the 5 computed link placeholders, at least once (AC6).
6. A value containing `$`/`\` renders correctly, no exception (AC8, Finding #1).
7. A token containing `&`/`=`/space renders correctly URL-encoded in a computed link (AC8, Finding
   #3).
8. Blank `baseUrl` produces a relative link (Finding #4); a trailing-slash `baseUrl` produces a
   single `/` at the join point (AC9, Finding #5).
9. Unknown `(name, channel)` throws `IllegalArgumentException` (AC7).
10. `V7` grant-proof, in `NotificationBaselineMigrationIntegrationTest`: `notification_app` can
    `SELECT` but not `INSERT`/`UPDATE`/`DELETE` on `templates` (Finding #7, AC5).

## Execution order

1. `Template` (no dependencies on anything else new).
2. `TemplateRepository` (depends on step 1).
3. `V7__notification_app_templates_grant.sql` (independent of 1/2, needed before any
   Testcontainers-backed test can pass).
4. `TemplateRenderer` (depends on steps 1-2, plus the already-existing `LinkProperties`).
5. `NotificationBaselineMigrationIntegrationTest` update (Finding #7) — depends on step 3 existing.
6. Phase 10's own planned tests (depends on steps 1-4).
