# auth · T15 — Phase 9: Review Resolution

Human Approval gate. Self-review (Phase 7, 2 findings) and independent review (Phase 8, 3
findings) both verified against source before disposition. All five converge on documentation/
comment-level items — none required a new trade-off decision, so none needed escalation beyond
the mechanical resolutions below (consistent with how purely mechanical items were resolved
directly in prior tasks, reserving `AskUserQuestion` for genuine judgment calls).

## Resolution log

| # | Source | Comment | Accepted? | Change made |
|---|---|---|---|---|
| 1 | Self-review F1 / Independent review F3 (same issue, independently found twice) | Comment at the baseline case implies the stub itself exercises an expired-lock `LOCKED` account, when only the exception *type* is shared | **Accepted** | Reworded the comment at `LoginFailureHandlerTest.java` (baseline case) to state explicitly that the expired-lock scenario is not separately stubbed, and that the equivalence is at the exception-type level only, per `AccountUserDetailsService`. |
| 2 | Self-review F2 | Five sequential blocks instead of a data-driven loop | **Rejected, with reason recorded.** | No change. The `LOCKED` case needs an extra stub (`isCurrentlyLocked`) the other four don't — a loop would need an internal branch anyway, and inline blocks keep each status→exception mapping visible at its own line, which benefits readability for what this test is specifically proving. Not a defect; a documented style trade-off. |
| 3 | Independent review F1 | Test injects exception types manually; can't catch a regression in the upstream `AccountUserDetailsService`→exception mapping | **Accepted as a documentation update, not a code change.** | Recorded below under Residual risks (extending the disposition already established at Phase 4 Finding 1) — the practical constraint is unchanged: an integration/`MockMvc` test that would close this gap is still unexecutable in this sandbox (Testcontainers still fails its Docker handshake even with Docker now present, see Phase 6 notes). No brief amendment — the frozen brief is not renegotiated post-Phase-4; this is a sharper articulation of a trade-off already approved, not a new one. |
| 4 | Independent review F2 | `DELETED` and non-existent cases share one stub in the test itself, not just in production — no independent validation of `AccountService.findLoginView`'s `DELETED` filter from this test | **Accepted as a documentation update.** | Verified the filter *is* independently tested: `AccountServiceTest.loginViewHidesDeletedAccountsLikeUnknownEmails` (line 660) asserts `findLoginView` returns empty for a `DELETED` account. Recorded below, citing that test by name. |

## Residual risks (extends Phase 4's list, frozen brief itself unmodified)

- **Session-stored exception message** (Phase 4 Finding 1) — unchanged, still the primary
  documented gap.
- **Status→exception mapping is asserted by construction, not derived from `AccountUserDetailsService`
  in this test** (Independent review Finding 1, new this phase): `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`
  constructs `LockedException`/`DisabledException`/`UsernameNotFoundException`/`BadCredentialsException`
  directly rather than driving them through `AccountUserDetailsService.loadUserByUsername` and
  Spring's `DaoAuthenticationProvider`. `AccountUserDetailsServiceTest` independently covers the
  `UserDetails` flags (`isEnabled`/`isAccountNonLocked`) those exceptions derive from, but nothing
  currently asserts the flag→exception translation itself (that's Spring Security's own framework
  behavior). A regression in either layer would not be caught by T15's test alone. Closing this
  fully would require an integration or `MockMvc`-level test — out of scope per the frozen brief's
  Scope > Out and still impractical in this sandbox (Testcontainers/Docker handshake issue,
  unresolved — see `docker-testcontainers-handshake-issue` memory). Owner: whoever next needs the
  full end-to-end chain verified should start by fixing the Testcontainers handshake, not
  re-litigating this test's scope.
- **`DELETED` filter is validated by `AccountServiceTest`, not this test** — confirmed, not a gap:
  `loginViewHidesDeletedAccountsLikeUnknownEmails` covers it directly.
- `contracts/api/auth.yaml` still absent (tracked since T11).

## Build verification

`mvn -pl services/auth clean test-compile` — zero errors.
`mvn -pl services/auth test -Dtest=LoginFailureHandlerTest` — `Tests run: 11, Failures: 0, Errors: 0`.

## Specification references

- Frozen brief: `04-frozen-task-brief.md`.
- Self-review: `07-self-review.md`. Independent review: `08-independent-review.md`.
- Requirements: R21. LOCKED decisions: L5.
