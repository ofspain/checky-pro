# auth · T19 — Phase 9: Review Resolution

Consumes Kimi's `artifacts/08-independent-review.md`. Every finding independently re-verified
against actual source before disposition, per this pipeline's own standing discipline — nothing
accepted or rejected on Kimi's word alone.

---

## Dispositions

| # | Finding | Severity | Verified | Disposition | Resolution |
|---|---|---|---|---|---|
| 1 | `MfaCurrentPasswordMismatchException` deviates from the frozen brief's 403/`MFA_PASSWORD_MISMATCH` | Medium | Re-read `02-task-implementation-brief.md`'s mapping table and `AccountExceptionHandler.java:55-61` directly — Kimi's citation is accurate; the 400/reused-type choice is correct and was already disclosed at Phases 5/6, just never recorded *in* the frozen-brief artifact itself. | **ACCEPTED** | Added a dated addendum to the top of `04-frozen-task-brief.md`, cross-referencing Phases 5/6/9 and this disposition. The original frozen text is left untouched below it, matching this pipeline's "disclose forward, don't rewrite history" convention — Kimi's option (a), not (b) (reverting to the worse 403 design would reintroduce the exact inconsistency the correction was for). |
| 2a | `auth.yaml`'s four MFA paths have no explicit `security:` key | Medium | Verified directly: the frozen brief's own Phase 2 text says "all `security: [bearerAuth]`" — a real, literal instruction I did not follow, distinct from "mirror `/accounts/me/password`'s style" (which itself also omits an explicit key, a genuine internal tension in the brief's own wording, but the explicit instruction is the more specific, intentional one). | **ACCEPTED** | Added `security:\n  - bearerAuth: []` to all four MFA operations in `auth.yaml`. `AuthOpenApiContractTest` re-run (10/10 green) — it doesn't inspect `security:` at all, so no regression risk there. |
| 2b | `auth.yaml`'s four MFA paths document only success responses, no 400/401/404/409 | Medium | Re-verified by direct parse (not re-grep): **0 of 30 pre-existing, non-MFA operations in `auth.yaml` document any 4xx/5xx response today** — the identical premise Phase 3's own Finding 5 cited, re-confirmed live, not stale. This is Kimi's own Phase 3 Finding 5, restated verbatim with no new evidence at Phase 8. | **REJECTED AS WRITTEN, same grounds as Phase 4's original disposition** | Adopting this now would make T19 the first and only endpoint set with full error-response documentation, inconsistent with every one of the other 30 operations in the same file — still true today. If a repo-wide error-documentation convention is wanted, that remains a separate, larger decision, not T19's to make unilaterally a second time. |
| 3 | Only 3 of 4 planned `ProblemTypes` constants added | Low | Direct consequence of Finding 1's correction, already disclosed at Phase 5. | **ACCEPTED, no action needed** | Matches Finding 1's resolution — `MFA_PASSWORD_MISMATCH` is correctly never added; adding it now would be dead code. |
| 4 | Old-code invalidation now proven (concurs with self-review Finding 3) | Low | Re-read the test directly — accurate. | **ACKNOWLEDGED** | No action. |
| 5 | Concurrent-regenerate race disclosed and characterized (concurs with self-review Finding 4) | Low | Re-read the test and self-review artifact — accurate, including the "initial hypothesis was wrong, real behavior is safer" framing. | **ACKNOWLEDGED** | No action. Tracked as a possible future ergonomic improvement (retry/backoff on the opaque 500), not this task's scope. |
| 6 | Stale Javadoc corrected (concurs with self-review Finding 2) | Low | Re-read the corrected Javadoc — accurate. | **ACKNOWLEDGED** | No action. |
| 7 | All 11 ArchUnit rules re-verified (concurs with self-review Finding 1) | Informational | Re-ran the same standalone check a third time, post-`auth.yaml` edit (which touches no Java) — still 11/11 pass. | **ACKNOWLEDGED** | No action. |
| 8 | `noneOfTheFourEndpointsArePublic` is a valuable complement to ArchUnit | Informational | Re-read the test — accurate description. | **ACKNOWLEDGED** | No action. |

## Cross-check against acceptance criteria — reconciled

Kimi's own table flagged AC6 and AC9 with ⚠️ for the same "missing error-response documentation"
reason as Finding 2b. Per that finding's rejection, both are **expected, not defects**: Phase 4's
own disposition explicitly reinterpreted AC10 (and by extension AC6/AC9's error-documentation
half) to mean "documents all four endpoints' real *success* responses," matching the file's
100%-consistent existing convention — not full error-response documentation. AC9's `security:` gap
is now closed by Finding 2a's fix. Updated status: **AC6 and AC9 both now read as satisfied** under
the Phase-4-corrected interpretation of what they require; the only remaining partial gap
(error-response documentation) is a disclosed, deliberate, twice-confirmed scope boundary, not an
open defect.

## Files changed this phase

- `contracts/api/auth.yaml` — added `security: [bearerAuth]` (empty scope) to all four
  `/accounts/me/mfa/*` operations.
- `.ai/prompts/auth/T19/artifacts/04-frozen-task-brief.md` — addendum only, original frozen text
  unchanged.

## Verification performed

`mvn -pl services/auth test -Dtest=AuthOpenApiContractTest,MfaServiceTest,MfaControllerTest,MfaExceptionHandlerTest,MfaControllerIntegrationTest,MfaServicePersistenceIntegrationTest`:
all green (10/36/13/6/14/10), confirming the `security:` addition has no effect on the contract
test (it doesn't inspect that field) and no other regression.

## Open Questions

None. Every finding disposed; both accepted fixes applied and verified; one finding rejected on
the same, re-verified grounds as its first rejection at Phase 4.
