# notification · T18 · Phase 0 — Repository Understanding

## Task

`tasks.md` task 18: "Run full suite. `mvn -pl services/notification verify` must pass; Docker image
builds from repo root."

## State confirmed directly

- `mvn -pl services/notification clean verify` has been run fresh at the end of every one of T14
  through T17's own phases, most recently at T17 Phase 12: **369 tests, 0 failures, 0 errors,
  `BUILD SUCCESS`.** This half of the task is already continuously satisfied; this task's own job
  is to make it an explicit, dedicated, final checkpoint, not to newly achieve it.
- **No Dockerfile exists anywhere in `services/notification`** — confirmed directly, `find` returns
  nothing. `services/auth/Dockerfile` and `services/crypto/Dockerfile` both exist as the established
  precedent this task is expected to mirror.
- **Docker is available in this session right now** — confirmed via `docker info` (a standing
  "Docker unavailable" disclosure from an earlier session must never be assumed still true;
  `services/crypto`'s own T28/T29 record made exactly this mistake once already).

## A real, already-known blocker reproduced directly, not assumed

[[crypto-service-task-progress]] already recorded (T29, prior session) that **both existing
Dockerfiles fail `docker build`** via a shared Maven reactor validation error, flagged as "the
single highest-priority follow-up task for this repo." Re-verified fresh, right now, rather than
trusted on a memory's own word:

```
docker build -f services/auth/Dockerfile -t auth-service-test .
```

fails with:
```
[ERROR] Child module /workspace/services/crypto of /workspace/pom.xml does not exist
[ERROR] Child module /workspace/services/notification of /workspace/pom.xml does not exist
```

**Root cause, confirmed:** the root `pom.xml` declares three `<module>` children (`services/auth`,
`services/crypto`, `services/notification`). Each existing Dockerfile's build stage copies the root
`pom.xml` plus only its *own* service's `pom.xml` into the build context, then runs `mvn -pl
services/<name> ...`. Maven's reactor validation reads the root `pom.xml`'s own `<modules>` list and
requires *every* declared module's `pom.xml` to be physically present to resolve the project
structure at all — even though only one module is actually being built (`-pl`) — so the build fails
before `-pl` even gets a chance to narrow anything.

**Implication for this task:** a new `services/notification/Dockerfile`, if it mirrors the existing
(broken) pattern — copying only its own `pom.xml` — would fail identically, now citing
`services/auth` and `services/crypto` as the missing children instead. T18's own literal
requirement ("Docker image builds") would not actually be met by copying the established pattern as-is.
**The fix is already known and cheap**: copy every sibling module's `pom.xml` (not its `src`) into
the build context before running Maven, not just the module being built's own.

## Decision

Presented to the user as a genuine scope question, since the fix for this task's own new file is
identical to the fix the already-known auth/crypto bug needs.

**User chose: fix only `services/notification/Dockerfile`.** This task's own new file will copy
every sibling module's `pom.xml` into its build context (fixing the bug for itself, since it's a
new file free to do so), while `services/auth/Dockerfile` and `services/crypto/Dockerfile` remain
untouched — the already-known, already-flagged top-priority follow-up from
[[crypto-service-task-progress]] stays exactly that, a separate, not-yet-scheduled task.
