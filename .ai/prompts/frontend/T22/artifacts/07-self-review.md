# frontend · T22 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Re-read the Phase 6 measurement against the frozen brief, and re-read the probe's own source rather than its
output. The headline holds — **no leak** — but the probe is weaker evidence than Phase 6 claimed, in one place
materially.

---

## F1 · The success case's ID-token claim was measured against the wrong string

**Severity.** High. This is the package's recurring defect class, in my own probe.

**Evidence.** Probe A built the ID token from the **real** nonce the authorize step generated, because that is
what the nonce check requires:

```
const nonce = sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)!;
const idToken = tokenWith({ nonce, … });
await arriveAt(…, () => jsonResponse(tokensBody({ id_token: idToken })));
```

But the report searched every surface for the module constant `ID_TOKEN`, minted from `STORED_NONCE`:

```
say(`ANY SURFACE CONTAINS id token: ${all.includes(ID_TOKEN)}`);
```

Those are **different strings**. So "ID token absent" in the success case was a search for a token that never
entered the system — vacuous, and it would have reported `false` with the real ID token sitting in
`localStorage`.

**Partially saved by accident, and the detail matters:** a JWT's three segments are header, payload, signature.
`tokenWith` builds the same header for both and the same filler signature, so **two of the three segment searches
were valid** and did measure real absence. Only the whole-token search and the **payload** segment — the one
carrying the claims — were testing nothing.

**Disposition.** Accepted. Phase 10 must take the token values it searches for from **the response body the stub
actually returned**, never from a constant declared beside it. The access-token half was sound only because the
success case happened to send the constant unchanged; that is luck, not design, and the ID token is proof of it.

## F2 · Probe B never had a usable token to lose

**Severity.** Medium, and it is a scoping clarification rather than an error.

**Evidence.** Every row of Probe B sent the default `tokensBody()`, whose `id_token` carries `STORED_NONCE` —
while the real authorize step had written a fresh random nonce. So even the rows that reached the nonce check
(refused code, 5xx, dropped connection, unusable response, bad nonce) could never have produced a held token.
`held: null` across all nine rows is therefore the correct outcome, but for two reasons at once, and the dump
cannot tell which.

**Disposition.** Accepted as a Phase 10 requirement: the failure table must send a **valid** ID token on the rows
whose failure is elsewhere (a 5xx, a dropped connection, a refused code), so that the only reason nothing is held
is the failure under test. A row that fails twice measures neither failure.

## F3 · `document.title`, `history.state` and `window.name` are still empty boxes

**Severity.** Medium — already named as Trap 3 in Phase 6, repeated here because the fix is not yet designed.

A sentinel is the obvious instrument, but a sentinel written into `document.title` before the flow and found
afterwards proves only that the sweep reads `document.title` — not that the flow would have been caught writing a
token there. That is still worth having, and it is the honest maximum: **the sweep can prove it reads a surface;
it cannot prove the surface is reachable by a leak that does not exist.** Phase 10 should say so rather than
imply more.

## F4 · The DOM sweep read only the mounted container

**Severity.** Low.

`report()` searched `container.innerHTML`. A React portal, a `document.body`-level node, or an attribute outside
the container would not be seen. Nothing in this package uses a portal today, so the measurement stands, but the
committed sweep should read `document.documentElement.outerHTML` — the whole document, attributes included —
because that is the surface a reader will assume was checked.

## F5 · Four of the brief's required cases were never measured

**Severity.** Medium. Listed so nothing is assumed proven:

| Not measured | Where it lands |
|---|---|
| **AC11** — a *throwing* store (`storeEntries` calls `key()` and `getItem()` unguarded; blocked site data throws) | Phase 10 |
| **AC15** — the synthetic `Bearer <token>` write | Phase 10 |
| Two sign-ins in one tab, no residue from the first | Phase 10 |
| A sweep after `clearAccessToken()` | Phase 10 |

**Disposition.** Accepted; all four are Phase 10's, and none changes the "no leak" finding.

## F6 · The source scan can be fooled, and should say so

**Severity.** Low, by design rather than by defect.

The scan reads comment-stripped text, so `globalThis['localStorage']`, `'session' + 'Storage'`, or a storage
reference obtained from a variable all pass it. This is the same limit `bundleContainsNoSecretEnvKeys` states
about itself in its own header — pattern matching catches the shapes someone thought of. The real defence here is
that there is **one** storage writer in 23 files and the runtime sweep watches `Storage.prototype` rather than a
name. Phase 10's header should carry that sentence, next to T22-D1's three.

## F7 · "No production change" deserves a stronger statement than T22-D7

**Severity.** Low.

T22-D7 says a verification task may end with no production diff. True, but the stronger and more useful claim is
available: the measurement shows the **one** place in this service that writes to any store
(`authorizeRequest.ts:47-49`) writes exactly the three items L16 names, and nothing in 23 files can write
anywhere else. That is a structural property worth stating in the PR, not merely an absence of findings.

## Summary

The finding is unchanged: **nothing leaks.** But F1 means one of Phase 6's claims was not actually measured, and
F2 means nine others were measured for an ambiguous reason. Both are fixed by the same rule for Phase 10 — **a
sweep must search for the values the flow really used, and each failure must be the only reason its case
fails.** F3-F7 are instrument honesty: read the whole document, say what the scan cannot see, and claim the
structural property rather than the absence.

---

## Next — Phase 8 — independent review (Kimi 2.7).
