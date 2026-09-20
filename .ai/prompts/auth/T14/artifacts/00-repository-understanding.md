# auth · T14 — Phase 0: Repository Understanding

Grounding only — no design, no requirements extraction. Read: `spec/auth-service/{package.md,
requirements.md, design.md, tasks.md, agents.md}` plus the actual repository state.

## 1. Architecture summary

Same as documented at T11/T12/T13 Phase 0 — unchanged. T14 is a small, well-scoped task: one new
admin endpoint, no new module, no new persistence.

## 2. Existing code this task touches

**Already exists, directly reusable:**
- `AdminAccountController.java` (`account` package) — the exact file this task extends. Already
  has `get`, `activate`, `suspend`, `reinstate`, `delete`, each following an identical shape:
  `@PostMapping("/{accountUuid}/<action>")`, `@PreAuthorize(...)`, `Authentication authentication`
  resolved via a private `actorUuid(authentication)` helper (`UUID.fromString(authentication
  .getName())`), delegating to a same-named `AccountService` method. `suspend`/`reinstate` already
  use `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")` — the exact role pair R20's own header
  names ("ADMIN or COMPLIANCE"). T14's `unlock` method is a direct, same-shape addition to this
  file, not a new controller.
- `LockoutService.resetLockout(UUID accountUuid)` (T12, `authn` package) — already exists,
  explicitly built anticipating this task (T12's own Javadoc: "for T14's future use; not called by
  anything in this task"). Zeroes `lockout_state` (or no-ops if the row is absent) and calls the
  guarded `AccountService.unlock(UUID)` — which itself only transitions `LOCKED → ACTIVE` if the
  account is currently `LOCKED` (a no-op otherwise). Between the two, `resetLockout` already
  satisfies R20's "transition the account to `ACTIVE` and clear the failed-attempt counter and
  `lock_count`" in one call. Returns `LockoutStateMachine.LockoutDecision`, not `AccountResponse` —
  a shape mismatch with every sibling `AdminAccountController` method, noted below.
- `AccountService.unlock(UUID)` (T13) — guarded, idempotent, already exists. `LockoutService
  .resetLockout` already calls it; this task does not need to call it directly.

**A real design tension this phase surfaces, not resolved here:** none of `AccountService`'s
existing audit-emitting admin methods (`suspend`, `reinstate`, `delete`, `activate` — each calls a
private `recordAudit(eventType, accountUuid, actorUuid)` helper internally) exist for unlock.
`LockoutService.resetLockout` and `AccountService.unlock` neither one audits today — audit was
explicitly out of scope for both T12 (deferred at T12's own Phase 2 scoping) and T13 (T13 audits
only `login.failed`, not any unlock path). If audit logic were added *inside*
`AccountService.unlock(UUID)` itself, it would incorrectly fire for **every** caller of that
method, including the ordinary login-flow-driven unlocks `LoginSuccessHandler`/`LoginFailureHandler`
(T13) already trigger via `LockoutService` — not just this task's admin-initiated action. Audit
must be scoped to the admin action specifically, which means it needs a home somewhere new: either
the controller calls `AuditService` directly (no precedent — every existing admin controller
method delegates audit entirely to its `AccountService` call), or a new orchestrating method is
needed somewhere. Not decided here — flagged for Phase 1/2.

## 3. Established patterns to follow

- **Admin controller shape:** confirmed directly from `AdminAccountController.java` — per-method
  `@PreAuthorize` (never class-level, per that file's own Javadoc rationale: "avoid depending on
  unverified class-level method-security behavior across Spring Security versions"), `Authentication
  authentication` parameter, actor UUID resolved via the file's own private helper. `ArchitectureTest
  .admin_controller_handlers_require_preauthorize` (ArchUnit, confirmed present) enforces this at
  compile-verification time for every public method in any `Admin*`-named `@RestController`.
- **Audit:** `AccountService`'s existing pattern — a private `recordAudit(eventType, accountUuid,
  actorUuid)` helper calling `AuditService.record(new RecordAuditEventRequest(...))`, with `null`
  for `ip`/`rawUserAgent`/`traceId`/`details` (matching every admin-action call site; T13's
  `LoginFailureHandler` was the *first* call site to populate those fields, using its own direct
  `HttpServletRequest` access — an admin endpoint has that same access if needed, unlike
  `LockoutService`, which never does). Event-type naming convention: `"account.suspended"`,
  `"account.reinstated"`, `"account.deleted"`, `"account.activated"` — `"account.unlocked"` would
  be the natural, consistent name for this task's event, though this is a Phase 1/2 naming
  decision, not confirmed by any existing constant.
- **Module boundaries (L12):** `AccountService` has never depended on anything in `authn`
  (`LockoutService`, etc.) — the dependency direction established across T12/T13 is strictly
  `authn → account`, never the reverse. If T14's orchestration needs both `AccountService`-owned
  audit conventions and `LockoutService.resetLockout`, the calling code (likely the controller
  itself, or a new method) needs to reach both without inverting this direction — `AccountService`
  gaining a `LockoutService` dependency would create the codebase's first module-dependency cycle
  (`authn` already depends on `account`).

## 4. Testing conventions

- **Unit tests:** plain JUnit 5 + Mockito, no Spring context — `AdminAccountControllerTest.java`
  already exists (confirmed), mocks only `AccountService` today; will need updating if T14's
  endpoint also needs `LockoutService` injected into the controller.
- **Integration tests:** `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`,
  unchanged convention. Given this task's small surface (one endpoint, no new persistence), a full
  Testcontainers test may or may not be warranted — a Phase 1/5 scoping question, not decided here.
- **ArchUnit:** `admin_controller_handlers_require_preauthorize` will directly check whatever new
  public method this task adds to `AdminAccountController`.

## 5. Known gaps / unknowns

- **I do not know** the exact orchestration shape for T14's endpoint — whether the controller
  calls `LockoutService.resetLockout(...)` directly (requiring a new constructor dependency on
  `AdminAccountController`) plus a separate audit call, or whether a new method is added to
  `AccountService` or elsewhere. This is the single most consequential open design point this
  phase surfaced, directly parallel to T12's own "which direction does the call go" question at
  its own Phase 0/1 — flagged the same way, not resolved here.
- **I do not know** how the response shape reconciles: `resetLockout` returns a `LockoutDecision`,
  but every sibling `AdminAccountController` method returns `AccountResponse`. Likely resolved by
  calling `accountService.getByUuid(accountUuid)` after the unlock to fetch a fresh
  `AccountResponse` (matching the existing pattern where every method returns the post-mutation
  state) — a Phase 1/2 design point, not decided here.
- **Confirmed, not a gap:** `contracts/api/auth.yaml` (listed in this task's header) still does
  not exist anywhere in the repo — same already-logged gap as every prior task in this chain
  (T11-T13). This task does not depend on it for anything beyond documentation completeness.
- **Confirmed, not a gap:** the task statement's literal path (`POST
  /admin/accounts/{id}/unlock`) uses `{id}` while `requirements.md` R20's own text and every
  existing sibling method in `AdminAccountController` use `{accountUuid}` consistently. Not a
  genuine ambiguity — `{accountUuid}` is unambiguously the established, correct path-variable name
  to match; `{id}` is just the task statement's looser paraphrase, same category as `tasks.md`'s
  other short-form summaries throughout this spec package.
- **Confirmed, not a gap:** no Flyway migration needed — `lockout_state`/`accounts` schema is
  unchanged, all persistence this task needs already exists (T01/T12).
