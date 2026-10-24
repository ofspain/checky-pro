# frontend · T13 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Findings only; fixes land in phase 9, after Kimi's phase 8. The money-type, transaction and thread-safety headings
do not apply; secret-handling is the whole subject.

Two of these were confirmed by running the patterns rather than reasoning about them, and the first is a real hole
in the task's primary assertion.

## S1 · A secret in a query string on an allowlisted URL is invisible

**Severity: medium** — a genuine bypass of AC8, the criterion this task leans on hardest.

**Evidence:** `bundleContainsNoSecretEnvKeys.test.ts:80` (`ABSOLUTE_URL_PATTERN`), `:352-354` (set equality).

The URL pattern's path class is `[A-Za-z0-9._\-/]*`, which stops at `?`. Measured:

```
"https://bit.ly/wb-precache?key=SUPERSECRET"  →  ["https://bit.ly/wb-precache"]
```

That is an allowlisted entry, so **set equality passes and the secret is never reported**. Anything appended to
one of the eight known URLs as a query string or fragment is laundered through the allowlist. The secret-shape
patterns are the only remaining net, and they only catch shapes they know.

**Recommendation:** extend the pattern to consume query and fragment characters (`?`, `&`, `=`, `%`, `#`) so the
full URL is what gets compared, and add a case asserting that no emitted URL carries a query string at all — none
does today, and a vendor URL acquiring one is worth a human look. The negative control must include a known URL
with a secret appended, since that is precisely the case that currently passes.

## S2 · The platform-host check is narrower than it reads

**Severity: low** — defence in depth, already backstopped by set equality.

**Evidence:** `:356-362`.

`new RegExp('//' + host + '[:/]|//' + host + '$')` matches a bare service hostname. Measured:

```
http://auth:8080/x              → flagged
http://auth                     → flagged
https://auth.checky.example/x   → NOT flagged
```

So a real deployment hostname containing a service name slips past this case. It is not a hole in the suite, since
AC8's set equality fails on any URL not in the eight-entry allowlist, but the case advertises more than it does,
and someone adding an entry to `KNOWN_ABSOLUTE_URLS` might lean on it.

**Recommendation:** match the service name as a label anywhere in the host (`//(?:[^/]*\.)?auth(?:[.:/]|$)`), or
reword the case to say it checks bare service hostnames and that set equality is the real guard.

## S3 · The production-build assertion covers only the main chunk

**Severity: low** — the worker is built by a second pipeline whose mode is unasserted.

**Evidence:** `:194-201`.

The case that makes this whole file meaningful — "is a production build" — reads the single `assets/*.js` chunk.
`serviceWorker.js` is produced by `vite-plugin-pwa`'s own build and could in principle be development-mode
independently of the app chunk.

**Recommendation:** assert the property over every emitted JS file rather than the main chunk, which costs one
line and removes the asymmetry.

## S4 · Nothing asserts the bundle actually contains this application

**Severity: low** — a vacuity risk one level up from the one already closed.

**Evidence:** `scanBundle` throws on an empty tree (`:109`), and `:186-192` asserts the file list's shape.

`scanBundle` refuses zero files, and the tree case checks that `index.html`, one `assets/*.js` and one worker
exist. But nothing checks the chunk is *our* application. A build that emitted a near-empty chunk — a
misconfiguration, a broken entry — would satisfy every "this set is empty" assertion in the file.

**Recommendation:** assert the chunk contains a marker from our own source, such as the `app-shell` test id or the
`mountShell: root element #root not found` message. That turns "nothing was found" into "nothing was found in
something that is really the app".

## S5 · Binary assets are read as UTF-8

**Severity: low** — no binary asset exists today.

**Evidence:** `:116` reads every file with `readFile(..., 'utf8')`.

A font or image added later would be decoded as replacement characters. That cannot create a false *pass* for a
plain-text secret in a text file, but a secret embedded in a binary would very likely be missed, and the noise
could in principle produce a spurious hit.

**Recommendation:** either read non-text extensions as a Buffer and scan with `latin1` (which never drops bytes),
or state in the scanner's doc comment that the artifact is text-only today and that adding a binary asset means
revisiting this. I lean to the former: it is two lines and removes a future trap.

## S6 · The key scan cannot see a value inlined under an arbitrary name

**Severity: low** — by design, but the design should be stated where the assertion is.

**Evidence:** `ENV_KEY_PATTERNS` at `:63`.

The scan looks for `VITE_*` and `process.env.*`. A value inlined by a `define` block under a name like
`__API_BASE__` would be invisible to it. That is exactly why mechanism closure is the primary instrument and the
"no `define`" case exists — but the key-scan cases do not say so, so a reader could over-trust them.

**Recommendation:** a comment at `ENV_KEY_PATTERNS` naming the limit and pointing at the `define` case as its
counterweight. No behaviour change.

## S7 · The "every entry justified" case is a smell detector, not a proof

**Severity: low** — honest labelling.

**Evidence:** `:364-376`.

It asserts the `KNOWN_ABSOLUTE_URLS` block contains at least three comment lines. It does not tie a comment to an
entry, so four new unjustified URLs could be added under the existing comments and the case would pass.

**Recommendation:** assert the comment count is at least the number of comment *groups* expected, or drop the case
and rely on review. Pretending it enforces one-reason-per-entry is worse than not having it. I lean to keeping it
with a comment that says what it actually checks.

## What I checked and found sound

- **One scanner, two callers.** The negative control writes its own temp tree and calls `scanBundle`, so the
  control and the real assertion share every definition. Ten categories are proven reportable, including the
  platform's own `checky-local-only`, so the scanner is demonstrably not pattern-only.
- **The vacuity trap is closed** where it bit T12: `scanBundle` throws on an empty tree, and a case asserts the
  throw.
- **Regex state.** The `/g` secret patterns are module-level and shared, and `lastIndex` is reset after each
  `test()`. `matchAll` does not mutate its source regex. Repeated scans give the same answer — verified by the
  control and the real scan running in the same file.
- **The artifact is the right one now**, and that is asserted rather than assumed (the phase-6 finding).
- **Mechanism closure** is genuinely closed on all four routes: `envPrefix` pinned (and the rebuild produced an
  identical content hash), no `define`, no `.env*`, no surviving `import.meta.env.`, plus no sourcemap.
- **The allowlist is the spec's**, by set equality, and the parser keys on `/Environment keys/i` so §4c's stale
  "PROPOSED" wording cannot break it.
- **Module boundaries.** `scanBundle` is test-local; no production module imports it, and nothing was exported for
  it.
- **Both "zero keys" assertions are deliberate and labelled** — one for the rule (nothing outside the allowlist),
  one for today's stricter reality (nothing at all), with a comment pointing at OQ-1 so they are not conflated.
- **No T01–T12 file modified**; `vite.config.ts` is the only production change and it only states an existing
  default.

## Still open, unchanged

**OQ-1** — `design.md` §4c declares four public keys that R58 permits, while T01 `:130` forbids a source from
naming the environment object at all. No conflict today; the owner must settle it before T14+ consumes a key.

**Carried from phase 6, not a finding against this task:** T01's own build runs through Vite's JS API from inside
vitest, so it asserts against a development bundle. Nothing it claims is wrong, and the file is on this task's
not-to-modify list.
