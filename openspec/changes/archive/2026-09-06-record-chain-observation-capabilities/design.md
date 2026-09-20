## Context

See `proposal.md` — Why. This change records existing, tested behaviour; the design decisions it
describes were made when the code was written, not here.

## Goals / Non-Goals

**Goals:**

- A living spec that matches the service as built, so later changes deliver deltas rather than
  starting from a blank page.

**Non-Goals:**

- Re-deciding anything. Where a decision looks wrong, it belongs in a later change with its own
  proposal, not in a retroactive edit here.

## Decisions

**Two capabilities, not one.** Quorum is a decision procedure over answers; EVM observation is
about reading one chain. They change for different reasons — adding Tron touches the second and
not the first — so splitting them keeps later deltas narrow.

**Specs describe behaviour, not classes.** No requirement names a type or method, so a refactor
does not invalidate the spec. The test suite is the link between the two.

**Volatile fields are named in the spec, not enumerated.** The quorum spec says fields that
legitimately change between replies are excluded from comparison, and gives confirmation depth as
the scenario. Naming the principle rather than the field list means adding another volatile field
later is not a spec change.

## Risks / Trade-offs

- **A retroactive spec can be written to match a bug** → Every requirement here corresponds to a
  passing test that was written from ARCHITECTURE.md, not from the implementation. The known open
  question below is recorded rather than specified away.
- **Backfilling normalises building before specifying** → It should not become the pattern. The
  remaining crypto work — watchers, persistence, publishing, attestation — is unbuilt, and each
  should be proposed before it is written.

## Open Questions

- A provider that returns a well-formed empty result for every query is indistinguishable from one
  honestly reporting a transaction absent. Two such providers would reach an absence quorum that is
  false. This is observed behaviour of at least one public endpoint, not a hypothetical. The
  current specs record absence-as-a-vote as built; whether absence needs a stronger signal than an
  empty result is a real question for a later change, and touches ARCHITECTURE §6.1.
