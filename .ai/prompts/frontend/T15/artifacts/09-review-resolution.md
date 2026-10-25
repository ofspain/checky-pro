# frontend · T15 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Kimi's phase 8 **corroborated all seven** of my phase-7 findings and added one consolidated finding that merges
S2 and S3 into a concrete fix. **All eight are applied.** 37 → **42 cases**.

## Disposition

| # | Finding | Source | Disposition |
|---|---|---|---|
| S1 | parameter set never asserted to be exactly the eight | mine; Kimi: "the most important defect" | **FIXED** — set equality + a negative control |
| S2 | the "percent-encoded" leak row is vacuous | mine, corroborated | **FIXED** — row replaced by two that test something |
| S3 | the matcher throws instead of answering | mine, corroborated | **FIXED** — decode removed (New 1) |
| New 1 | the matcher should not decode at all | Kimi, merging S2+S3 | **FIXED, after verifying it loses nothing** |
| S4 | nothing asserts a second request replaces the first | mine, corroborated as a real gap | **FIXED** |
| S5 | the `location` stub is narrower than the global | mine, corroborated | **FIXED** — both halves |
| S6 | substring matcher cannot see an encoded or split verifier | mine, corroborated as by design | **DOCUMENTED in the test file** |
| S7 | `toHaveLength(43)` couples T15 to T14's default | mine, informational | **FIXED differently from the suggestion** — see below |

## The fix that needed verifying first

Kimi's New 1 says to replace `decodeURIComponent(url).includes(verifier)` with `url.includes(verifier)`, and claims
every leak case still passes. **Verified before accepting**, because a matcher that quietly stopped detecting
something would be worse than the crash it fixes:

```
with the decode REMOVED (url.includes):
  plain query param:       true
  in the fragment:         true
  nested redirect_uri:     true
  in a path segment:       true
  repeated param:          true
malformed escape:          ok, returns false   (previously: URIError)
```

Nothing is lost and the crash path is gone. The matcher's comment now states **why** no decode is needed — the
unreserved set survives `encodeURIComponent` unchanged, so percent-encoding is not a channel that can hide a
verifier — rather than leaving a line that looks load-bearing and is not. Same discipline as T13's `latin1`
correction.

## S1, the one that mattered

```ts
expect([...parameters(url).keys()].sort()).toEqual([
  'client_id', 'code_challenge', 'code_challenge_method', 'nonce',
  'redirect_uri', 'response_type', 'scope', 'state',
]);
```

Plus a negative control: the same expression over a URL with `&login_hint=someone@example.com` appended must
differ. Without the control, the equality is a shape nobody has shown can fail.

The example is deliberate — `login_hint` carrying an email address is the realistic way this request would start
leaking PII into a URL, which `agents.md:79-81` forbids outright.

## S2, replaced rather than deleted

The vacuous row is gone and **two** rows take its place: the verifier **in a path segment** (not a query string at
all, so it proves the matcher reads the whole URL) and **as a repeated parameter**. Both fail against a matcher
that only iterated `searchParams` values.

## S5, fixed in both directions

Kimi's two options were "expand the stub" or "pin with a source-level assertion". **Both are applied**, because
they fail for different reasons: the stub now carries the fields a real `Location` has, and a new case asserts
that **every `location.<property>` access in the module source is `location.origin`** — so the stub's narrowness is
pinned rather than incidental. If a later edit reads `location.href`, the source case fails immediately rather
than the stub silently yielding `undefined`.

## S7, where I declined the suggested fix

Kimi suggested deriving the expected length from `randomUrlSafeToken().length` **instead of** the literal 43. That
would make the assertion survive a change in T14's default — which is precisely what it should **not** do: 43
characters (256 bits) is a property worth pinning, and an assertion that follows whatever the default becomes
pins nothing. If the default silently dropped to 16 bytes, Kimi's version would still pass.

So both are asserted: the literal **and** the derived form, with a comment naming the coupling. A failure after a
`pkce.ts` change is then informative rather than noise, and a weakening of the default still fails.

## Gates after the fixes

| Check | Result |
|---|---|
| `authorizeRequestsExactlyTheDeclaredScopes.test.ts` | **42/42** |
| **T14's suite** (the standing D-2 obligation) | **67/67** |
| `vitest run src/auth` | **109/109** |
| `vitest run` (whole suite) | **521/523** — 481 + 42, delta exactly 42 |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

The two failures remain T07's `lintRejectsHandWrittenBackendFetch` AC4 and AC2 — environmental, untouched.

## Kimi's closing note is wrong again, for the sixth time

The review ends: *"the suite could not be executed on this host because `frontend/node_modules` is incomplete
(`vite-plugin-pwa` is missing), continuing the T12 OQ-C blocker."* **T12 closed that in its phase 6**
(`ab9ec5c9`). Rejected with evidence in T12 ph3, T13 ph3, T14 ph3, T14 ph8 and T15 ph3; this is the sixth. The
suite has run at every phase of this task and every number above is measured. Nothing in the findings depends on
the claim, so it costs nothing — but six repetitions is a pattern, not an accident, and it is recorded as such.

## Still open, for the owner

**OQ-1** — T01 `:130` versus `design.md` §4c's four keys. **T15 reads no environment key**, so the conflict stays
latent; phase 0 corrected my earlier claim that this task was blocked by it. On the same evidence T16 is unlikely
to need one either.
