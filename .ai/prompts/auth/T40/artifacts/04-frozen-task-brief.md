<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T40 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 8 Phase 3 (Kimi) findings verified against source before disposition.

| # | Finding | Disposition |
|---|---|---|
| 1 | Q5 is a real R43 defect, not merely an unresolved question | **Verified accurate**, confirmed with additional precision: `adminUnlock` (`AccountService.java:345-355`) wraps the plain `unlock(accountUuid)` call and conditionally fires `publishLifecycleEvent`/`recordAudit` only at that layer — the plain `lock`/`unlock` methods (lines 316-330) never do. **Human-gate decision: fix now** (see below). |
| 2 | Q3 only partially resolved (scope confirmed, max-key-limit not) | Accepted. §11 text below records both halves precisely — no limit implemented, `merchant.api` the only scope — rather than a blanket "resolved." |
| 3 | Q4's boundary with Notification Service unclear | Accepted. Confirmed `EmailRequestedEventPayload` carries no URL/link field — link construction is entirely the Notification Service's own responsibility. Recorded as out-of-scope for this spec, not fabricated as answered. |
| 4 | AC2's test-suite precondition not literally met; needs explicit waiver criteria | Accepted. **Human-gate decision: accept Groups A/B as named exceptions** (see below), matching T37's own established precedent. |
| 5 | The R43 gap may be cheap enough to fix within T40 | Accepted — **same decision as Finding 1**: fix now, reusing `AccountService`'s own existing `recordAudit`/`publishLifecycleEvent` private helpers (already proven correct via `adminUnlock`'s usage) — no new mechanism, two method bodies. |
| 6 | Q2's citation should be precise | Accepted. §11 text below cites D-026 and the exact values. |
| 7 | Status bump with known gaps shouldn't set an unreviewed precedent | Accepted. Every accepted gap (Groups A/B) is named in `package.md` §11, this artifact, and will be named again in the Phase 13 commit message — not recorded in only one place. |
| 8 | Q6's repo-root `agents.md` follow-up still open, should stay tracked | Accepted. §11 text below preserves the non-blocking follow-up note rather than dropping it. |

## Frozen brief (Phase 2 TIB, as amended)

### Task

Fix the R43 lock/unlock audit gap (Q5); update `package.md` §11 to accurately reflect Q2 (resolved),
Q3 (partially resolved, precisely stated), Q4 (out of scope), Q5 (now resolved by the fix), Q6
(unchanged); bump `package.md`'s Status to `READY FOR IMPL` and Version to `0.2`, with Groups A/B
explicitly named as accepted test-suite exceptions.

### Scope (revised)

**In**: the small `AccountService.lock`/`unlock` fix; `package.md` header + §11 edit; the status
bump itself.

**Out**: Q3's max-active-keys limit (no operational need demonstrated, deferred); Q4 (out of this
spec's own boundary); fixing Groups A/B (unchanged, already-logged environmental/unconfirmed issues).

### Files to Create

None.

### Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `lock(UUID)` and
  `unlock(UUID)` gain `recordAudit`/`publishLifecycleEvent` calls, mirroring `adminUnlock`'s existing
  pattern.
- `spec/auth-service/package.md` — header (Status/Version) + §11 (Q2, Q3, Q4, Q5 text).

### Files NOT to Modify

Every other production/spec file, including `requirements.md`/`design.md` (R43's own text needs no
change — the fix makes the implementation match what R43 already required).

### Acceptance Criteria

- **AC1** — §11 accurately reflects every question's real status: Q1/Q6 unchanged (already correct);
  Q2 marked resolved, citing D-026; Q3 marked partially resolved (scope yes, limit no, stated
  precisely); Q4 marked out-of-scope for this spec; Q5 marked resolved once the `AccountService` fix
  lands.
- **AC2** — the test-suite precondition is satisfied via explicit, named acceptance of Groups A/B as
  environmental/unconfirmed-flaky exceptions (both independently corroborated across T36-T38),
  documented in §11 and the Phase 13 commit message — not silently ignored.
- **AC3** — `package.md`'s Status becomes `READY FOR IMPL`, Version becomes `0.2`, only after AC1/AC2
  are satisfied per the above.
- **AC4** (new, from the R43 fix) — `AccountService.lock`/`unlock` now audit and emit lifecycle
  events on every real state transition, matching `adminUnlock`'s existing, already-correct pattern.

### Constraints

- The `AccountService` fix must reuse the existing private `recordAudit`/`publishLifecycleEvent`
  helpers exactly as `adminUnlock` already does — no new audit mechanism.
- Must preserve `lock`/`unlock`'s existing idempotent-no-op guards (only fire the audit/event on an
  actual state transition, matching `adminUnlock`'s own `wasLocked`-gated pattern — Phase 9's prior,
  human-approved fix for exactly this class of bug).

### Open Questions

No blockers remaining. Both genuine judgment calls (the R43 fix, the Groups A/B waiver) are resolved
above via human gate.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
