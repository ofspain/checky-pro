# payment · T27 · Phase 7 — Self-Review

Consumes:
- `artifacts/06-implementation-notes.md`
- the Phase 6 diff (`04200adf`)

Reviewed against `artifacts/04-frozen-task-brief.md` (FROZEN) and `spec/payment-service/agents.md`.
This phase lists findings only. Fixes are decided at Phase 9.

## Scope check

The diff touches exactly the two files the brief authorises (AC6):
- `services/payment/Dockerfile`, which is new
- `T01SkeletonRegressionTest.java`, which gains one `@Test`, one constant and one helper

`src/main`, both poms, the sibling Dockerfiles, `spec/` and `contracts/` are untouched.

## Dimensions with no finding

These areas have nothing to report:
- **Business logic:** money types, transaction boundaries, idempotency, module boundaries.
  T27 adds no production code.
- **Thread-safety.** The test is stateless and touches no shared mutable fields.
- **Null-safety.** `Files.readString` throws on a missing file, which fails loudly as intended.
  The `fromLines.get(size-1)` call is guarded by the `>= 2` assertion before it.
- **Secret handling.** The Dockerfile has no `ENV`, `ARG` or `LABEL`, and copies no config files.
  AC3's inspect shows only the base image's environment. AC4 passed every runtime value as an
  environment variable.

## Findings

| # | Issue | Severity | Evidence | Recommendation |
|---|---|---|---|---|
| S1 | **The D4 guard matches Dockerfile keywords case-sensitively.** Docker treats instructions case-insensitively, so `env FOO=bar`, `add x y` or `from …` would slip past the `ENV` and `ADD` bans and the `FROM` detection. | Low | `T01SkeletonRegressionTest.java:272, 281, 283`: the `startsWith("FROM ")`, `startsWith("ADD ")` and `startsWith("ENV ")` checks | Normalise the instruction keyword before matching: upper-case the first token, or use `(?i)` regexes. |
| S2 | **The `ARG` instruction is not banned.** `ARG` values are recorded in the image history (`docker history`), so a `--build-arg SECRET=…` would leak, which violates L13. The brief bans only `ENV`. | Low | `T01SkeletonRegressionTest.java:282-283`, and frozen brief D4 | Add `ARG` to the banned set. This sits within the spirit of D4 and L13, and needs a Phase 9 decision because the frozen D4 lists only `ENV`. |
| S3 | **The broad-copy check is narrow.** `COPY\s+\.\s.*` misses `COPY --chown=x . .` and `COPY ./ .`. | Low | `T01SkeletonRegressionTest.java:281` | Match any `COPY` or `ADD` whose source argument, after flags, is `.` or `./`. |
| S4 | **The `USER nonroot` window starts at the first occurrence of the final `FROM` text.** `indexOf` returns the first match. If both stages used the identical `FROM` line, a `USER nonroot` in the build stage would satisfy the check. That cannot happen today, because the stages use different images. | Info | `T01SkeletonRegressionTest.java:277` | Use `lastIndexOf(finalFrom)`, a one-token change. |
| S5 | **The test uses fully-qualified `java.util.List` and `java.util.ArrayList`** rather than imports. This adds four new fully-qualified uses to a file that already had two. | Info | `T01SkeletonRegressionTest.java:270-287` | Optionally import `java.util.List` and `java.util.ArrayList`. Readability only. |
| S6 | **Base images are pinned by tag, not by digest.** `maven:3.9-eclipse-temurin-21` and `java21-debian12:nonroot` float. | Info | `Dockerfile:8, 19` | No change. This mirrors the siblings (D1, which is frozen), and the infra workstream owns digest pinning. Record it in the PR notes. |
| S7 | **There is no `.dockerignore`.** `COPY services/payment/src` brings test sources into the build stage. They never reach the runtime image, because only the jar is copied across. | Info | `Dockerfile:16`. The TIB scope excludes `.dockerignore`. | No change. Out of scope per the brief. |

## Summary

- **No Critical, High or Medium findings.**
- **S1 to S3 (Low):** gaps in how strictly the guard test reads the Dockerfile. The Dockerfile
  itself complies today.
- **S4 to S7 (Info).**

All AC evidence in `06` stands: 497/497, build exit 0, inspect, and the read-only smoke start
reaching `UP` with partitions assigned.

## Open Questions

- **S2** extends D4's banned set from `ENV` to `ENV` plus `ARG`. This needs a human decision at
  Phase 9.
