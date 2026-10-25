# frontend · T15 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md`, the implementation |
| **Produces** | `artifacts/07-self-review.md` |

Adversarial review of my own phase 6. Nothing changed here; findings resolve in phase 9 after Kimi's phase 8. Two
were demonstrated by running code.

## S1 — The parameter set is never asserted to be *exactly* the eight. **Medium.**

Every individual parameter is checked — `response_type`, `client_id`, `redirect_uri`, `scope`, `state`, `nonce`,
`code_challenge`, `code_challenge_method` — but **nothing asserts there are no others**. A ninth parameter would
pass the entire file: `prompt=login`, `login_hint=<an email>`, a stray debugging value, or anything a future edit
appends. For a request that leaves the browser, "no parameter I did not intend" is the security-relevant half, and
this suite only checks the half I did intend.

This is the project's own default-deny pattern (T11 states the cacheable set and negates it; T13 uses set equality
over the URL allowlist), and I did not apply it to the thing most obviously enumerable.

**Resolution for phase 9:** assert `[...searchParams.keys()].sort()` equals the eight expected names exactly.

## S2 — The "percent-encoded" leak case is vacuous, and the decode it exercises is dead logic. **Medium.**

The leak matcher is fed four synthetic URLs, one of them "percent-encoded" via `encodeURIComponent(verifier)`.
Measured:

```
plain query:        true
single-encoded:     true
DOUBLE-encoded:     true
```

All three pass **for the same reason**: a code verifier is 43 characters from the RFC 7636 unreserved set, and
`encodeURIComponent` escapes **nothing** in that set. So `encodeURIComponent(verifier) === verifier`, the
"percent-encoded" row is byte-identical to the plain row, and it tests nothing additional.

It follows that the `decodeURIComponent(url)` inside the matcher **cannot ever be the reason a verifier is found** —
a verifier is URL-safe by construction, so percent-encoding is not a channel that can hide it. The decode is dead
logic for its stated purpose, and it brings a real cost (S3).

**Resolution for phase 9:** drop the redundant row and replace it with one that tests something — and state in the
matcher's comment *why* no decode is needed, rather than leaving a line that looks load-bearing and is not. This is
the same discipline as T13's `latin1` correction: a guard must not imply more than it buys.

## S3 — The matcher throws instead of answering. **Low.**

`decodeURIComponent` raises `URIError` on a malformed escape. Demonstrated:

```
urlLeaksVerifier('https://x?a=%E0%A4%A', v)   →  THROWS: URIError
```

A predicate used as a negative control must *report*, not crash: a thrown error in the "real URL does not leak"
direction would surface as a test error rather than a leak verdict, and in a future caller it would be worse. The
real URL is always well-formed so it is not reachable today, but combined with S2 the honest fix is to remove the
decode entirely rather than guard it.

## S4 — Nothing asserts a second request replaces the first. **Low, a real gap.**

The frozen brief says "each call overwrites the previous attempt, which is correct — only the most recent authorize
request may be completed." **No case checks it.** If the implementation ever wrote under a key derived from the
state, or skipped the write when a value was already present, a **stale verifier** would survive a re-attempt and
the next callback would exchange the wrong one — a failure that looks like a server problem.

**Resolution for phase 9:** two sequential calls; assert storage holds the second's `state`, `nonce` and verifier,
and that exactly three keys remain.

## S5 — The `location` stub supplies only `origin`. **Low.**

`vi.stubGlobal('location', { origin: 'https://checky.example' })` is enough for today's module, which reads nothing
else. But if a later edit read `location.href` or `location.pathname`, the stub would yield `undefined` and the
case would keep passing while the production path broke. The stub is narrower than the global it replaces.

**For phase 9:** give the stub the fields a `Location` plausibly has, or assert the module reads only `origin`
(a source-level check), so the narrowness is pinned rather than incidental.

## S6 — Limits of a substring matcher, stated rather than implied. **Info.**

Measured, the matcher does **not** detect:

| Leak shape | Detected |
|---|---|
| verifier base64-encoded into a parameter | **no** |
| verifier split across two parameters | **no** |

Neither is a realistic path for code we write — both require deliberately transforming the value — but a reader
should not infer from "the matcher finds a leak" that it finds *any* leak. The structural guard is stronger than
the textual one: the verifier is absent from `AuthorizeRequest` entirely, so a caller cannot put it anywhere.

## S7 — `toHaveLength(43)` couples T15's cases to T14's default. **Info.**

`state` and `nonce` are asserted to be 43 characters, which is really "whatever `randomUrlSafeToken()`'s default
byte length yields". If T14's default changed, these cases would fail for a reason that is not T15's defect. Minor,
and arguably correct coupling — 43 characters is a property worth pinning — but worth naming so a future failure is
read correctly.

## What I checked and found sound

- **AC3 is the sharpest case and it is genuine**: the stored verifier is read back from sessionStorage and its
  challenge recomputed, so a module storing one pair and sending another's would fail here and nowhere else.
- **Both AC11 cases are self-verifying.** If `vi.doMock` failed to apply, the real `generatePkcePair` would succeed
  and `rejects.toThrow` would fail — a pass proves the mock took effect.
- **The storage-write ordering holds**: all three `setItem` calls sit below the `await` and below the validity
  check, and both rejection cases assert storage is empty afterwards.
- **`checky-spa` is counted, not merely found** (exactly one occurrence), so it cannot drift into a second literal.
- **The source assertions target defects, not mentions** — `/['"`]S256['"`]/`, `/\blocalStorage\s*[.[]/` — which
  is why no copy of T14's comment scanner was needed.
- **The scope matcher is fed eight inputs** including reordered and duplicated, so the equality is not a `contains`
  in disguise.
- **T14's 67 cases still pass** after the `pkce.ts` change, and that gate was run before T15 existed.

## Summary

Seven findings: **two medium** (S1 — no default-deny over the parameter set; S2 — a vacuous leak row whose decode is
dead logic), three low, two informational. **S1 is the one that matters**: it is the only finding where a real
defect could ship unnoticed. Nothing requires a change to the frozen brief. All carried to phase 9.
