## 1. Resource server

- [x] 1.1 Add `spring-boot-starter-oauth2-resource-server`, matching `services/auth`. Verify the
      module compiles and the existing suite passes.
- [x] 1.2 Add issuer configuration and a require-issuer flag defaulting to required outside local
      development, using the environment-variable pattern from auth. Verify no secret literal
      enters the file.
- [x] 1.3 Fail startup in a non-local profile when no issuer is configured. Verify the context
      fails, and verify local development still starts without one.

## 2. Authorisation

- [ ] 2.1 Require authentication on `/internal/**` and the required scope on top of it. Verify a
      valid token lacking the scope is forbidden while one carrying it succeeds.
- [ ] 2.2 Leave liveness and readiness open; require authentication for other management
      endpoints. Verify each independently.
- [ ] 2.3 Verify an unauthenticated request for a watch identifier answers identically whether or
      not that watch exists.
- [ ] 2.4 Verify 401 and 403 remain distinguishable, so a client whose scope was never provisioned
      is diagnosable.

## 3. Reconcile

- [ ] 3.1 Raise with whoever owns `services/auth` that the payment client needs the watch scope
      provisioned before payment integrates. Verify by their acknowledgement on the PR.
- [ ] 3.2 Propose the `SECURITY-THREAT-MODEL.md` update recording that the unauthenticated inbound
      endpoint is closed, per §6.7. The file is CODEOWNERS-protected, so this is proposed rather
      than applied.
- [x] 3.3 Update `add-watch-registration`'s open security note now that the gap is closed.
- [x] 3.4 Run the full module suite and confirm the existing 51 unit tests still pass.

## 4. Blocked

- [ ] 4.1 Tasks 2.1–2.4 are implemented and covered by `WatchApiSecurityIT`, but that test cannot
      run on this machine — the same Docker Desktop 29.6.1 / Testcontainers `/info` failure that
      blocks `WatchRegistrationIT` (see `add-watch-registration` task 6.1). The authorisation rules
      are therefore **written and unverified**. Resolve by running on CI's Linux runner.
