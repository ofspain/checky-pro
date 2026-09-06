## Why

The crypto service can now reach chains and establish facts by quorum, but has no idea what to
look for. `services/payment` is meant to register a watch over REST when an invoice is created
(ARCHITECTURE §4, §7), and no such endpoint exists.

This is also where the service acquires the `chain` schema it owns (§3.4). Everything after it —
watchers, the observation log, reorg handling — needs somewhere to keep state that survives a
restart.

## What Changes

- An internal REST endpoint accepts a watch: which chain, which recipient address, which token
  contract, the expected amount, and when the watch expires.
- The `chain` Postgres schema and its first Flyway migration, following D-005: Flyway owns the
  DDL, Hibernate only validates against it, and no business logic lives in the database.
- Registration is **idempotent on the caller's own reference**. Payment retrying a call after a
  timeout must not produce a second watch over the same invoice.
- **Every watch expires.** An invoice nobody pays would otherwise be watched forever, and the
  cost of watching is paid per provider call.
- Addresses are validated for the chain's namespace on the way in (§6.3), so a malformed address
  is rejected at registration rather than producing a watch that can never match.

## Non-goals

- **Authentication on the endpoint. This is a gap, not an oversight.** §8 requires JWT validation
  in every service — "zero trust, required regardless" — and `services/auth` already provisions a
  `crypto-service` client. Wiring the OAuth2 resource server is its own change and **must land
  before this service is deployed anywhere reachable**. Until then the endpoint is unauthenticated
  and belongs behind an ingress rule that refuses `/internal/**` from outside the cluster.
- **Actually watching anything.** Registration records intent. The watcher layer that acts on it
  is the next change; until it exists, a registered watch sits there.
- **The observation log.** §6.1's verbatim provider record is a separate change, and arrives with
  the traffic that produces observations.
- **Marking a watch satisfied.** Nothing yet observes a payment, so nothing can complete a watch.
  The status vocabulary admits it without this change implementing it.
- **Canonical token allowlist checks (§6.3).** A watch names a token contract; validating it
  against the signed allowlist is that change's job.

## Capabilities

### New Capabilities

- `chain/watch-registration`: How another service tells the crypto service what to watch for, what
  makes a watch valid, how a watch ends, and what a retried registration does.

### Modified Capabilities

None.

## Impact

- **New:** the `chain` Postgres schema, its baseline migration, a watch entity and repository, a
  service, and an internal REST controller.
- **New dependencies** in `services/crypto`: JPA, Flyway, PostgreSQL driver, and Testcontainers for
  integration tests — matching `services/auth`.
- **Local development** gains a `compose.local.yaml` for Postgres, as auth has.
- **Payment** gains a contract it can code against. The request shape is the coupling point and
  should be agreed with whoever owns that service before it is treated as settled.
- **Deployment prerequisite**: a database. Until now the service needed none.
- **Security**: the service exposes its first inbound endpoint while having no authentication.
  Recorded above as a non-goal with a required follow-up, and worth raising in
  `SECURITY-THREAT-MODEL.md` per §6.7.
