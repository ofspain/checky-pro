STATUS: FROZEN

# notification · T16 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings verified directly against actual source before disposition — Kimi's own factual
claims (the real package layout, the real entity distribution, the real import lists for
`EmailChannel`/`DeliveryOrchestrator`) were independently re-confirmed via direct grep, not taken on
word. All 8 are **ACCEPTED**; none rejected.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `FEATURE_MODULES` must exactly match the current layout | — | **ACCEPTED** | `FEATURE_MODULES = List.of("channel", "consumer", "delivery", "inapp", "preference", "template")`; `common` deliberately excluded. Verified directly: 7 real `@Entity` classes distributed exactly as claimed (`delivery` 2, `consumer` 1, `template` 1, `inapp` 1, `preference` 2). A comment instructing future developers to update this list when adding a new top-level package is included. |
| 2 | The L2 rule is broader than L2's own literal wording | Low | **ACCEPTED, broader scope kept** | The whole-service scope (not narrowed to `delivery/`) is kept, exactly as Phase 2 proposed - simpler to enforce/review than distinguishing "external" from "sibling" at the import level, and this service has no legitimate use for a raw synchronous HTTP client today regardless. |
| 3 | The L2 ban list may miss a future HTTP client (OkHttp, Feign) | Low | **ACCEPTED, finite list kept** | Mirrors the established precedent's own finite-list style rather than inventing a longer, unvalidated one. A code comment states explicitly that adding any new HTTP client is a design-review-gated decision, not something this list silently also needs to track. |
| 4 | No negative proof is possible for the sibling-service-package half of L2 | — | **ACCEPTED, disclosed** | Confirmed directly - no `com.themistra.auth\|crypto\|payment` reference exists anywhere in this module's own source or `pom.xml`. `RogueHttpClientUser`'s own genuine negative proof (the HTTP-client half) stands in as proof the same rule mechanism works; the sibling-package half remains structural-only, documented as such, not silently unproven. |
| 5 | L8 rule relies on a Spring Security internal API that could silently stop matching on a future upgrade | Low | **ACCEPTED, precedent followed, no negative-proof test added** | Mirrors `services/auth`'s own identical rule and identical lack of a negative-proof test for it. If a future Spring Security upgrade ever breaks the match, the canary itself would need updating - acceptable, since the canary would then simply no longer find the real `permitAll()` call it expects, surfacing the need for a human to look rather than silently passing on a broken check (confirmed by reading the rule's own `noClasses()...should().callMethod(...)` shape - it fails loudly if the real call it expects to find and permit isn't found exactly, not silently). |
| 6 | `@ArchTest` fields alone do not execute under this monorepo's Surefire configuration | — | **ACCEPTED** | One real `@Test` canary per rule in `ArchitectureTest.java`; one single, eagerly-initialized `JavaClasses` instance shared by `@AnalyzeClasses` and every canary, so the two can never silently drift apart (mirrors both existing precedents' own documented lesson). |
| 7 | Test fixtures must not pollute the main scan | — | **ACCEPTED** | `ImportOption.DoNotIncludeTests` on both `@AnalyzeClasses` and the canary's own shared `ClassFileImporter`; every negative-proof test imports only its own named fixture classes via a separate, narrow `ClassFileImporter` call. |
| 8 | No pre-existing cross-module entity violation is expected, but must be confirmed, not assumed | — | **ACCEPTED** | Verified directly: no feature module imports another feature module's entity today. The pre-existing, already-permitted cross-module imports Kimi's own evidence named (`channel.EmailChannel` importing `template.TemplateRenderer` for its own `RenderedMessage` parameter type; `delivery.DeliveryOrchestrator` importing `channel.NotificationChannel`/`preference.PreferenceResolver`/`preference.ContactProjectionUpdater`/`template.TemplateRenderer`) are all service/interface imports, never an `@Entity` import - confirmed by direct inspection, none of those four target classes carries `@Entity`. Proceed assuming clean; if the first real canary run (Phase 6) finds otherwise, fix if cheap/in-scope or allowlist narrowly with a staleness-guard test - not decided here, since it cannot be known until the rule actually runs. |

## Task

Unchanged from Phase 2.

## Scope

Unchanged from Phase 2, with Finding #1's exact `FEATURE_MODULES` list and Findings #2/#3's
confirmed (not narrowed) L2 ban-list scope folded in.

## Business Rules

None (confirmed at Phase 1).

## Locked Decisions

L2, L8, L11 (unchanged).

## Dependencies

Unchanged from Phase 2: `archunit-junit5:1.3.0` (already present), JUnit Jupiter.

## Files to Create

Unchanged from Phase 2:
- `src/test/java/com/themistra/notification/ArchitectureTest.java`
- `src/test/java/com/themistra/notification/channel/RogueChannelEntityReferencer.java`
- `src/test/java/archtestfixtures/RogueUnmappedEntity.java`
- `src/test/java/archtestfixtures/RogueHttpClientUser.java`

## Files to Modify

None expected. Conditional (unchanged from Phase 2): only if the first real canary run surfaces a
genuine, pre-existing violation.

## Files NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged AC1-AC5 from Phase 2, with AC1's own `FEATURE_MODULES` list now pinned exactly:
`channel`, `consumer`, `delivery`, `inapp`, `preference`, `template` (`common` excluded).

## Required Tests

Unchanged from Phase 2.

## Constraints

Unchanged from Phase 2, plus (Finding #6): `@AnalyzeClasses` and every canary's own
`ClassFileImporter` must share one single, eagerly-initialized `JavaClasses` instance and identical
`ImportOption`, so the two can never silently analyze a different class set than each other.

## Open Questions

No blockers. All 8 Phase 3 findings resolved above, every one ACCEPTED - this task's own Phase 2
brief was already sound; Phase 3's own job here was confirming exact concrete values
(`FEATURE_MODULES`, the entity/import distribution) and affirming scope decisions already proposed,
not correcting a flaw.
