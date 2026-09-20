## Context

See `proposal.md` — Why. `services/auth` is already an issuer and already provisions a
`crypto-service` client; `SecurityChainsConfig` there shows the house pattern for a resource
server with a scope-aware authority mapping.

This service is unusual in the estate: it holds the only IAM role permitted to call `kms:Sign` on
the attestation key (§8). That raises the cost of getting authentication wrong here above the
cost elsewhere.

## Goals / Non-Goals

**Goals:**

- No unauthenticated caller reaches anything that spends provider budget or changes state.
- A deployment cannot run with authentication effectively off.
- Probes keep working, so adding security does not break orchestration.

**Non-Goals:**

- Deciding who owns a watch. That needs the caller identity model settled.
- Provisioning the scope on the issuer — another module's change.

## Decisions

**Require a scope, not merely a valid token.** Every service in the estate validates against the
same issuer, so a token minted for the notification service would otherwise open watch
registration. A scope is the difference between "we know who you are" and "you may do this."

**Fail startup without an issuer outside local development, defaulting to required.** Mirrors
D-011, where auth refuses to boot without real key material rather than minting an ephemeral dev
key. The failure mode being guarded against is identical: a deployment that looks healthy while
silently having no security. Defaulting the flag to *required* means disabling it is a visible,
deliberate act in a diff, not an omission.

**Probes open, everything else closed.** Liveness and readiness carry no configuration and must be
reachable for orchestration to work. The chain-configuration health detail is a different matter —
it names chains and provider counts — which is why health details remain suppressed.

**403 stays distinguishable from 401.** Collapsing them would hide the most common real
misconfiguration, a client whose scope was never provisioned, behind the symptom of a bad token.
The distinction leaks that the token was valid, which is acceptable: the caller already knows,
having presented it.

**Rejections disclose nothing about resources.** An unauthenticated request for a watch identifier
answers identically whether or not that watch exists, so the endpoint cannot be used to probe for
live invoices.

## Risks / Trade-offs

- **Payment cannot call this service until auth provisions the scope** → Deliberate. The failure
  is a 403 at integration time, which is loud and diagnosable, rather than an endpoint quietly
  open to anyone. Worth flagging to whoever owns auth before payment starts integrating.
- **Local development needs a running issuer, or the local exemption** → Mitigated by the flag
  defaulting to required only outside local. The exemption is the same trade auth already made.
- **One scope is coarse** → It gates the whole internal surface. As that surface grows —
  attestation especially — this needs revisiting, and `POST /attest` should almost certainly not
  share a scope with watch registration.

## Migration Plan

The endpoint is currently open, so this is a tightening. No caller exists yet — payment has not
been built — so nothing breaks. Sequence matters for the estate: auth must provision the scope
before payment integrates, or payment's first call fails.

Rollback is disabling the requirement flag, which is exactly the act the default is designed to
make visible.

## Open Questions

- Whether `POST /attest` should require a distinct, more tightly held scope than watch
  registration. It almost certainly should, given it is the sole path to `kms:Sign` — but the
  endpoint does not exist yet, and the answer does not change anything in this change.
