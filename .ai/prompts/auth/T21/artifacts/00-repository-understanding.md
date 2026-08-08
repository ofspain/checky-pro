# auth · T21 · Phase 0 — Repository Understanding

## 1. Architecture summary

Unchanged from T20's understanding of this service: Spring Boot 3.5.4 / Java 21, package-by-feature under `com.themistra.auth`, PostgreSQL + Flyway (`V1`–`V5`), Kafka outbox, Spring Authorization Server for OIDC/OAuth2, ArchUnit-enforced module boundaries. Nothing about the service's shape changed between T20 and T21 — see `spec/auth-service/agents.md` and T20's own `00-repository-understanding.md` for the full picture; not restated here.

The one directly relevant piece: **`token.TokenClaimsCustomizer`**, an `OAuth2TokenCustomizer<JwtEncodingContext>`, is the single place that sets `amr`/`acr`/`roles`/`email_verified` on every issued access token, branching on `AuthorizationGrantType`.

## 2. Existing code this task touches — and the central finding

**Already fully implemented (by T20, not this task):** `TokenClaimsCustomizer.customizeAccessToken` already branches correctly for:
- `CLIENT_CREDENTIALS` grant → `amr: ["client_secret"]` (pre-existing, predates T20).
- Interactive grants (`AUTHORIZATION_CODE`/`REFRESH_TOKEN`) → conditional on a synthetic `OTP_VERIFIED` granted authority (`TotpAuthenticationProvider.OTP_VERIFIED_AUTHORITY`) carried on the principal: `amr: ["pwd","otp"]`/`acr: urn:themistra:acr:otp` when MFA was used, `amr: ["pwd"]`/`acr: urn:themistra:acr:pwd` otherwise — preserved correctly across refresh, with no grant-type branching needed for that part.

This is R26 and R27, verbatim — both already implemented, both already covered by tests (`TokenClaimsCustomizerTest.shouldIssueTokenWithOtpAmrAndAcrAfterMfa`, and `...interactiveTokenGetsRolesAmrAcrAndEmailVerified_noEmailOrName` covering R27/`shouldIssueTokenWithPwdAmrWhenMfaNotRequired`), landed and committed as part of T20 (see T20's Phase 4 resolution #3 — "T20 fully owns `TokenClaimsCustomizer`'s `amr`/`acr` output for the interactive... cases" — and T20's Phase 12 traceability matrix, which recorded this exact scope absorption).

**Does not exist at all — and this is the actual open scope of T21:** the third branch this task's header names, the **api-key grant** (R31), has no supporting code anywhere in the service:
- `com.themistra.auth.apikey` — the package exists (`package-info.java` only) but is otherwise **empty**: no `ApiKey` entity/repository, no `ApiKeyService`, no `ApiKeyTokenIssuer`, no controller.
- `POST /api-keys/token` — does not exist. Not in `PublicEndpoints`, not implemented anywhere.
- No mechanism exists for a request to reach SAS's token-issuance pipeline via an API key at all — `TokenClaimsCustomizer` has nothing to branch on for this case, because nothing produces that kind of `JwtEncodingContext` yet.
- `api_keys` table **does** already exist (V1 baseline migration, unused) — schema is ready, application code is not.

`tasks.md` sequences the API-key work as items 23–27 (`ApiKey` entity/repository, key service, exchange endpoint, CRUD controller, integration tests) — all **after** item 21 (this task) in the file's own ordering. R31 cannot be satisfied by changes to `TokenClaimsCustomizer` alone; it requires at minimum task 25's exchange endpoint and task 24's `ApiKeyTokenIssuer` to exist first, per L8's own wording ("Exchange endpoint `POST /api-keys/token` issues a... JWT... via the existing `TokenClaimsCustomizer` path" — `TokenClaimsCustomizer` is the *downstream* consumer of that flow, not something that can create the flow itself).

## 3. Established patterns to follow

Same as T20 documented: `OAuth2TokenCustomizer` branches on `AuthorizationGrantType`; principal identity for interactive grants is the account UUID via `Authentication.getName()`; roles are always resolved fresh from `RoleService` at issuance time, never cached. If/when an api-key branch is added, `AuthorizationGrantType` doesn't have a built-in constant for "api key exchange" — SAS's grant-type model is extensible via `new AuthorizationGrantType("...")`, but no such custom grant type exists anywhere in this codebase today; how the exchange endpoint would actually get a JWT issued through SAS's pipeline (vs. building one directly, bypassing `OAuth2TokenCustomizer` entirely) is an open design question for whichever task actually builds the exchange endpoint, not something already established to be extended here.

## 4. Testing conventions

Unchanged from T20: plain JUnit + Mockito for `TokenClaimsCustomizerTest` (fixed dependencies, no Spring context), Testcontainers for anything touching real Postgres/Kafka/HTTP. No test precedent exists yet for an api-key grant, for the same reason no production code does.

## 5. Known gaps / unknowns

- **I do not know whether T21, as scoped by its own header (R26, R27, R31), is actually executable right now.** Two of its three named requirements (R26, R27) are already done — this task's literal remaining work is R31 alone, and R31 has a hard, unmet dependency on tasks 23–26 (the entire `apikey` module) that this task's own scope explicitly excludes touching (`tasks.md` sequences them as separate, later tasks). I don't know whether the intent is: (a) T21 should be treated as effectively already complete / a no-op verification pass, (b) T21 should be deferred until tasks 23–26 land, or (c) T21's scope should be reinterpreted to include enough of the api-key plumbing to make R31 meaningful. This isn't a design decision I'm positioned to make — flagging it here per this phase's own instruction rather than guessing.
- Everything else about this task depends on that question being resolved first; Phase 1 (specification extraction) can proceed for R26/R27 (trivially — confirming what already exists) but cannot meaningfully extract R31's implementation requirements without knowing whether the api-key module is in scope.

## Resolution (human decision, 2026-08-08)

Option 1 selected: **T21 is treated as complete.** R26 and R27 shipped as part of T20 (implemented and tested — see above). R31 is explicitly deferred, not implemented here, not silently dropped — it depends on the `apikey` module (`tasks.md` items 23–26), which does not exist yet. No code changes made under T21. This task's pipeline stops here rather than running Phases 1–13 against no actual remaining implementation work; when tasks 23–26 land, R31's `TokenClaimsCustomizer` branch (and the accompanying `ApiKeyTokenIssuer` wiring) should be revisited then, either as part of task 24/25 directly or as a small follow-up once the exchange endpoint exists to give it something real to branch on.

