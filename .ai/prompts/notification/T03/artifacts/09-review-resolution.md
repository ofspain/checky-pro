# notification · T03 · Phase 9 — Review Resolution

Disposition of the 7 findings from `artifacts/08-independent-review.md`, plus the 2 findings from
`artifacts/07-self-review.md` (self-review Finding 1 overlaps Kimi's Findings 1/4/5 — resolved
together below; self-review Finding 2 needs no action, already an accepted observation).

## Findings 1–5 (Kimi) — accurate, but premature: deferred to Phase 10 by design

**Disposition: DEFERRED, not implemented now.**

All 5 findings correctly observe that no T03-specific test file exists yet
(`EmailPropertiesTest`, `LinkPropertiesStartupValidationTest`, `PublicEndpointsTest`,
`ResourceServerConfigIntegrationTest`, `ApplicationProperties*Test`). This is verified true against
the actual file listing. It is not, however, a defect in T03's own completion state at this point in
the pipeline: Phase 6's own explicit instruction ("Do NOT write tests here (that is Phase 10) unless
the task itself is test-only") deliberately splits config/wiring tasks into a code-only
implementation phase followed by a dedicated test-generation phase — and `services/crypto`'s own T03
is the identical, already-executed precedent: its own Phase 6 implementation notes state "no tests —
Phase 10 scope, per this phase's own rule," and all 8 of its test files (51 tests) were written at
its own Phase 10, not Phase 6. Writing them now, at Phase 9, would both violate that established
split and duplicate work Phase 10 is about to do anyway.

Kimi's own recommendations for each of the 5 findings are retained verbatim as Phase 10's own
required-test list (they match, almost line for line, what Phases 2/4/5 already planned):
`EmailPropertiesTest`/`RetryPropertiesTest`/`InappPropertiesTest`, `LinkPropertiesStartupValidationTest`
(with the exact `@ActiveProfiles("local")`/`@ActiveProfiles("dev")` pair Kimi's own Finding 4
specifies), `PublicEndpointsTest`, `ResourceServerConfigIntegrationTest`, and
`ApplicationPropertiesSecurityConfigTest`/`ApplicationPropertiesJpaConfigTest`. Self-review's own
Finding 1 (no real-binding proof for 3 of the 4 records) is subsumed by this same disposition —
Phase 10's real-binding test resolves it directly.

**Nothing implemented for Findings 1–5 in this phase.** Tracked forward, not dropped.

## Finding 6 (Kimi) — `server.port` conflict with `services/auth`

**Disposition: ACCEPTED, fixed.** Verified: `services/auth/application.properties:109` sets
`server.port=8080`; notification's own file had no `server.port` entry, defaulting to the same 8080.
Added `server.port=${SERVER_PORT:8082}` with a comment. Noted (not fixed, out of scope): `services/crypto`
has the identical latent gap — its own `application.properties` also has no explicit `server.port`,
carrying the same silent 8080 default. Not touched here; a sibling service's file is out of this
task's own scope regardless of the shared root cause.

## Finding 7 (Kimi) — `AccessDeniedHandler` currently untriggered

**Disposition: ACCEPTED as a forward-looking note, no action needed now.** Correct and expected: no
authority-scoped endpoint exists until task 13, so no path in T03 itself can trigger a 403. Tracked
for whichever later task's own `ResourceServerConfigIntegrationTest` extension first adds an
authority check — not a T03 defect.

## Self-review Finding 2 — new architectural pattern, no sibling precedent

**Disposition: ACCEPTED as a documented observation, no action needed.** Already resolved at the
self-review's own recommendation level (reuse the same shape if it recurs) — nothing further to do.

## Verification

`mvn -pl services/notification -am verify` — 22 tests, 0 failures, unchanged except for the
`server.port` addition (a runtime property, not exercised by any current test). `git status -s
services/auth services/crypto` — empty; no sibling service touched.
