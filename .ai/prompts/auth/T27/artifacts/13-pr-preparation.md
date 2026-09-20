<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T27 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Task ready for merge preparation. No code changed in this phase.

---

## Commit Title

```
Add API-key lifecycle integration test (T27)
```

## Commit Message

```
Add API-key lifecycle integration test (T27)

Add ApiKeyLifecycleIntegrationTest, proving the full
create->exchange->revoke->exchange-fails sequence for a single API
key through real HTTP calls (TestRestTemplate) against a real running
server and real Postgres (Testcontainers). No production code is
touched - every operation this test drives (POST /api-keys, POST
/api-keys/token, GET /api-keys, DELETE /api-keys/{keyUuid}) already
exists and works, built across T24 (service layer), T25 (token
exchange), and T26 (CRUD endpoints).

Each of those three tasks' own test files already proves its one
operation in isolation. None of them proves the *sequence* - that a
key valid a moment ago is immediately and completely unusable the
moment it is revoked, observed continuously at the HTTP boundary
rather than inferred from separate tests using separate keys. That
gap is what this test closes.

This task's own four header-listed "named tests" already exist,
correctly, in those three other files - package.md itself has no
distinct row for a T27-specific test name, confirming those four
names were simply carried over from the requirements they trace to.
Writing four more methods under those names here would have collided
in intent; this file contributes exactly one, distinctly-named test
instead.

Design review before writing the test surfaced that last_used_at and
revoked_at are only ever observable over HTTP via GET /api-keys - the
exchange endpoint's success response is only the JWT, and revoke
returns 204 with no body - so the sequence needed three GET /api-keys
checkpoints added to what would otherwise have been a four-call flow.
Two rounds of test review (self + Kimi, twice) progressively
strengthened the single test: exact problem-type/title assertions on
both 401 responses (not just status and body equality), Content-Type
checks throughout, and an exact-field-set assertion on the create
response so this test's own entry point is self-contained.

Testcontainers-backed; could not be executed this session (no Docker
daemon available). Compiles cleanly and, per this test's own design
notes, if it fails on first real execution the most likely root cause
is T25's ApiKeyTokenIssuer/JwtEncoder infrastructure (which this test
depends on to authenticate its own calls), not this test's own logic.

Refs: spec/auth-service/tasks.md task 27, R30/R31/R32/R33/R34/R35,
L7/L8.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

*(The generated template specifies "Claude Opus 4.8 (1M context)" in this trailer; substituted with the model that actually did the work, same substitution as T16/T25/T26's Phase 13.)*

---

## Files Changed

**Tests — created:**
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyLifecycleIntegrationTest.java`

No production file created or modified. No file under `spec/` touched (confirmed empty diff against both `fd64a72`, T26's actual final commit, and the broader `ca742e0` range). No Flyway migration.

**Note on commit boundaries (recurring in this session):** as with T26's own Phase 13 note, this session's git commits don't cleanly separate task boundaries — `fd64a72` ("conclude t26") sits chronologically *after* `ca742e0` ("t26 test review by kimi") and contains T26's own Phase 11 fixes to `ApiKeyCrudIntegrationTest.java`, which a naive diff against `ca742e0` would have misattributed to T27. The Files Changed list above uses `fd64a72` (T26's actual last commit touching `services/auth/`) as the correct baseline, verified by checking which commit's message actually describes T26 wrap-up work.

---

## Summary

Implements task 27 of `spec/auth-service/tasks.md`: one integration test proving the API-key subsystem's complete lifecycle end-to-end, closing a real coverage gap that existed after T24/T25/T26 each shipped their own operation-scoped tests. Design review (Phase 3/4) caught that the originally-planned four-call sequence couldn't actually verify `last_used_at`/`revoked_at` transitions over HTTP at all — fixed by adding three `GET /api-keys` observation points, expanding the flow to eight steps. Two independent review rounds (Kimi Phase 8 and Phase 11) each found and closed real gaps in the test's own rigor — status/content-type assertions that were missing before either review pass, and problem-type/title assertions that weren't part of the original design — without ever needing a second Phase 9 human-gate round, since Phase 11's findings were straightforward enough to triage and apply directly.

## Testing Performed

- `mvn -pl services/auth -am compile` and `test-compile` — clean.
- `ApiKeyLifecycleIntegrationTest` (1 test, 8-step sequence, Testcontainers + real filter chain via `TestRestTemplate`) — **not executed this session**, Docker daemon unavailable. Fails only with the same `ApplicationContext failure` every other Testcontainers-backed class in this module currently produces; not a defect in the test.
- No Docker-independent regression run needed — no production code changed, and this is a single new, self-contained test file with no shared state affecting any other test class.
- **Before merge:** run `ApiKeyLifecycleIntegrationTest` with a working Docker daemon. Per the frozen brief's D3, check T25's `ApiKeyTokenIssuer`/`JwtEncoder` first if it fails on the first real attempt. This is now the **third** consecutive task (T25, T26, T27) whose full integration coverage has never executed this session — running all three together, in that dependency order, is the single highest-value pre-merge action for this whole API-key feature set.

## Specification References

- **Task:** `spec/auth-service/tasks.md`, task 27 — API-key integration tests.
- **Requirements:** R30, R31, R33, R35 (scoped) — plus R32, R34 (referenced by the frozen brief, D1).
- **LOCKED decisions:** L7, L8 (scoped).
- **Full design-decision trail:** `artifacts/03-design-challenge.md` (Kimi, 5 findings) → `artifacts/04-frozen-task-brief.md` (D1–D4, human-decided) → `artifacts/09-review-resolution.md` (2 of 3 Kimi findings accepted, human-decided) → `artifacts/12-specification-verification.md` (PASS).

---

**Phase 13 complete — pipeline finished, all 14 phases.** No commit run (per established session rhythm — only an explicit "commit it" triggers that). Branch remains `spec/service-specs-and-ai-framework`; `main` untouched throughout.
