<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T31 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

**Issue: T31 cannot extend T30's forbidden-claim list without violating the test-file ownership rule.**
· Severity: **Blocker**
· Evidence: AC5 requires the token-claim scan to cover `status` and `emailVerified` in addition to T30's eight claims. In `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` the claim list is the module-level `FORBIDDEN_TOKEN_CLAIM` helper, which T30 owns. The brief says "T31 adds only `describe('T31: …')` blocks" and may touch T30 blocks only to repair compilation errors caused by renamed exports. Adding `status` and `emailVerified` to `FORBIDDEN_TOKEN_CLAIM` is neither of those.
· Recommended brief amendment: Permit T31 to extend the shared module-level helper constants (`FORBIDDEN_TOKEN_CLAIM`, and the `INSTRUMENTS` table in AC12), or explicitly require T31 to define its own claim pattern and compose it with T30's decode-scan helpers. If the shared-constant approach is chosen, document that "helpers" are shared and extensible even though `describe` blocks are owned.

**Issue: Deriving `Account` from `AccountResponse` conflicts with faithfully returning unknown statuses.**
· Severity: **Major**
· Evidence: AC2 freezes the returned type as derived from the generated `AccountResponse`, whose `status` is typed as `"PENDING_VERIFICATION" | "ACTIVE" | "LOCKED" | "SUSPENDED" | "DELETED"`. AC6 requires the five known statuses to survive unchanged, and AC8 requires an unknown status string not to be silently treated as `ACTIVE`. Returning an unknown string is the faithful behavior, but it violates the derived TypeScript type unless a cast or a widened type is used.
· Recommended brief amendment: Define `Account` as `Omit<AccountResponse, 'status'> & { status: string }` (or equivalent) so the type admits measured reality without restating the schema. Document that the reader preserves unknown strings and that downstream consumers must handle them; this keeps AC2 honest while satisfying AC6 and AC8.

**Issue: AC9 assumes the `/accounts/me` 401 shape matches T30's `/userinfo` measurement, but this was not measured for auth-service.**
· Severity: **Major**
· Evidence: AC9 says failure surfaces no detail "including from `WWW-Authenticate`, which is where the measured 401 puts it with an empty body." The cited measurement is from T30's `/userinfo` (an OIDC/SAS endpoint). `GET /accounts/me` is served by auth-service's `AccountController.me` with `@PreAuthorize("isAuthenticated()")`; its 401 body and headers may differ (e.g., RFC 9457 problem body, Spring default HTML, or a different `WWW-Authenticate` format).
· Recommended brief amendment: Measure `GET /accounts/me` with no bearer and with a malformed bearer against the running auth service before freezing AC9. Then assert whichever shape is real: if detail is in a body, assert the body is not surfaced; if in a header, assert the header is not surfaced. Do not inherit the OIDC measurement by analogy.

**Issue: OQ-T31-3 is listed as "pending owner input" in Phase 1 but treated as decided in Phase 2 without a cited artifact.**
· Severity: **Major**
· Evidence: `artifacts/01-specification-extraction.md:156-161` records OQ-T31-3 (what the SPA does with a non-ACTIVE status) as "pending owner input". `artifacts/02-task-implementation-brief.md:44-45` states "Owner decision, 2026-10-31: T31 is the read; the gap is logged as OQ-T31-3 and assigned to no unit" without citing the decision artifact or a `spec/` update.
· Recommended brief amendment: Cite the artifact or spec change that records the owner decision (e.g., an updated `tasks.md` note, a frozen brief, or an ADR), or keep OQ-T31-3 in the brief's Open Questions and explicitly note it is unresolved. Do not remove it from blockers by assertion alone.

**Issue: AC7's "missing or empty" validation is ambiguous for the boolean `emailVerified`.**
· Severity: **Major**
· Evidence: AC7 requires each required field, "missing or empty in turn, to be rejected." `emailVerified` is a `boolean`. `false` is a valid, contract-required value; rejecting it would violate AC6 (faithful read) and the contract. "Empty" has no meaning for a boolean, so a literal implementation of AC7 could wrongly reject `false`.
· Recommended brief amendment: Clarify AC7: for `emailVerified`, reject `undefined`, `null`, and non-boolean values, but never reject `false`. Add a control proving `emailVerified: false` is accepted and returned as `false`.

**Issue: T31 needs a `createAuthApi` harness, but T30's shared harness only wires `createOidcApi`.**
· Severity: **Minor**
· Evidence: T31's reader calls `GET /accounts/me` through `createAuthApi`. T30's `harness()` helper in the test file constructs `createOidcApi`. AC3/AC4/AC11 require the same boundary/renewal/rate-limit assertions for the auth client. Extending the helper to support both factories is a change to a T30-owned helper.
· Recommended brief amendment: Decide whether T31 creates a sibling `authHarness` helper or extends the existing `harness` with a `client: 'oidc' | 'auth'` option. If extending, explicitly permit T31 to modify the shared helper.

**Issue: AC10 omits `document.body.innerHTML` and `document.head.innerHTML` from the swept surfaces.**
· Severity: **Minor**
· Evidence: T30's leak sweep covers body, title, head, window.name, URL, sessionStorage, localStorage and all six console channels. The brief's AC10 lists "sessionStorage, localStorage, document.title, window.name, the URL and all six console channels" but not body or head. An account value written into the DOM body or head would therefore not be asserted absent.
· Recommended brief amendment: Align AC10 with T30's surface list, explicitly including `document.body.innerHTML` and `document.head.innerHTML`.

**Issue: AC7 does not explicitly require rejecting a JSON-`null` body.**
· Severity: **Minor**
· Evidence: The Constraints section warns that "A body that is JSON `null` must not reach a property access: that exact shape threw a raw `TypeError` out of `readProfile` and was a shipped defect found in T30 phase 11." AC7 currently speaks only of "missing or empty" fields. A `null` body has no fields and could slip past a per-field check.
· Recommended brief amendment: Add to AC7: "A JSON-`null` body is rejected as `unavailable` without reaching any property access."

**Issue: AC5's wording "anywhere in the tree" could be read as a tree-wide claim scan.**
· Severity: **Minor**
· Evidence: The brief later clarifies that the scan composes T30's decode scan with a claim scan over only the decoding file set. However, the phrase "anywhere in the tree" appears in AC5 and could be misread as requiring a tree-wide scan for `status` and `emailVerified` — both ordinary words that appear in commissioned code (`response.status`, `AccountResponse.emailVerified`).
· Recommended brief amendment: Rephrase AC5 as: "Neither `status` nor `emailVerified` is read from any token, in any file that decodes a token, proven by composing T30's decode scan with a claim scan over that bounded set."

**Issue: No decision on whether `readAccount` reuses `ReadProfileError` or defines its own error class.**
· Severity: **Minor**
· Evidence: The brief says the failure is "a neutral error whose message cannot hold detail" and references the `ReadProfileError` pattern, but does not decide whether T31 imports `ReadProfileError`, renames it to a shared neutral error, or creates `ReadAccountError`. This affects whether AC8/AC9 tests are duplicated and whether failure codes are generic or scoped.
· Recommended brief amendment: Freeze the choice: either (a) T31 creates `ReadAccountError` with its own three failure codes, or (b) T31 reuses a renamed shared `ReadError` with generic codes. If reusing, document that the failure codes apply to both readers.

**Issue: The brief does not decide whether `readAccount` short-circuits on a null token.**
· Severity: **Minor**
· Evidence: T30 short-circuits because `/userinfo` returns 302 with a side-effect. `/accounts/me` returns 401, which the boundary handles. A short-circuit would avoid one request and one failed renewal, but the brief neither requires nor forbids it.
· Recommended brief amendment: Decide and document the behavior: if `tokens.current()` is `null`, either return `unauthorized` immediately with no request, or let the boundary handle the 401. Add the corresponding test.

---

**Phase 3 complete.** Ten findings returned; no code or contract changed.
