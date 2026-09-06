## 1. Persistence

- [x] 1.1 Add JPA, Flyway, PostgreSQL driver, and Testcontainers dependencies to the module,
      matching `services/auth`. Verify the module still compiles and the existing suite passes.
- [x] 1.2 Write `V1__chain_baseline_schema.sql` creating the `chain` schema and a `watches` table
      with a unique constraint on the caller reference and an index for active-watch lookup by
      chain and address. Verify it contains no function, procedure, or trigger.
- [x] 1.3 Configure Flyway to own the `chain` schema and Hibernate to validate rather than
      generate, per D-005. Verify startup fails if the mapping and schema disagree.
- [ ] 1.4 Add `compose.local.yaml` for a local Postgres, matching auth's. Verify the service starts
      against it.

## 2. Domain

- [x] 2.1 Add the watch entity with a closed status vocabulary of ACTIVE, EXPIRED, CANCELLED and
      SATISFIED. Verify an invalid status is rejected by the check constraint.
- [x] 2.2 Store addresses normalised and keep the checksummed form for output. Verify a watch
      registered with a checksummed address matches one registered lowercase.
- [x] 2.3 Implement expiry evaluated on read, with a single query path for active watches. Verify
      a watch one second past expiry is excluded and one second before is included.

## 3. Registration

- [x] 3.1 Reject a watch naming an unconfigured chain, using the adapter registry as the authority.
      Verify the rejection names the chain.
- [x] 3.2 Validate addresses against the chain namespace's address form. Verify a malformed EVM
      address and a wrong-family address are both rejected.
- [x] 3.3 Reject a non-positive amount and an expiry in the past. Verify each independently.
- [x] 3.4 Make registration idempotent on the caller reference, returning the existing watch.
      Verify two identical registrations yield one row and the same identifier.
- [x] 3.5 Reject a reused reference carrying different terms as a conflict. Verify neither the
      original nor the new terms are silently applied.

## 4. API

- [x] 4.1 Add the internal controller: register, fetch by identifier, cancel. Verify each against
      the persisted state.
- [x] 4.2 Make cancellation idempotent, and distinguish an unknown watch as not found. Verify both.
- [x] 4.3 Return validation failures as client errors carrying which field was wrong, without
      echoing anything the caller did not send.
- [ ] 4.4 Add an integration test over a real Postgres via Testcontainers covering register,
      retry, conflict, cancel, and expiry. Verify it runs against the actual migration.

## 5. Reconcile

- [ ] 5.1 Confirm the request shape with whoever owns `services/payment` before treating it as
      settled. Verify by their agreement recorded on the PR.
- [ ] 5.2 Record the unauthenticated endpoint in `SECURITY-THREAT-MODEL.md` per §6.7, or state why
      no update is needed. Verify the follow-up change for the resource server is proposed before
      deployment.
- [x] 5.3 Run the full module suite and confirm the existing 39 tests still pass.

## 6. Blocked

- [ ] 6.1 **Integration test does not run locally.** Docker Desktop 29.6.1 runs containers fine,
      but answers Testcontainers' `/info` probe with an empty HTTP 400 on both the `docker_engine`
      and `dockerDesktopLinuxEngine` pipes, so no Docker environment is detected. Overriding
      docker-java to 3.7.1 did not help; Testcontainers 2.x reorganises its artifacts and is a
      migration rather than a bump. `mvn verify` is therefore red on this machine while
      `mvn test` is green. Resolve by confirming it passes on CI's Linux runner, or by pinning a
      working local Docker Desktop version.
- [ ] 6.2 `compose.local.yaml` for local Postgres — deferred with 6.1, since it serves the same
      local-development path that is currently blocked.
