<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). Tests only; no production code changed. -->

# frontend · T48 · Phase 10 — Test Manifest

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `10-mutation-run.txt` |

**No production file was changed in this phase.**

```
baseline whole suite: 0 failed of 2048, exit 0

regressions: 21/21 killed
canaries:    ['TR18', 'TR19'] - none survived
survivors:   ['TR20']
broken:      none
disagreements: none
```

---

## How this phase chose what to write

The harness was **17/17 entering this phase**, so "find the survivors" was unavailable. The question became
which criteria are written down and measured nowhere — and the answer came from the freeze's own
**Constraints** section, which says the token is

> *not stored, not logged, not sent to analytics, not rendered*

**Only the first of those four had an instrument.** Three of the five cases below exist because a sentence in
the brief was doing the work an assertion should have been doing.

## The five cases added

| Case | Criterion | Why nothing held it |
|---|---|---|
| `never renders the token, in ANY state` | L16 / brief constraints | A credential on screen is shoulder-surfable and lands in every DOM snapshot, screenshot and serialised error report. The **success** path was covered *by accident* — the equality assertions elsewhere exclude the token incidentally — while **`idle` was not covered at all**, and `idle` is where a visitor sits while the request is in flight. |
| `logs nothing at all` | brief constraints | Behavioural where the existing check is structural. The source scan forbids the literal `console.`; this forbids a console call **however it is spelled** — through an alias, a re-export, or a logger a later edit adds. `tokensAreNeverWrittenToAnyStorage` makes the same argument for access tokens. |
| `sends a POST with a JSON content type` | AC3 / the contract | The transport recorded the path, body and `authorization` and **not the verb or the content type**. A correct body on the wrong verb, or with a type the server will not parse, fails in production and passed here. |
| `verifies from a link with a trailing slash` | AC1 | `publicRouteSetIsExactlyTheDeclaredSet` already pins that `/app/verify/` **resolves** — but against a **synthetic table of probes**, because that path had no element when the case was written. It has one now, so the claim can finally be made end to end: a mail client that appends a slash gets a verified account, not a uniform failure. |
| `takes the FIRST token when a link carries two` | AC2 | `?token=a&token=b` is a URL anyone can build, and `get` versus `getAll().join()` is **one method name apart**. First-wins is also the safer branch: a token appended to a legitimate link is ignored rather than concatenated into something the server might parse. |

## The four harness rows added

| Row | Outcome | Purpose |
|---|---|---|
| **TR21** the token rendered into the heading | `KILLED`, 6 cases | |
| **TR22** the token logged through an **alias** | `KILLED`, 1 case | **Written to evade the source scan on purpose.** The scan forbids the literal `console.`; `const sink = console;` does not contain it, so only the runtime console watcher can see this. A mutant that died to the scan would have measured the scan rather than the new case. |
| **TR23** the content type overridden | `KILLED`, 1 case | |
| **TR24** repeated tokens joined | `KILLED`, 1 case | |

Three of the four die by **exactly one case** — the new one — which is what isolation looks like.

## One assertion that is unfalsifiable, declared rather than dressed up

**That the request is a `POST` has no row.** openapi-fetch exposes the verb as the method name and the
contract declares no `GET` for this path, so a mutant changing it **cannot typecheck** — and a mutant that
cannot compile measures nothing (T36 scored one as a kill before noticing). The assertion is kept because it
costs a line and would catch a hand-rolled call, but nothing here proves it bites. This is the same honest
label T47's Phase 11 had to put on its widened `role=` selectors.

## Full coverage map

| AC / rule | Cases |
|---|---|
| **AC1** | success at `/app/verify?token=…` · **trailing slash, end to end** · stays on the screen |
| **AC2** | the token from the URL is the body · base64url round trip · the `+` latent trap · **first of two** |
| **AC3** | one public POST · no `Authorization` · **method and content type** · `createPublicAuthApi` in source |
| **AC4** | success on 204 · **and on a 200** (holds `response.ok`) · the response is not ignored |
| **AC5** | both keys as literal English · no untranslated key · nothing the server sent |
| **AC6** | focus on arrival · focus **with no token** · the region mounted empty **before** it is filled · no tab stop · a body control |
| **AC7** | one request · one under StrictMode · the guard **before** the request (source order) · **a token arriving after a tokenless visit** (the guard's meaning) |
| **AC8** | `Storage.prototype` spy + intercept control · **the token in no DOM, in any state** · **nothing logged** |
| **AC9** | the declared set unchanged · attached by constant |
| **AC10** | no lowercase tag · no props · the scoped source scan |
| **AC11** | 2048/2048, and every touched file run alone |
| **AC12** | no token · empty token · **PINS A LIMIT**, which must fail when task 49 lands |

## Not added, and why

- **No case for TR20** (`useEffect` → `useLayoutEffect`): a kill would mean a case had started pinning the
  hook rather than the behaviour.
- **Nothing about the failure states.** R4 is task 49's and inventing its copy would pre-empt it.
- **No analytics assertion.** The brief forbids sending the token to analytics; the SPA has no analytics
  client, so there is nothing to forbid it *to*. Recorded rather than faked — a scan for a module that does
  not exist is a pattern aimed at nothing.

**Phase 10 complete. Next is Phase 11 — Test Review (Kimi 2.7). STOP HERE.**
