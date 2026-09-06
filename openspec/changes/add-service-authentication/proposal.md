## Why

`add-watch-registration` gave this service its first inbound endpoint, and it accepts anyone.
ARCHITECTURE §8 is unambiguous — "JWT validation lives in each service (OAuth2 resource server) —
zero trust, required regardless" — and §3.1 makes the same point about the edge being routing, not
a security boundary.

The practical exposure is not abstract: anyone who can reach `/internal/watches` can make this
service spend provider calls, and later can register watches whose observations feed a payment
state machine. This service also holds the only IAM role permitted to call `kms:Sign` on the
attestation key (§8), so it is the last service in the estate that should accept unauthenticated
traffic.

`services/auth` already issues tokens and already provisions a `crypto-service` client, so the
issuer side of this exists. Only the resource-server side is missing.

## What Changes

- The service becomes an OAuth2 resource server, validating bearer tokens against the auth
  service's issuer and its published JWKS.
- `/internal/**` requires an authenticated caller holding a scope naming this capability. A valid
  token for some other purpose is not sufficient — a token is not an authorisation.
- Actuator liveness and readiness stay open, so Kubernetes probes work without credentials, while
  everything else that could disclose configuration requires authentication.
- The service **fails to start in any non-local profile without an issuer configured**, so a
  deployment cannot accidentally run with security switched off — the same posture auth takes with
  its signing keys (D-011).

## Non-goals

- **Provisioning the scope on the issuer.** `services/auth` must register the scope for the
  payment client; that is a change in another module and belongs with whoever owns it. This change
  defines what this service requires, which is the half it can be responsible for.
- **Fine-grained authorisation.** One scope gates watch registration. Per-watch ownership — should
  payment be able to cancel a watch it did not register — is a real question, but it needs the
  caller identity model settled first and would be guesswork now.
- **Mutual TLS or network policy.** Defence in depth at the network layer is infrastructure (§8),
  and does not remove the requirement for the service to authenticate its callers.
- **Rate limiting.** Belongs at the edge (§3.1).

## Capabilities

### New Capabilities

- `chain/service-authentication`: Who may call this service, what a caller must present, and what
  the service does when it is deployed without the means to check.

### Modified Capabilities

- `chain/watch-registration`: Registration now requires an authenticated caller holding the
  required scope. The registration behaviour itself is unchanged; what changes is that an
  unauthenticated caller no longer reaches it.

## Impact

- **New dependency:** `spring-boot-starter-oauth2-resource-server`, matching `services/auth`.
- **New configuration:** issuer URI, and a flag requiring it outside local development.
- **Cross-service prerequisite:** `services/auth` must provision the scope for the payment client
  before payment can call this service. Until it does, payment gets 403 rather than 200 — a loud,
  diagnosable failure rather than a silent one.
- **Deployment:** removes the standing requirement that `/internal/**` be unreachable from outside
  the cluster. That network control remains worth having, but is no longer the only thing standing
  between the internet and this service.
- **`SECURITY-THREAT-MODEL.md`** should record the closure, per §6.7. That file is
  CODEOWNERS-protected, so the update is proposed rather than applied here.
