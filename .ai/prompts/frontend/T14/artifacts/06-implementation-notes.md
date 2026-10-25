# frontend · T14 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/05-implementation-plan.md` (and, through it, the frozen brief) |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

| File | Size | Contents |
|---|---|---|
| `frontend/src/auth/pkce.ts` | 97 lines | 3 constants, 1 type, 2 private helpers, 4 exported functions |
| `frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts` | 237 lines | **44 cases** |

**First module under `src/auth/`.** No file modified — the plan's *Files to modify: none* held.

## Gates

| Check | Result |
|---|---|
| `vitest run src/auth` | **44/44** |
| `vitest run` (whole suite) | **456/458** |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

**AC8, measured.** Baseline immediately before this task was **412/414**; it is now **456/458**. The delta is
**exactly 44**, the number of cases added, the same two failures remain, and no other case changed state. Those two
are T07's `lintRejectsHandWrittenBackendFetch` **AC4** (this host's `oxlint@1.87.0` prints no summary line) and
**AC2 end to end** (`spawn pnpm ENOENT`) — environmental, failing since T07, untouched here.

## Risk 1 from the plan, discharged: the χ² threshold transfers to jsdom

Phase 4 derived `CHI_SQUARE_THRESHOLD = 139` by measuring Node's `randomBytes`, while the suite runs on jsdom's
`crypto.getRandomValues`. The plan required confirming the transfer rather than assuming it, so
`vitest run src/auth` was run **15 times**:

```
runs with a failure: 0/15        (44/44 every time)
```

Both are CSPRNGs and base64url is deterministic, so this is the expected result — but it is now measured on the
environment the suite actually uses.

## Decisions taken while writing, none of which change the frozen brief

1. **The forbidden-name scan strips comments first.** The module's doc comments quote the rules on purpose — they
   name `Buffer`, `% 66`, `sessionStorage` and `plain` in explaining *why* each is absent. A raw `toContain` over the
   file would therefore fail on its own documentation. The scan removes block and line comments, then asserts over
   the remaining code. This is a real trap: the naive version of this test forbids the module from explaining
   itself.
2. **`history` and `location` are in the forbidden list but would never appear anyway**, because this module builds
   no URL. They are there for the reason D-9 gives: a URL primitive in *this* module is a defect regardless of
   intent. Noted because a reader may wonder why a module with no URL code scans for URL APIs.
3. **AC5 got a second, stronger assertion than the brief asked for.** "A one-character change changes the
   challenge" is satisfied by a hash that only differs in one place, so the case also asserts the two challenges
   share **fewer than half** their characters. A truncating or prefix-only hash passes the weaker form.
4. **`deriveCodeChallenge` throws `TypeError`, not a custom error class.** Nothing catches it by type; the message
   carries the bounds. A custom class would be API surface T15 does not need.
5. **The tail case asserts the exact 16 values, not just the count.** Phase 4 measured
   `048AEIMQUYcgkosw`; the test derives them as every 4th alphabet entry (the two low bits of the final sextet are
   always zero) rather than hardcoding the string, so it states the *reason* and not only the observation.

## What the implementation does not do

- **It stores nothing.** L16 permits sessionStorage to hold a `code_verifier`, but the task that writes that set as
  a unit owns it; `sessionStorage` is in the forbidden-name scan precisely so this stays true.
- **It builds no URL.** The challenge is the half that may appear in one; assembling the authorize request is T15's.
- **It reads no configuration.** Which is why OQ-1 does not touch this task — see phase 13.

## Open Questions

**No blockers.** OQ-1 (T01 `:130` vs §4c's four public keys) is carried for the owner and lands on T15/T16.
