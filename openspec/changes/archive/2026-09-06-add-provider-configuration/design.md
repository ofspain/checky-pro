## Context

See `proposal.md` — Why. `chain/quorum-reads` already requires a threshold of at least two and
refuses to construct below it; this change decides where the providers satisfying that threshold
come from.

`services/auth` establishes the conventions: `themistra.<service>.*` properties, environment
variables with local-development defaults, and no secret ever written into a committed file
(D-010).

## Goals / Non-Goals

**Goals:**

- One declaration point for what chains exist and who serves them.
- A misconfiguration that could weaken §6.1 fails at deploy time, not during a verification.
- A shape that accommodates Tron and the sidecar chains without rework.

**Non-Goals:**

- Provider health, degradation, retry, and rate limiting. Those follow the traffic, which arrives
  with the watcher.
- Anything that opens a connection at startup.

## Decisions

**Configuration declares providers; it does not prove them reachable.** Startup validates shape —
enough providers, unique labels, a known namespace, resolvable endpoints — and stops there. It
deliberately does not probe endpoints. A provider being momentarily down is exactly the condition
§6.1 tolerates by design, so failing to boot on it would convert a survivable condition into an
outage. Reachability is the health checker's job. Alternative considered: probe every provider at
startup and refuse to boot below threshold — rejected because it makes a restart during a provider
incident unrecoverable.

**Too few providers is a startup failure, not a warning.** A service that starts with one provider
for a chain will, under load and out of anyone's attention, attest on a single provider's word —
the exact failure §1 names as the core threat. A log line is not sufficient protection against
that. This is the one shape error worth refusing to run for.

**A namespace with no adapter is also a startup failure.** Declaring a Tron chain before a Tron
adapter exists would otherwise produce a chain that registers watches and observes nothing, which
looks like a payment never arriving.

**No chains at all is valid.** A service not yet given work is not misconfigured, and requiring a
chain would make the first deployment awkward for no safety gain.

**The registry returns adapters, not observations.** It is a lookup, not a facade over the quorum
layer, so nothing acquires a second way to reach a provider.

## Risks / Trade-offs

- **Not probing at startup means a fully-broken chain boots healthy** → Accepted deliberately, for
  the restart-during-incident reason above. Mitigated by health reporting provider counts now, and
  by the provider-health change reporting reachability next.
- **Three commercially independent providers is a real operational cost** → It is the product.
  §6.1 already accepted it; this change only makes a deployment that ignores it fail loudly.
- **Public endpoints satisfy the shape but not the independence requirement** → Nothing here can
  detect that two configured endpoints are the same upstream behind different names. It is a
  procurement question, and worth stating in deployment documentation rather than pretending the
  service can enforce it.

## Migration Plan

Additive. No existing deployment has chains configured, so nothing breaks. The first deployment to
declare a chain must supply provider endpoints or fail to start, which is the intended behaviour.
