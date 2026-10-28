# frontend · T22 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 (human approval gate) |
| **Consumes** | `artifacts/08-independent-review.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

All nine Phase 8 findings resolved. **I1-I5 and I7-I8 accepted**, **I6 accepted with its mechanism corrected**,
**I9 acknowledged as the planned phase boundary**. Four decisions added: **T22-D12..D15**.

---

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| **I1** | the success case searched for an ID token that never entered the system | **Accepted** — same as self-review F1. **T22-D13** below. |
| **I2** | the failure matrix never had a token to lose | **Accepted** — same as self-review F2. The table sends a **valid** ID token on every row whose failure is elsewhere; only the bad-nonce row sends a mismatched one. |
| **I3** | `title`, `history.state`, `window.name` are empty-box assertions | **Accepted.** A sentinel per surface, read back through the **same** sweep function, plus the sentence saying what that does and does not prove (self-review F3). |
| **I4** | the DOM sweep read only the mounted container | **Accepted.** The sweep reads `document.documentElement.outerHTML` — whole document, attributes included — and a sentinel placed **outside** the container proves it. |
| **I5** | four areas unmeasured | **Accepted.** A throwing store (AC11), the synthetic `Bearer <token>` (AC15), two sign-ins in one tab, and a sweep after `clearAccessToken()`. I5's own reason for the two-sign-in case is the right one: `authorizeRequest.ts` **overwrites** the three items, so a second sign-in is the one path where a stale value could survive. |
| **I6** | the scan can miss aliased storage and console references | **Accepted, mechanism corrected.** See below. |
| **I7** | jsdom's cookie jar cannot prove much | **Accepted.** The frozen brief §7's limitation goes into the test file's header, in the file's own words: the cookie claim is a sweep of `document.cookie`, not proof against a `Set-Cookie` response header or an `httpOnly` cookie. |
| **I8** | the allowlist should be a table, not a regex | **Accepted. T22-D15** below. |
| **I9** | nothing committed yet proves the claim | **Acknowledged.** It is the planned boundary: Phase 6 measures, Phase 10 commits. Recorded so the gate reads Phase 8 as a methodology review, which is what it is. |

## I6, corrected

**What is wrong with the finding.** It recommends adding `globalThis.console` and `window.console` to the
forbidden patterns. The scan already forbids `/console\./`, and `globalThis.console.log(token)` **contains**
`console.` — so the dotted alias was never a hole.

**What is genuinely uncovered**, and no regex closes it honestly: `globalThis['console'].log(token)`,
`const c = console; c.log(token)`, and the same two shapes for storage. A scan is text; an alias is resolved at
runtime.

**So the answer is not a cleverer pattern.** It is that T22 carries **two** instruments and they fail
differently — **T22-D14**:

- The **runtime** sweep spies `Storage.prototype.setItem/removeItem/clear` and all six `console` channels. Both
  are **alias-proof by construction**: a write through `globalThis['localStorage']` still calls
  `Storage.prototype.setItem`, and `c.log` where `c === console` is still the spied method. This is the primary
  instrument.
- The **source scan** catches the shapes someone thought of, over code the runtime sweep never executes. Second
  net, not first — the same relationship `bundleContainsNoSecretEnvKeys` states about itself.

Phase 10 proves the first claim rather than asserting it: a control writes through
`(globalThis as Record<string, unknown>)['localStorage']` and calls `globalThis['console'].log(token)`, and the
**same** spies catch both. A bracket-form row is added to the scan as well, since it costs one line.

### And the third instrument that exists but is not T22's to use — T22-D12

`.oxlintrc.json` already bans `fetch` and `XMLHttpRequest` through `no-restricted-globals` and
`no-restricted-properties`, and `lintRejectsHandWrittenBackendFetch.test.ts:31-42` proves that mechanism
**catches the bracket forms** (`window['fetch']`, `globalThis['fetch']`). Adding `localStorage`, `indexedDB` and
`openDatabase` as restricted globals would be a durable, zero-exception, alias-proof static guard for L3 — nothing
in the package legitimately uses any of the three.

**T22 does not do it**, for a measured reason: `lintRejectsHandWrittenBackendFetch.test.ts:196-214` (T18's AC5)
pins that config by **set equality** — `expect(globals.map(e => e.name).sort()).toEqual(['XMLHttpRequest',
'fetch'])` — and requires every entry's message to be exactly L6's wording. Widening the config therefore means
editing another task's acceptance criteria and deciding that the lint config enforces **L3 as well as L6**. That
is an owner's call about the instrument's remit, not a side effect of a verification task.

**Recorded as a handoff** with the exact cost: three entries in `.oxlintrc.json`, one updated set-equality
assertion and one new message constant in T18's test, in exchange for an alias-proof static guard. `sessionStorage`
cannot join them — `authorizeRequest.ts` legitimately writes it, and an inline disable comment is exactly what
T18's AC7 forbids adding casually.

## Decisions added

| ID | Decision |
|---|---|
| **T22-D12** | **T22 does not widen `.oxlintrc.json`.** The route is proven and attractive, and the cost is T18's AC5 set-equality plus a decision that the lint config enforces L3; recorded as a handoff, not taken. |
| **T22-D13** | **Every searched value is derived from what the flow actually carried** — the stub's own response body and the values the real authorize step generated — never from a constant declared beside the assertion. A guard case asserts the searched value equals what was sent, so the mismatch that made Phase 6's ID-token claim vacuous cannot recur silently. |
| **T22-D14** | **Two instruments, stated as such.** The runtime spies on `Storage.prototype` and the console channels are the primary, alias-proof instrument; the comment-stripped source scan is the second net over code no test executes. Phase 10 proves the alias-proofness with a bracket-form control rather than claiming it. |
| **T22-D15** | **The storage allowlist is a structured table** — file path → permitted methods → reason — asserted entry by entry, with a failure message naming the offending file, the method, and the instruction to add an entry with a justification. Task 23 is the expected next entrant. |

## What did not change

No production file. The measurement stands: one storage writer in 23 files, writing exactly the three items L16
names, and no token in any surface across the success path, nine failure paths and a sibling-tab adoption
(T22-D7). Every finding in both reviews is about the instrument, and the instrument is Phase 10's.

---

## Next — Phase 10 — test generation.
