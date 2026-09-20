<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T39 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: record O2/O3/O4/O5 resolutions in auth-decisions.md (T39)
```

## Commit message

```
auth: record O2/O3/O4/O5 resolutions in auth-decisions.md (T39)

The decision log (D-001-D-025) stopped roughly at the MFA implementation
task - everything since (rate limiting, API keys, contracts, ArchUnit
hardening, end-to-end verification) went undocumented there, even though
several of design.md's still-open items (O2-O5) were quietly resolved
along the way. Adds four entries:

- D-026 (O2): the rate-limit thresholds actually shipped (10/5/30 per
  minute), with the deliberate MFA-folded-into-login-bucket design, the
  refresh_token-only grant scoping, and the per-token (not per-account)
  keying for password-reset/refresh - all independently re-verified
  against source, not copied from memory.
- D-027 (O3): recorded honestly as still unresolved - session device
  labels are null in production today; none of the three options
  design.md names was ever actually chosen. A decision log's job is to
  be accurate, not to look complete; fabricating a resolution here would
  have been worse than leaving it open.
- D-028 (O4): the default Spring Security login form, no custom template
  - correctly cited against the real SecurityChainsConfig configuration,
  not an initially-guessed one.
- D-029 (O5): SHA-256 recovery-code hashing, matching the spec's own
  suggested default.

Q1 needed no new entry - already covered by D-025.

Deliberately scoped to just these four items plus Q1's confirmation, not
a full retrospective of every decision made across the many tasks since
the log's last entry - that would be its own, separately-scoped task.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Documentation only**
- `services/auth/docs/architecture/auth-decisions.md` (modified — 4 new entries, D-026 through
  D-029, appended after D-025)

No production or test code changed. `spec/auth-service/` untouched.

## Summary

Brings the service's durable decision log current for four of `design.md`'s five still-open items,
each verified against actual shipped behavior rather than assumed from the spec's original framing —
and caught two real inaccuracies (grant-type scoping, per-token keying) along the way that an
independent review round surfaced. The fifth item (O3, session device labels) is recorded honestly
as never having been decided, which is the correct and complete answer for it, not a gap in this
task's own work.

## Testing performed

Not applicable — documentation-only task, no code or test surface. Verification was source
cross-referencing: every citation in all four new entries independently checked against current
`services/auth` source at Phases 6, 9, and 11, not carried forward unverified between phases.

## Specification references

- **Task:** T39 — Update `auth-decisions.md` (`spec/auth-service/tasks.md`, task 39)
- **Requirements:** none scoped (process/documentation task)
- **LOCKED decisions:** none scoped
- **Named tests (`package.md` §8):** none scoped to this task

## Known, logged, out-of-scope follow-ups

1. **O3 (session device-label source) remains genuinely unresolved** — `deviceLabel` is `null` on
   every real token issuance. A future task implementing one of the three named options (most likely
   a `User-Agent` hash) would close this.
2. **A broader retrospective covering tasks #17-#38's other undocumented decisions** (MFA completion
   details, API-key design trade-offs, contract/ArchUnit hardening choices, this session's own
   T36-T38 human-gate decisions) was explicitly scoped out of T39 — a candidate for its own,
   separately-scoped documentation task if wanted.

---

**Phase 13 complete — PR preparation written. T39 is ready for merge.**
