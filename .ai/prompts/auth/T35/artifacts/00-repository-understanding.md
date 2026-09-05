<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T35 · Phase 0 — Repository Understanding

## 1. Architecture summary

`auth-service` is feature-modularized under `com.themistra.auth.*` (`account`, `authn`, `authz`,
`apikey`, `audit`, `token`, `cleanup`, `ratelimit`, `mfa`, `events`, `common`), with module
boundaries enforced by `ArchitectureTest.java` (ArchUnit 1.3.0, `@AnalyzeClasses`). The file
already contains 10 `@ArchTest` rules plus 3 plain-JUnit tests (added at T32), one per specific
"no module may do X" design decision, each citing the decision it encodes.

## 2. Existing code this task touches

**`ArchitectureTest.java`** — the sole file this task extends. Directly relevant existing rules:

- **`only_the_account_module_may_touch_the_Account_entity`** (line 37) — this is the rule the
  task's own subject ("new modules do not import account entities") already maps onto. **It has a
  known, real, currently-unfixed bug, discovered during T32's own negative-proof testing (see
  Known gaps below) — squarely in this task's scope to fix, unlike T32 where it was out of scope.**
- No existing rule constrains "controllers depend only on their module's services" — the task's
  second clause is genuinely new work, not an extension of an existing rule.

**Controllers** (7 total, cataloged fully in T33's own Phase 0/4): `AccountController`,
`AdminAccountController`, `ApiKeyController`, `AdminAccountRoleController`, `AdminRoleController`,
`AdminRoleTemplateController`, `AdminAuditController`.

## 3. Established patterns to follow

- ArchUnit rules in this file are structural/dependency-based (`noClasses().that()...should()
  .dependOnClassesThat()...`), plus one method-call-site rule added at T32
  (`callMethod(...)`) and one method-annotation rule (`admin_controller_handlers_require_
  preauthorize`). All patterns needed for this task's two rules already have a direct precedent in
  this same file.
- **T32's canary-test pattern**: because ArchUnit's own JUnit 5 engine does not execute under this
  project's `mvn test` (see Known gaps), any NEW rule this task adds needs its own plain-`@Test`
  canary invoking `.check(analyzedClasses())` directly to actually gate a real build — established
  precedent, not a new design question.

## 4. Testing conventions

Same file, same conventions as T32: `@AnalyzeClasses(... ImportOption.DoNotIncludeTests.class)`
excludes test code from the analyzed population; rules verified via real negative-proof runs
(introduce a violation, confirm it fails, revert), not trusted from inspection alone — established
hard requirement by this point in the pipeline, given how much T32 specifically relied on it.

## 5. Known gaps / unknowns

**Two significant tensions between the task's literal wording and already-accepted, real code —
both need explicit Phase 1/2/4 resolution, not silent narrowing:**

1. **The existing account-entity rule has a real, already-discovered bug.** `.resideOutsideOfPackage
   ("com.themistra.auth.account")` (no `..` wildcard) means the rule's *actual* condition is "classes
   NOT in the exact package `com.themistra.auth.account`" — which incorrectly includes
   `account.dto`/`account.event`, subpackages the rule's own `.because(...)` text explicitly says
   should be exempt ("other modules address accounts via AccountService, UUIDs, and
   account.dto/account.event types only"). This was discovered during T32's own negative-proof
   testing (documented in T32's artifacts and project memory) but was out of T32's scope to fix.
   `AccountResponse.from(Account)` (in `account.dto`, a completely correct, ordinary DTO factory
   method) currently violates this rule as literally written — confirmed via a direct JUnit
   Platform Launcher run in T32 (Surefire itself never actually runs this rule today, which is
   exactly why the bug has never surfaced as a real build failure). **This task's own subject line
   ("ensure new modules do not import account entities") gives clear license to fix this now.**

2. **"Controllers depend only on their module services" conflicts with two real, already-documented
   design decisions.** Confirmed via direct import inspection of all 7 controllers:
   - `AccountController` (in `account`) imports and depends on `SessionService` (from `token`) —
     used by the `/accounts/me/sessions` endpoints.
   - `AdminAccountController` (in `account`) imports and depends on `LockoutService` (from
     `authn`) — used by `POST /admin/accounts/{accountUuid}/unlock`, with its own Javadoc
     explicitly framing this as a deliberate, precedented pattern: *"the first method here to
     depend on a service from another module ... the same shape `AccountUserDetailsService`
     already established (T12), not a new precedent."*

   A literal reading of the task statement would make both of these fail a new ArchUnit rule. Since
   both are pre-existing, intentional, already-shipped design decisions (not defects), the new rule
   cannot simply be "no controller may depend on another module's service" — it needs a
   Phase 1/2/4 decision on how to scope it (e.g., an explicit, named allowlist of the two known
   exceptions; a narrower rule about a *specific* kind of cross-module dependency, like entities or
   repositories, rather than services in general; or some other framing). Flagging now rather than
   letting Phase 2 quietly narrow or widen this without visibility.

**Also noting, consistent with the pattern found in T32/T33/T34:** `package.md`'s own named-test
table maps `shouldPreventCrossModuleEntityImports → L10`, but L10 ("MFA enforcement role rule," per
`design.md`) is unrelated — the actual matching LOCKED decision is **L12** ("Module boundaries"),
which is exactly what this task's own Phase 0 header already correctly cites. Treated as a known,
already-recurring `package.md` staleness pattern, not a new investigation — no further action
needed here beyond following the header's own (correct) L12 citation.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
