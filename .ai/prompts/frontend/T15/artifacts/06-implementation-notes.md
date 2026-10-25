# frontend · T15 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/05-implementation-plan.md` (and, through it, the frozen brief) |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

| File | Size | Contents |
|---|---|---|
| `frontend/src/auth/authorizeRequest.ts` | 98 lines | 4 constants, 1 type, 2 private helpers, 1 public function |
| `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` | 304 lines | **37 cases** |
| `frontend/src/auth/pkce.ts` | +9 / −4 | `randomUrlSafeToken` added; `generateCodeVerifier` delegates (D-2) |

## Gates

| Check | Result |
|---|---|
| `vitest run src/auth` | **104/104** — T14 **67** + T15 **37** |
| **T14's suite alone** (the D-2 regression obligation) | **67/67** |
| `vitest run` (whole suite) | **516/518** |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

**AC10, measured.** Baseline immediately before this task was **479/481**; now **516/518**. The delta is **exactly
37**, the same two failures remain — T07's `lintRejectsHandWrittenBackendFetch` AC4 (no `oxlint` summary line on
this host) and AC2 (`spawn pnpm ENOENT`) — and no other case changed state. T01 still passes with a new production
source file present, so the new module satisfies its no-absolute-URL and no-environment-key rules.

## D-2's regression gate was run first, deliberately

Phase 5 put the `pkce.ts` change at step 1 and required T14 green **before a line of T15 was written**, so a
regression there could not later be mistaken for T15's fault. Result: `67/67` immediately after the edit, before
`authorizeRequest.ts` existed. T14's CSPRNG spy ("called once, with a `Uint8Array` of 32 bytes") and its
`btoa(`-in-source assertion both still hold, which is exactly why the helper stayed inside `pkce.ts`.

## One change to the module beyond the plan, and why

**`buildAuthorizeRequest` now validates the pair it receives** —
`if (!isValidCodeVerifier(pkce.verifier)) throw new TypeError(...)`. Writing the AC11 case exposed that the frozen
brief's wording could not be satisfied as the plan had it: D-6 says "mock `generatePkcePair` to return an invalid
verifier and assert rejection", but a function that merely *passes through* whatever its collaborator yields would
**not** reject — it would store the bad verifier and build a URL, and the failure would surface at the token
exchange, far from the cause. One line makes the function defend its own contract instead of trusting a
collaborator, and makes AC11 testable as written.

This is within the frozen brief (AC11: "rejects on an invalid verifier"), not an expansion of it.

## Decisions taken while writing

1. **Source assertions are anchored to usage, not mention — and that avoided duplicating T14's scanner.** The
   module's doc comments discuss `S256` and `plain` on purpose, so a bare `toContain('S256')` would fail on its own
   documentation. T14 solved the general problem with a string-aware comment scanner, but that scanner lives in
   T14's test file, which this task may not modify, and copying it would be the `segmentPrefixPattern` mistake
   again. Instead each assertion targets the *defect*: `/['"`]S256['"`]/` for a hardcoded method,
   `/\blocalStorage\s*[.[]/` for a storage access, `/\bMath\s*\.\s*random\b/` for the wrong entropy source. Sharper
   than a scan over stripped text, and it needs no shared helper. **T01 already owns the general rules**
   (`import.meta.env`, absolute URLs) across all of `src/`, so T15 does not restate them.
2. **Storage writes come after the `await`.** The three `setItem` calls sit below `await generatePkcePair()` and
   below the validity check, so any failure leaves storage untouched. This is the ordering phase 5 flagged as risk
   1, and both AC11 cases assert `Object.keys(sessionStorage)` is empty after a rejection.
3. **`code_challenge_method` is set from `pkce.method`**, the value on the returned pair, not from a separate import
   of the constant. Same literal type, one fewer place for the two to disagree.
4. **The `checky-spa` count is asserted as exactly one occurrence**, not merely present — phase 5's risk 4. The
   constant's own declaration is that one occurrence, so a second would mean the value had drifted into a URL
   literal.
5. **`vi.unstubAllGlobals()` runs in `afterEach`**, not inside the one case that stubs `location` — phase 5's risk
   2. A leaked stub would corrupt the shared `beforeEach`, so the cleanup is unconditional rather than per-case.
6. **The AC11 cases are self-verifying.** If `vi.doMock` failed to apply, the real `generatePkcePair` would succeed
   and `rejects.toThrow` would fail — so a passing case proves the mock took effect, rather than passing because
   nothing happened.

## What the implementation does not do

- **It does not navigate.** It returns a URL; T16's sign-in screen navigates.
- **It does not read or clear the callback.** Tasks 17–20 own that, including deleting the three items.
- **It does not touch the return target** — tasks 24/25, though it is L16's fourth item.
- **It reads no configuration and performs no discovery.** Every value is derived from `location.origin` and the
  route table.

## Open Questions

**No blockers.** OQ-1 is carried for the owner and does not touch this task — T15 reads no environment key.
