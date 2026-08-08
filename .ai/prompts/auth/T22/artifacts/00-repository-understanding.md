# auth · T22 · Phase 0 — Repository Understanding

## 1. Architecture summary

Unchanged from T20/T21's understanding — Spring Boot 3.5.4 / Java 21, Postgres + Flyway, Kafka outbox, Spring Authorization Server, ArchUnit-enforced boundaries. Not restated; see T20's `00-repository-understanding.md` for the full picture.

Directly relevant here: `token.SecurityChainsConfig`'s `authorizationServerChain` (`@Order(1)`) matches `/oauth2/**`, `/.well-known/**`, `/userinfo`; unauthenticated browser requests are redirected to `/login`. `token.RegisteredClientConfig`/`RegisteredClientSeeder` provision the OAuth2 clients at boot into `oauth2_registered_client` (Flyway V1 table). `token.AuthorizationServiceConfig` wires `JdbcOAuth2AuthorizationService` (decorated by `ReuseDetectingAuthorizationService`) as the durable store for in-flight and completed authorizations.

## 2. Existing code this task touches

**The central finding of this phase:** T22's task statement — "merchant without MFA cannot finish authorize flow," "correct code produces `amr: [pwd, otp]`" — asks for exactly the category of test this codebase's own architecture decisions have twice declined to write blind:

- `docs/architecture/auth-decisions.md` D-023 (written well before T20) explicitly named "a full MockMvc-driven OAuth2 Authorization Code + PKCE flow test against Spring Authorization Server's actual `/oauth2/authorize` → `/login` → `/oauth2/token` sequence" and rejected attempting it: "requires exact knowledge of SAS's session/CSRF/redirect behavior... which cannot be verified without running it... Writing speculative flow-orchestration code that looks thorough but is unverified would be worse than not writing it."
- T20 (Phase 10) hit the same fork and made the same call: R26/R27's `amr`/`acr` claims were verified directly at `TokenClaimsCustomizer` (the component actually responsible for them), explicitly choosing *not* to chase the full `/oauth2/authorize`→`/oauth2/token` round trip, citing D-023 by name.

T22 is not redundant with either of those — read charitably, it's the task where the team apparently intended this deferred, higher-risk work to actually get built, as its own dedicated deliverable rather than bundled into an implementation task. That reading matters for how this task should be scoped and reviewed: this is exactly the kind of test Phase 3 (Kimi design-challenge) and Phase 8 (independent review) need to scrutinize hard, since — same as D-023 and T20 before it — nothing about it can be run in this environment (no Docker, confirmed the same limitation every prior Testcontainers test in this pipeline has carried).

**Already exists, directly reusable:**
- `authn/SasLoginIntegrationTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` (no MockMvc precedent anywhere in this module), CSRF-token scraping, cookie propagation, redirect-following disabled. T20 already extended this file with `merchantWithoutMfaEnrollmentCannotLogIn` and `merchantWithConfirmedEnrollmentRequiresCorrectTotpOrRecoveryCode` — but both stop at `/login`, asserting only that the *login* redirect succeeds or fails. Neither proceeds into `/oauth2/authorize`, so neither actually proves "cannot finish the *authorize* flow" (T22's literal wording) or inspects an issued token's claims.
- `token/RegisteredClientSeeder.java` — the SPA client (`client_id = checky-spa`, from `themistra.auth.clients.spa.client-id`) is registered as: `ClientAuthenticationMethod.NONE` (public client, no secret), `AuthorizationGrantType.AUTHORIZATION_CODE` + `REFRESH_TOKEN`, `requireProofKey(true)` (**PKCE is mandatory** for this client), `requireAuthorizationConsent(false)` (**no consent-screen step** — one less round trip to handle), scopes `openid`/`profile`/`email`. Redirect URI: `themistra.auth.clients.spa.redirect-uris[0]`, defaulting to `http://localhost:5173/auth/callback` (env-overridable via `SPA_REDIRECT_URI`, unset in this test context so the default applies).
- `com.nimbusds:nimbus-jose-jwt` (currently resolves to 10.9 in this build) is already on the classpath transitively via `spring-security-oauth2-jose` — available for parsing the issued JWT's claims in a test (`JWTParser.parse(token).getJWTClaimsSet()`) without needing to separately add a JWT library.
- `mfa/MfaService` (public, existing) is how T20's tests seed a confirmed TOTP enrollment — no self-service HTTP endpoint exists (T19 gap, unchanged, see T20/T21 memory).

**Does not exist:**
- No test anywhere in this module has ever driven `/oauth2/authorize` or `/oauth2/token`. There is no established pattern for PKCE code-verifier/code-challenge generation, authorization-code extraction from a redirect, or token-response parsing in this codebase's test suite — T22 would be the first.

## 3. Established patterns to follow

`TestRestTemplate` with redirect-following disabled (`SasLoginIntegrationTest`'s `SimpleClientHttpRequestFactory` override) is the only viable pattern for a multi-hop redirect flow given no MockMvc precedent exists. CSRF-token scraping from a GET response body, manual `Set-Cookie`/`Cookie` header propagation between requests — same as `SasLoginIntegrationTest.attemptLogin`, which does not currently expose the session cookie it receives (private, discarded after the method returns) — a genuine gap this task's tests will need to close, since `/oauth2/authorize` needs to be called *with* the session `/login` just established.

## 4. Testing conventions

Testcontainers (Postgres + Kafka) per `TestcontainersConfiguration`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`. This task's own statement names "Testcontainers tests" explicitly — no unit-test-only interpretation is available here, unlike T20/T21's requirements, since the whole point is proving the real, wired-together SAS pipeline behaves as claimed.

## 5. Known gaps / unknowns

- **I do not know whether writing this test is something this team's own established risk posture (D-023) actually wants attempted now, or whether T22's task statement was written before that risk was fully appreciated and should be revisited the same way T21's was.** Unlike T21 (where the blocker was a missing dependency), here the blocker is a *deliberate, twice-repeated engineering judgment call* by this same codebase against exactly this category of test. I'm not positioned to overrule that call by just writing the test anyway — flagging it for Phase 1/4 rather than proceeding on the assumption it's simply been green-lit by task-list inclusion.
- I do not know the exact session-cookie name SAS/Spring Security uses in this deployment (`JSESSIONID` by default, but not confirmed against this specific config) — needed to correctly carry the `/login` session into `/oauth2/authorize`.
- I do not know whether `/oauth2/authorize`'s success redirect, for a PKCE public client with consent disabled, is a single 302 straight to the client redirect URI with `?code=...&state=...`, or whether some intermediate step exists — this is exactly the "cannot be verified without running it" uncertainty D-023 named, now scoped to a specific, checkable claim rather than a vague risk.
