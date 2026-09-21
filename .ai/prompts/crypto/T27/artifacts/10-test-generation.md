# crypto · T27 · Phase 10 — Test Generation

## Scope

None. Phase 1's own "Tests required" section is explicit: "None — this task authors no test. Its own
'test' is the full-suite run itself (AC1) and the image build (AC2), both process-level checks, not new
JUnit tests." Restated, unchanged, at Phase 2, Phase 4, and Phase 5. No deviation found while
implementing.

## What stands in for tests here

- `mvn -pl services/crypto -am verify` — run twice this task (Phase 6, Phase 9 after the branch
  restoration), both times producing 696 tests / 0 failures / 14 already-disclosed Docker-only errors.
- `docker build -f services/crypto/Dockerfile -t crypto-service .` — attempted, confirmed blocked only
  at the daemon-connection level (no Docker daemon in this environment), not a Dockerfile-content issue.
- `KmsSignerArchitectureTest` — the one piece of test *code* this task touched (Phase 6's allowlist
  addition, Phase 9's message-assertion addition) belongs to T25/T26's own pre-existing file and was
  modified only to resolve a genuine cross-task conflict (Phase 6) and a Kimi review finding (Phase 9),
  not authored fresh for T27 itself.

## Phase 11 (Kimi Test Review) preview

Since no new test file exists for T27, Phase 11 has nothing test-generation-specific of its own to
review; if Kimi's Phase 11 pass produces findings, they will necessarily be about
`KmsSignerArchitectureTest`'s two T27-touched pieces (the allowlist condition, the message assertions)
rather than about a dedicated new test suite.
