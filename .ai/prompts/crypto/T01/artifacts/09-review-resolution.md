<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# crypto · T01 · Phase 9 — Review Resolution

All 5 Phase 8 (Kimi) findings verified against source before disposition — several turned out to be
partially or fully inaccurate, corrected below with evidence.

| # | Finding | Verified? | Disposition |
|---|---|---|---|
| 1 | ADR-0004 missing — AC3 not satisfied | **Partially wrong.** The file exists on disk with verified-correct content (Phase 7 re-read it in full). Root cause: `git log --diff-filter=A -- docs/adr/0004-...` shows it was never part of any commit, unlike every other Phase 6 file, which all landed together in `d0ce60c`. Kimi's review evidently worked from the committed tree, where the file genuinely was absent — the finding is accurate *relative to that view*, not a real implementation gap. | **Human-gate decision: staged (`git add`), not committed** — visible/diffable now, commit remains the user's own call per the standing no-commit-unless-told rule. |
| 2 | AC5 relies on a non-green build; 3 Kafka-observation failures undocumented | **Wrong on the "undocumented" claim.** `package.md` §12's *first* bullet ("Kafka producer→broker environment connectivity") already names `EndToEndLifecycleIntegrationTest`, `AccountPersistenceIntegrationTest`, and `AuditTrailIntegrationTest` by name, first logged T36. Kimi's evidence cited only the second bullet (the `ApiKey*` pair) and appears not to have checked the first. Re-ran the failing tests a third time regardless, in isolation: the `ApiKey*` class produced a *third distinct symptom* seen at T40 (`auditRecordsOneFailureRowAndOneOutboxMirrorPerRejection`, "expected 1L was 2L" — actually a **recurrence** of an already-documented symptom, not a new one), while the three Kafka-observation tests failed with the exact already-documented pattern again. | **Human-gate decision: `package.md` §12 updated** — not with new scope (there wasn't any), but with a one-line re-confirmation note on each existing bullet, dated and attributed to crypto-service T01's AC5 check, closing the audit-trail gap Kimi correctly wanted addressed even though the specific "undocumented tests" claim was inaccurate. |
| 3 | Self-review missed the "missing" ADR | Same root cause as #1 — the self-review verified the file's *content* (accurately) but not its *commit* status, since at self-review time nothing else from this task was committed either. Not a self-review process gap; a git-hygiene gap, now closed by #1's resolution. | **No action beyond #1.** |
| 4 | Redundant `spring-security-oauth2-resource-server` dependency | Kimi's own disposition: harmless, mirrors auth's own pom, "leave as-is for T01." Confirmed accurate. | **Accepted, no change.** |
| 5 | Threat-model header slightly stale | Already surfaced and dispositioned in Phase 7 self-review with the same reasoning Kimi independently arrived at. Cross-confirms the Phase 7 judgment rather than adding new information. | **Accepted, no change** — same reasoning as Phase 7: AC1 scoped the table only; noted for T02. |

## Actions taken this phase

1. `git add docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` — staged, not committed.
2. `spec/auth-service/package.md` §12 — two re-confirmation notes added (one per existing bullet),
   dated 2026-08-25, attributed to this task's AC5 check. No new failure classes introduced; both
   notes tie back to symptoms/tests already named in the doc.

## Corrected understanding carried forward

AC5's evidence base is now: two full-suite runs (Phase 6) + one targeted 3-test isolation run (Phase
9), all consistent with `package.md` §12's pre-existing, already-accepted flakiness taxonomy — zero
novel failure classes attributable to this task. The mechanism argument from Phase 6 (no file under
`services/auth` touched; `-pl services/auth` never reads the root `<modules>` list) still stands as
the primary reason this isn't a regression; the doc update makes the supporting evidence auditable
per Kimi's actual underlying concern, independent of the one inaccurate claim.

---

**Phase 9 complete — all findings resolved, human gate cleared.** Proceed to Phase 10 (Test
Generation) on approval.
