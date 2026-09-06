## Context

See `proposal.md` — Why. `chain/provider-configuration` already decides which chains exist, so
this change validates a watch against that registry rather than inventing its own notion of a
supported chain.

`services/auth` sets the persistence conventions: Flyway owns DDL, `ddl-auto=validate`, one
logical schema per service, no business logic in the database (D-005).

## Goals / Non-Goals

**Goals:**

- Payment can register intent and get a stable identifier back.
- A watch that could never match is rejected at registration, not discovered later as a payment
  that never arrived.
- Watch state survives restart, since the watcher layer will depend on it.

**Non-Goals:**

- Acting on a watch. Nothing observes yet.
- Authentication. Called out in the proposal as a required follow-up.

## Decisions

**The caller supplies the idempotency reference, not us.** Payment already has an invoice
identifier; asking it to invent a second one, or asking it to remember ours across a timeout it
never saw the response to, both create failure modes. A retry that contradicts the original is a
conflict rather than an overwrite: silently accepting new terms under an old reference would let a
watch drift from the invoice it represents.

**Expiry is mandatory with no default.** A watch costs provider calls for as long as it lives, and
an unpaid invoice is the common case. Refusing to invent a default forces the caller to state the
invoice's lifetime, which only it knows.

**Addresses are stored normalised and compared normalised.** EIP-55 checksumming is a display and
transport concern (§6.3); matching an observation against a watch must not depend on which casing
the caller happened to send. The checksummed form is what we publish, the normalised form is what
we index.

**Status is a small closed vocabulary, admitting states this change does not implement.**
`ACTIVE`, `EXPIRED`, `CANCELLED`, `SATISFIED` — the last unused until something can observe a
payment. Admitting it now avoids a migration to add a value, and costs nothing.

**Expiry is evaluated on read, not by a sweeper.** A background job flipping rows to `EXPIRED`
would be a second source of truth that can lag; "active" means active-and-unexpired at the moment
of asking. A sweeper may later archive old rows, but it will not be what decides the answer.

## Risks / Trade-offs

- **An unauthenticated inbound endpoint** → The largest risk in this change. Anyone able to reach
  it can make the service spend provider calls. Mitigated only by network placement until the
  resource-server change lands, which is why the proposal names it a prerequisite for deployment
  rather than a nice-to-have.
- **The request shape is a cross-team contract** → Agreed with nobody so far. Getting it wrong
  costs a coordinated change later, the same as the event contract. Worth walking through with
  payment's owner before treating it as settled.
- **Expiry-on-read means a query must always consider time** → A repository method that forgets
  the clause silently resurrects expired watches. Mitigated by keeping a single query path for
  active watches and testing the boundary directly.
- **No allowlist check on the token contract yet** → A watch can name any contract, including a
  counterfeit one. §6.3 exists precisely for this; until that change lands, a watch's token is
  taken on trust from the caller.

## Migration Plan

Additive. The service currently has no database, so the baseline migration creates the `chain`
schema from nothing. Rollback is dropping the schema; no data exists to preserve.

Deployment gains a database prerequisite, and the ingress must refuse `/internal/**` from outside
the cluster until authentication lands.
