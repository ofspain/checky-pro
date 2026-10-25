# frontend · T13 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/08-independent-review.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Ten findings — my seven, all corroborated, plus three fresh from Kimi. **All ten addressed.** 31 cases (27 + 4),
suite **406/408**, tsc and oxlint clean, `.oxlintrc.json` and every T01–T12 file untouched.

One of them was resolved by discovering that **both Kimi's premise and my own assumption were wrong**, and the
correction is the most useful thing in this phase.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| **S1** | secret in a query string on an allowlisted URL is invisible | **Fixed** + regression case |
| **S2** | platform-host check narrower than it reads | **Fixed** — host-label match, and the case renamed |
| **S3** | production assertion covers only the main chunk | **Fixed** — every emitted JS file |
| **S4** | nothing asserts the bundle is this application | **Fixed** — see below |
| **S5** | binary assets read as UTF-8 | **Fixed** — byte-preserving decode |
| **S6** | key scan blind to a `define`d arbitrary name | **Fixed** — stated at the pattern |
| **S7** | "every entry justified" is a smell detector | **Fixed** — renamed to say so |
| **K1** | host `VITE_*` variables leak into the build | **Premise falsified; addressed anyway** |
| **K2** | allowlist parser stops at the first blank line | **Fixed; "fails open" corrected to fails closed** |
| **K3** | negative control lacks the query-string bypass | **Fixed** — it is now the S1 regression |

## S1 — the hole, and its regression case

`ABSOLUTE_URL_PATTERN`'s path class stopped at `?`, so a secret hung off an allowlisted URL reduced to that entry
and set equality passed. The pattern now consumes `/ ? # & = % + ~`, and three things changed together:

1. the negative control carries `https://bit.ly/wb-precache?key=SUPERSECRET` and asserts the scanner reports the
   **full** URL and **not** the bare allowlisted one;
2. that case also asserts the URL survives the real assertion's own filter, so the control exercises the
   instrument rather than just the record;
3. a new case asserts **no emitted URL carries a query string or fragment at all** — none does, and one appearing
   is worth a human look.

## K1 — the premise is false, and the truth is more interesting

Kimi's finding says a host variable such as `VITE_EDITOR` "will be inlined into the bundle, causing the test to
fail with an unlisted key". I set out to prove it with a positive control: a real production build of this app with
`VITE_INJECTED_SECRET` in the environment. **The key did not appear.** `envKeys` came back empty.

**Vite does not dump the environment into a bundle.** It substitutes `import.meta.env.X` **where source reads it**,
and no file in `frontend/src` reads any key — T01 `:130` forbids it. So no host variable can leak today, and the
flakiness the finding predicts cannot occur.

The control was rewritten against a **throwaway Vite project** in a temp directory that *does* read the key, since
our own source may not. What it shows is sharper than the original claim:

> **Vite inlines the key's VALUE, not its name.** The artifact held `checky-local-only` with **no trace of
> `VITE_INJECTED_SECRET`** — `envKeys` was empty, and only the real-value scan caught it.

Two consequences worth stating plainly:

- **A key-name scan cannot detect this class of leak at all.** AC1, the criterion that reads most like the task
  statement, would pass on a bundle containing an inlined secret. The real-value scan caught it only because
  `checky-local-only` is a value this repository knows. For a genuine production secret, neither the name nor the
  value is known in advance, so **no scan of the artifact could find it**.
- **Therefore the mechanism closure is not belt-and-braces — it is the actual defence.** Nothing reads
  `import.meta.env` (T01), `envPrefix` is pinned so only `VITE_` could ever be read, there is no `define` block
  and no `.env` file. The frozen brief chose mechanism-first over pattern-first on general principle; this control
  shows it was the only sound choice.

The sanitisation Kimi recommended is kept, with its comment corrected: not because a stray variable would be
dumped in, but because `{...import.meta.env}` in some future file would pull in whatever the host holds, and this
build should judge the repository rather than whose machine it runs on.

## K2 — fixed, with the direction corrected

The parser now reads to the next Markdown heading instead of the first blank line, so a reflow into a bulleted
list cannot cut the section short.

Kimi says this would "fail open". It would **fail closed**: stopping early can only *shrink* the parsed allowlist,
a smaller allowlist makes the key check stricter, and the set-equality case (`toEqual` against the four keys)
fails outright. Recorded because the difference matters when judging urgency — a guard that fails closed is a
nuisance, one that fails open is a hole.

## S2–S7, briefly

- **S2.** `//(?:[^/]*\.)?auth(?:[.:/]|$)` now matches a service name as a host label anywhere, so
  `https://auth.checky.example/x` is flagged where it was not. The case is renamed to say set equality is the real
  guard.
- **S3.** The production-mode assertion runs over **every** emitted `.js`, with at least two expected — the app
  chunk and the separately built worker, which could have been development-mode independently.
- **S4.** Closed as part of the positive control rather than with a marker string: the real bundle's `envKeys` and
  `secrets` are now asserted **against** the injected build's, so the two differ by exactly the leak. A near-empty
  chunk could not produce that contrast.
- **S5.** Files are read as `latin1` — every pattern here is ASCII, and the decode is byte-exact.
  **Corrected in phase 12 (2026-10-25):** the justification written here — "a future binary asset cannot swallow an
  ASCII secret behind replacement characters" — is **false**, and was falsified by M12 surviving twice. Node's UTF-8
  decoder resynchronises after an invalid byte, so a contiguous ASCII needle is found under either decode; and the
  two arrangements where detection genuinely fails (a needle split by a stray lead byte, a needle stored UTF-16)
  fail under `latin1` too. The change is still correct and marginally safer, but it removes no risk that exists for
  ASCII patterns. Measurements in phase 12.
- **S6.** A comment at `ENV_KEY_PATTERNS` names the limit and points at the "no `define` block" case as its
  counterweight, so a green key scan is not read as "nothing was inlined" — which K1 makes concrete.
- **S7.** Renamed to "keeps the allowlist commented, which is a smell check and not a proof", with a comment
  saying it would not notice an entry tucked under an existing reason.

## On Kimi's closing note

The review repeats that the suite "could not be executed on this host because `frontend/node_modules` is
incomplete (`vite-plugin-pwa` is missing), continuing the T12 OQ-C blocker". That blocker was closed in T12 phase
6 (`ab9ec5c9`) and the suite has run on every phase since; it ran four times during this phase. The review is
static, which is legitimate and still produced two of the three new findings — but its environmental claims should
not be carried forward as facts.

## Carried into phase 10

1. **Prove S1's fix**: a mutant restoring the old path class must fail the new regression case.
2. **Prove the positive control is load-bearing**: a mutant removing `REAL_PLATFORM_VALUES` must fail it, since
   that scan is the only thing that catches an inlined value.
3. **Mutate the guarded sources** — `vite.config.ts` (`envPrefix`, a `define` block) and `design.md` §4c — not the
   assertions.
4. **Commit before mutating.**

## Still open, unchanged

**OQ-1** — §4c declares four public keys that R58 permits; T01 `:130` forbids a source from naming the environment
object at all. K1 gives this new weight: **T01's rule is the only reason no host variable can leak today.**
Relaxing it to admit the four keys would open the inlining route that the positive control just demonstrated, and
no artifact scan could detect a secret arriving that way. Recommend keeping T01 as it is and deriving
configuration at runtime. Owner's call, before T14+ consumes a key.
