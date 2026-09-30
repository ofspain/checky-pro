# notification · T11 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify/
Delete. No additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryLog.java`
2. `.../delivery/DeliveryLogRepository.java`
3. `.../delivery/DeliveryOrchestrator.java`
4. `.../channel/NotificationChannel.java`
5. `.../channel/NoOpEmailChannel.java`
6. `.../channel/NoOpInAppChannel.java`

## Files to modify

1. `.../preference/ContactProjectionUpdater.java` — add `findEmail`.
2. `.../consumer/AuthEventConsumer.java` — add `sourceEventKey` to both `eventData` maps.

## Files to delete

1. `.../consumer/NoOpNotificationDispatcher.java` — pre-authorized since T06.

## Public methods (signatures)

**`DeliveryLog`** (`@Entity`, `delivery/`)
```java
@Entity
@Table(name = "delivery_log", schema = "notifications")
public class DeliveryLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "account_uuid") private UUID accountUuid;
    @Column(name = "recipient") private String recipient;
    @Column(name = "channel", nullable = false) private String channel;
    @Column(name = "source_event_key", nullable = false) private String sourceEventKey;
    @Column(name = "template_name") private String templateName;
    @Column(name = "template_version") private Integer templateVersion;
    @Column(name = "attempt", nullable = false) private short attempt;
    @Column(name = "outcome", nullable = false) private String outcome;
    @Column(name = "error_detail") private String errorDetail;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DeliveryLog() {} // JPA only

    public DeliveryLog(UUID accountUuid, String recipient, String channel, String sourceEventKey,
                        String templateName, Integer templateVersion, String outcome,
                        String errorDetail, Instant createdAt) {
        this.accountUuid = accountUuid;
        this.recipient = recipient;
        this.channel = channel;
        this.sourceEventKey = sourceEventKey;
        this.templateName = templateName;
        this.templateVersion = templateVersion;
        this.attempt = 1; // task 14's own scope to ever write a value > 1
        this.outcome = outcome;
        this.errorDetail = errorDetail;
        this.createdAt = createdAt;
    }
    // getters for all 10 non-generated-differently fields
}
```

**`DeliveryLogRepository`** (interface, package-private, `delivery/`)
```java
interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {
    // no custom methods - plain save() is this task's own entire write path, the first module
    // repository where that's the correct, idiomatic design (append-only, no upsert/conflict
    // logic needed, unlike every prior task's own read-mostly repository).
}
```

**`NotificationChannel`** (interface, `channel/`)
```java
public interface NotificationChannel {
    String channel(); // "EMAIL" or "IN_APP"

    /**
     * @param recipient the email address for EMAIL, the account UUID's own string form for
     *                   IN_APP (Kimi Phase 3 Finding #2/#6).
     */
    void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message);
}
```

**`NoOpEmailChannel`** / **`NoOpInAppChannel`** (`@Component`, `channel/`)
```java
@Component
public class NoOpEmailChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(NoOpEmailChannel.class);

    @Override public String channel() { return "EMAIL"; }

    @Override
    public void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message) {
        log.info("Email dispatch (no-op): accountUuid={}, recipient={}, message={}",
                accountUuid, recipient, message); // message's own toString() already excludes content (T09)
    }
}
```
`NoOpInAppChannel` is identical in shape, `channel()` returns `"IN_APP"`.

**`ContactProjectionUpdater`** (extended, `preference/`)
```java
public Optional<String> findEmail(UUID accountUuid) {
    return repository.findById(accountUuid).map(ContactProjection::getEmail);
}
```

**`AuthEventConsumer`** (extended, `consumer/`) — both listener methods' own `eventData`
construction gains `"sourceEventKey"`:
```java
notificationDispatcher.dispatch(event.accountUuid(), notificationKind,
        Map.of("token", event.token(), "sourceEventKey", eventKey));
// ...
notificationDispatcher.dispatch(event.accountUuid(), "user.registered",
        Map.of("sourceEventKey", eventKey));
```

**`DeliveryOrchestrator`** (`@Component implements NotificationDispatcher`, `delivery/`)
```java
@Component
public class DeliveryOrchestrator implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryOrchestrator.class);
    private static final List<String> LAUNCH_CHANNELS = List.of("EMAIL", "IN_APP");

    private record NotificationMapping(
            String emailTemplateName, String inAppTemplateName, String category) {}

    // VERBATIM from design.md §4c's own topic->template table + category groupings (Finding #7:
    // a null per-channel name here would mean "skip this channel" - not needed today, every real
    // entry has both). user.registered -> SECURITY (Phase 0/1's own resolved reasoning). auth.
    // user.lifecycle(user.suspended) -> account.suspended deliberately excluded (unreachable,
    // unseeded).
    private static final Map<String, NotificationMapping> NOTIFICATION_MAPPINGS = Map.of(
            "verify_email", new NotificationMapping("email.verify", "user.verify", "SECURITY"),
            "password_reset", new NotificationMapping("email.password_reset", "user.password_reset", "SECURITY"),
            "user.registered", new NotificationMapping("user.welcome", "user.welcome", "SECURITY"),
            "invoice.created", new NotificationMapping("invoice.created", "invoice.created", "PAYMENT"),
            "payment.seen", new NotificationMapping("payment.seen", "payment.seen", "PAYMENT"),
            "payment.finalized", new NotificationMapping("payment.finalized", "payment.finalized", "PAYMENT"),
            "receipt.issued", new NotificationMapping("receipt.issued", "receipt.issued", "PAYMENT"));

    private final PreferenceResolver preferenceResolver;
    private final TemplateRenderer templateRenderer;
    private final ContactProjectionUpdater contactProjectionUpdater;
    private final DeliveryLogRepository deliveryLogRepository;
    private final Clock clock;
    private final Map<String, NotificationChannel> channelsByName;

    public DeliveryOrchestrator(PreferenceResolver preferenceResolver, TemplateRenderer templateRenderer,
            ContactProjectionUpdater contactProjectionUpdater, DeliveryLogRepository deliveryLogRepository,
            Clock clock, List<NotificationChannel> channels) {
        this.preferenceResolver = preferenceResolver;
        this.templateRenderer = templateRenderer;
        this.contactProjectionUpdater = contactProjectionUpdater;
        this.deliveryLogRepository = deliveryLogRepository;
        this.clock = clock;
        this.channelsByName = channels.stream().collect(Collectors.toMap(NotificationChannel::channel, c -> c));
    }

    @Override
    @Transactional
    public void dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData) {
        try {
            NotificationMapping mapping = notificationKind == null ? null : NOTIFICATION_MAPPINGS.get(notificationKind);
            if (mapping == null) {
                log.debug("No notification mapping for kind={}, skipping dispatch", notificationKind);
                return;
            }
            String sourceEventKey = eventData.get("sourceEventKey");
            String email = contactProjectionUpdater.findEmail(accountUuid).orElse(null);
            for (String channel : LAUNCH_CHANNELS) {
                dispatchOneChannel(accountUuid, email, sourceEventKey, eventData, mapping, channel);
            }
        } catch (Exception e) { // AC9/Finding #9: Exception, not Throwable
            log.error("Unexpected failure in dispatch for accountUuid={}, notificationKind={}",
                    accountUuid, notificationKind, e);
        }
    }

    private void dispatchOneChannel(UUID accountUuid, String email, String sourceEventKey,
            Map<String, String> eventData, NotificationMapping mapping, String channel) {
        try {
            String templateName = "EMAIL".equals(channel) ? mapping.emailTemplateName() : mapping.inAppTemplateName();
            if (templateName == null) {
                return; // Finding #7
            }
            String recipient = "EMAIL".equals(channel) ? email : accountUuid.toString(); // Finding #2/#6

            boolean enabled = preferenceResolver.resolve(accountUuid, mapping.category(), channel);
            if (!enabled) {
                save(accountUuid, recipient, channel, sourceEventKey, null, null, "SUPPRESSED", null);
                return;
            }
            if ("EMAIL".equals(channel) && email == null) { // Finding #3
                save(accountUuid, null, channel, sourceEventKey, templateName, null,
                        "FAILED", "no recipient email on file");
                return;
            }

            TemplateRenderer.RenderedMessage message;
            try {
                message = templateRenderer.render(templateName, channel, eventData);
            } catch (Exception e) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, null, "FAILED", e.getMessage());
                return;
            }

            NotificationChannel channelBean = channelsByName.get(channel);
            if (channelBean == null) { // Finding #4's own "missing channel bean" row
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(),
                        "FAILED", "no channel bean registered for " + channel);
                return;
            }

            try {
                channelBean.send(accountUuid, recipient, message);
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(), "SENT", null);
            } catch (Exception e) {
                save(accountUuid, recipient, channel, sourceEventKey, templateName, message.version(),
                        "FAILED", e.getMessage());
            }
        } catch (Exception e) { // per-channel safety net - a save() failure on ONE channel must
            // never prevent the OTHER channel's own attempt, nor escape to dispatch's own boundary
            log.error("Unexpected failure dispatching channel={} for accountUuid={}", channel, accountUuid, e);
        }
    }

    private void save(UUID accountUuid, String recipient, String channel, String sourceEventKey,
            String templateName, Integer templateVersion, String outcome, String errorDetail) {
        String redacted = errorDetail == null ? null : SecretSafeLogging.redact(errorDetail); // Finding #5
        deliveryLogRepository.save(new DeliveryLog(accountUuid, recipient, channel, sourceEventKey,
                templateName, templateVersion, outcome, redacted, clock.instant()));
    }
}
```

## Entities used

`DeliveryLog` (new, this task).

## Repositories used

`DeliveryLogRepository` (new, this task), `ContactProjectionRepository` (T05, via
`ContactProjectionUpdater`'s own new `findEmail`, not called directly).

## Services used

`PreferenceResolver` (T08), `TemplateRenderer` (T09), `SecretSafeLogging` (T10, static, not
injected), `ContactProjectionUpdater` (T05, extended), `Clock` (T04's own `ClockConfig` bean).

## Unit / integration tests required

Deferred to Phase 10 (per this module's own established rule) — no Phase 6 carve-out this task.
Planned Phase 10 coverage, split across a mocked-collaborator unit test class
(`DeliveryOrchestratorTest`) and a real-Postgres integration test class
(`DeliveryOrchestratorIntegrationTest`):

1. Each pinned outcome per the decision table (Finding #4): `SENT` (enabled, render succeeds, send
   succeeds), `SUPPRESSED` (disabled), `FAILED`-render, `FAILED`-missing-recipient (`EMAIL` only),
   `FAILED`-send, unknown-kind no-row.
2. `IN_APP`'s own recipient is the account UUID's own string form; `EMAIL`'s own recipient is the
   resolved email (Finding #2).
3. A missing contact projection: `EMAIL` → `FAILED`; `IN_APP` → proceeds normally (Finding #3).
4. `errorDetail` is redacted before being persisted (Finding #5) — a render/send failure whose
   message contains `token=abc` is stored as `token=***`.
5. `dispatch` is `@Transactional` (reflection) and a `delivery_log` row it wrote rolls back if the
   external caller's own transaction later rolls back (Finding #1, corrected test).
6. `dispatch` never throws for a `null` `notificationKind`, an unmapped `notificationKind`, a
   throwing `PreferenceResolver`/`TemplateRenderer`/channel (AC9).
7. `ContactProjectionUpdater.findEmail` — present/absent cases (unit, mocked repository).
8. `AuthEventConsumer`'s own two listener methods now include `sourceEventKey` in `eventData` —
   extend the existing `AuthEventConsumerTest`/`AuthEventConsumerIntegrationTest` assertions rather
   than duplicating them in a new file.
9. `NoOpEmailChannel`/`NoOpInAppChannel` — `channel()` returns the right constant; `send` never
   throws and its own log output excludes message content (mirrors `NoOpNotificationDispatcherTest`'s
   own precedent).

## Execution order

1. `DeliveryLog` (no dependencies on anything else new).
2. `DeliveryLogRepository` (depends on step 1).
3. `NotificationChannel` (no dependencies).
4. `NoOpEmailChannel` / `NoOpInAppChannel` (depend on step 3).
5. `ContactProjectionUpdater.findEmail` (depends on nothing new — extends an existing T05 class).
6. `AuthEventConsumer`'s own `sourceEventKey` addition (depends on nothing new).
7. `DeliveryOrchestrator` (depends on steps 1-6, plus already-existing `PreferenceResolver`/
   `TemplateRenderer`/`SecretSafeLogging`/`Clock`).
8. Delete `NoOpNotificationDispatcher.java` (depends on step 7 existing as its replacement).
9. Phase 10's own planned tests (depend on steps 1-8).
