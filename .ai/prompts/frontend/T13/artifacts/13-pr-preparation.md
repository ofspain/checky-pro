# frontend · T13 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
frontend T13: no secret in the bundle
```

## Commit message

```
R58 is a property of the artifact, so the test builds one and reads it:
a real production build into a temp dir, every emitted file walked, not
a glob over assets/. The build is shelled out to node_modules/.bin/vite
with NODE_ENV, VITEST and every VITE_ variable deleted from the child
env -- Vitest sets NODE_ENV=test, and a bundle built under it is a
DEVELOPMENT bundle, which was the first version of this test scanning
the wrong artifact entirely.

The weight of the task is on mechanism closure, not pattern matching,
because a scan cannot find a value nobody can predict. Vite inlines a
key's VALUE, not its name, which the positive control proves by building
a throwaway project that reads one. So: envPrefix pinned to ['VITE_']
in vite.config.ts (the only production file this task touches), no
define block, no .env file, and no surviving import.meta.env. in the
output. Shape patterns and the platform's own checky-local-only are the
second line, not the first.

The §4c allowlist is parsed out of design.md and compared by set
equality, so a fifth key or a missing one fails here. It is a ceiling
nothing consumes: T01 still forbids any source from reading
import.meta.env at all, which is stricter than R58 permits. That
conflict is real and deliberately left for the owner -- see below.

src/app/bundleContainsNoSecretEnvKeys.test.ts, 37 cases, one scanner
shared by the real bundle, a synthetic negative control and a positive
control that builds its own project. AC8 is set equality over eight
enumerated vendor URLs with a justification each, because the bundle
genuinely contains eight and the criterion I first wrote ("none, with
exceptions if any") would have failed on its first run.

17 mutants, 16 caught, including both envPrefix broadenings that Vite
accepts, sourcemaps enabled (which fails seven cases -- a sourcemap is
a second copy of the source, not a side file), and a fifth key added to
design.md. The survivor is M12, utf8 in place of latin1: measured, Node's
UTF-8 decoder resynchronises, so a contiguous ASCII needle is found under
either decode, and where detection does fail latin1 fails identically.
The justification I gave that change in phase 9 was wrong and is
corrected in the code and in both earlier artifacts rather than left to
read as a solved problem.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts` | **new**, 592 lines, 37 cases |
| `frontend/vite.config.ts` | `envPrefix: ['VITE_']` pinned, with the comment corrected to what M3/M13/M14 measured |
| `.ai/prompts/frontend/T13/artifacts/*` | the fourteen phase artifacts (9 and 10 carry dated corrections) |

No other production file, no T01–T12 test, no `spec/**`, `contracts/**`, `services/**` or `infra/**`.

## Testing performed

| Check | Result |
|---|---|
| `vitest run src/app/bundleContainsNoSecretEnvKeys.test.ts` | **37/37** |
| `vitest run` (whole suite) | **412/414** — 377 before, delta exactly 37 |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |
| Mutation pass | **17 mutants, 16 caught**; the survivor explained by measurement |
| `spec/frontend/design.md` | **byte-identical** after M7/M8 reverted |

The two failures are T07's `lintRejectsHandWrittenBackendFetch` AC4 (no `oxlint` summary line on this host) and
AC2 end to end (`spawn pnpm ENOENT`), both environmental, both failing since T07, neither touched by this task.

## Carried forward

**Owner decisions, in the order they will bite:**

1. **OQ-1 — T01's source rule vs §4c's allowlist. This is now the one that blocks.** `design.md` §4c declares four
   public keys and R58 permits them; T01 `:130` forbids **any** source under `src/**` from naming
   `import.meta.env`. Today nothing collides because nothing reads a key. **T14 opens the auth section**, and the
   OIDC issuer, client id and redirect URI are exactly what §4c declares — so the first auth task that wants a
   build-time key will fail a T01 assertion on a change the spec expressly permits. T13 deliberately did not
   settle it (D-11): the frozen position is that the allowlist is a ceiling, not a plan. **The owner must choose
   between relaxing T01 to allow the four keys and keeping runtime derivation instead.**
2. **T12's OQ-1** — a refresh cannot promote a waiting worker; the prompt UI needs the owner's wording and its
   reload mechanism before it can be built.
3. `package.md:321-323` — forced update for rollback is owned by no task.

**Known, recorded, not fixed (all pre-existing and out of this task's scope):**

- `segmentPrefixPattern` is still duplicated between `navigationDenylist.ts` (private) and `apiRequestPolicy.ts`.
  The only T09–T12 carry-forward still open.
- `package.md:172` maps `serviceWorkerUpdateWaitsForReload` to R57, which does not describe it.
- `apiRequestPolicy.ts:46` lists `/app` in `SHELL_DOCUMENT_PATHS`, and `/app` returns 404 at the edge today.
- `design.md` §4c's heading still reads "PROPOSED, pending sign-off (Q6)" although Q6 closed 2026-10-10. The
  parser keys on `/Environment keys/i`, so this is cosmetic — but no task may edit `spec/`.
- **T01 also builds and asserts against a development bundle** (it inherits Vitest's `NODE_ENV=test`). Harmless
  for what it asserts, and T01 is on the do-not-modify list, but it is the same trap T13 fell into.

**What T13 leaves provable and what it does not** — the limits are in phase 12's "Coverage, honestly", and the two
that matter are in the code itself: a value nobody can predict is not findable by scanning, and a secret stored
UTF-16 is invisible to any text scan. Mechanism closure is the defence; the patterns are the backstop.

## Phase 1 — Foundation is complete

T13 is the last task of the foundation section (`tasks.md:23` opens auth immediately after). Thirteen tasks, the
SPA shell, router, generated API client, i18n, lint policy, the service worker and its policies, and now the
artifact-level secret guard. **T14 begins the auth section — and per the standing contract, the first real visible
screen is expected at T16.**

## Specification references

R58 (`requirements.md:206-207`); `design.md:46` (L10), `:44` (L9), `:183-184` (§4c), `:196-220` (§6);
`agents.md:20`, `:83`; `package.md:161` (§8 mapping), `§11 Q6` (closed 2026-10-10); `tasks.md:22`;
`frontend/vite.config.ts:8-16`, `frontend/src/app/shellBuildsAndMounts.test.tsx:127-133`,
`infra/local/compose.yaml:29`, `:206-213`.
