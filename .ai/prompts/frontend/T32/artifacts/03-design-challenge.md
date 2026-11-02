<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T32 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### Issue: I2 cannot be both tree-wide for `email_verified` and permissive of `/userinfo`-sourced reads
· **Severity:** **Blocker**
· **Evidence:** Phase 2 resolves OQ-T32-2 by permitting verification state from `GET /userinfo`. `contracts/api/oidc.yaml:191-197` names the `/userinfo` field `email_verified`. A future consumer reading it from `/userinfo` would write source code containing `data.email_verified`, `response.email_verified`, or similar. I2 proposes a **tree-wide** scan for the spelling `email_verified` with an **empty allowlist** and an admission rule that "/userinfo-sourced reads may be added". I5 then requires the scan to stay silent on a synthetic `/userinfo` read.
· **Assessment:** These three statements are mutually inconsistent. A tree-wide scan for `email_verified` cannot stay silent on a `/userinfo` read unless it knows the read is `/userinfo`-sourced, but the brief gives it no channel-discriminating context. Either the scan will flag legitimate `/userinfo` code (forcing premature allowlist entries) or it will miss token-sourced code that happens to use the same spelling in a non-decoding file. The dirty-proof I5 is unimplementable as described.
· **Recommended brief amendment:** Choose one of:
  1. **Bound I2 to token-decoding files** (the same bound I1 re-proves). This is the only place a token-sourced read can originate, and it makes the instrument identical to the claim: "no file that decodes a token reads `email_verified`". It also removes the `/userinfo` contradiction entirely.
  2. **Make I2 context-aware**: scan all production files, but allow `email_verified` only in files that call `GET /userinfo` through the generated client (initially none, later `readProfile.ts` or its successor). The dirty-proof then creates a synthetic `readProfile.ts`-like file and proves the allowlist fires correctly.
  3. **Abandon the `/userinfo` permission** and treat every `email_verified` read as prohibited. This is the Phase 1 recommendation that Phase 2 rejected, but it is at least internally consistent.

Do not leave the brief with a tree-wide empty-allowlist scan that is also claimed to stay silent on `/userinfo` reads.

### Issue: The spelling split is not a reliable channel discriminator
· **Severity:** **Major**
· **Evidence:** The brief states "Snake case is the **token/OIDC** spelling; camel case is the **contract** spelling" and builds the instrument on that split. But `oidc.yaml:191-197` is the `/userinfo` contract and it spells the field `email_verified`. The generated client type for `/userinfo` would therefore expose `email_verified`, not `emailVerified`. Conversely, the access token is also an OIDC artifact and also uses `email_verified`.
· **Assessment:** Both the legitimate `/userinfo` source and the prohibited token source use the same spelling. Relying on spelling to distinguish them is wrong in principle and will fail the moment a `/userinfo` consumer is written. The brief acknowledges this indirectly by saying the scan must key on "reading a claim off a token", but it does not explain how the scan knows a given `email_verified` access is token-sourced rather than `/userinfo`-sourced.
· **Recommended brief amendment:** Drop the spelling-split framing. Define the instrument in terms of **source**, not spelling: a read is prohibited if it originates from a decoded JWT (access token or id_token), and permitted if it originates from `GET /accounts/me` or `GET /userinfo` through the generated client. The static-analysis proxy for "token-sourced" is the decode-bound file set.

### Issue: The prohibition's scope does not say whether the id_token is included
· **Severity:** **Major**
· **Evidence:** `agents.md:38` is quoted as forbidding "the access-token `email_verified` claim". The named test is `emailVerifiedClaimIsNotAccountState`, with no "access token" qualifier. `idTokenNonce.ts` decodes the `id_token`, and OIDC id_tokens legitimately carry `email_verified`.
· **Assessment:** If the id_token is out of scope, the brief must say so, because a future module could decode the id_token and read `email_verified` while arguing it did not violate the access-token clause. If the id_token is in scope, the instrument must cover any JWT-decoding file, not only access-token decoding. The current I1 bound is "files that decode a JWT"; that would cover id_token if the file decoded it, but the wording of the rule does not.
· **Recommended brief amendment:** State explicitly whether the prohibition covers id_token, access token, or any JWT. If any JWT, reword AC1/AC2 and the named test to match. If access token only, add a rationale for why the id_token's `email_verified` is acceptable as account state.

### Issue: A forward-only instrument with no production code cannot be type-enforced
· **Severity:** **Minor/Major**
· **Evidence:** The brief says T32 writes no production code because no consumer is commissioned. The entire enforcement is static-analysis scans in a test file.
· **Assessment:** This is the correct choice for the present, but it should be stated as a deliberate limitation. A future consumer could receive verification state through a typed prop named `emailVerified` and the scan for `email_verified` would not see it, because the prop is camelCase and legitimate. If that prop's value is secretly sourced from a token elsewhere, the scan cannot trace the data flow. The rule is enforced at the point of token access, not at the point of use.
· **Recommended brief amendment:** Document that the instrument is a **source-side guard**, not a data-flow guard. State that it can catch direct reads of the token claim but cannot catch a consumer using a value that was token-sourced under a different name. This prevents the next reader from overvaluing the scan.

### Issue: AC4 pins prose in `token-claims.md` rather than the contract line that cites T32
· **Severity:** **Minor**
· **Evidence:** AC4 wants to pin the claim's real semantics (scope authorization, not verification state). The brief points at `token-claims.md:51`. `contracts/api/oidc.yaml:194-197` already states the same semantics and explicitly references `contracts/api/token-claims.md` and `(frontend T32)`.
· **Assessment:** `oidc.yaml` is the generated-client contract and is already read by other tests. Pinning prose in `token-claims.md` requires parsing markdown and is more brittle than asserting a known contract line.
· **Recommended brief amendment:** Make `oidc.yaml:194-197` the primary pin for AC4, with `token-claims.md` as supporting documentation. The test should assert that the `/userinfo` schema's `email_verified` description contains the disclaimer about the access-token claim and the T32 reference.

### Issue: The new test file will need scan helpers; the brief does not say where they come from
· **Severity:** **Minor**
· **Evidence:** The brief proposes `frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts` as a new file. The scan helpers (`productionSources`, `strip`, `hitsOf`, `refuseEmpty`, etc.) currently live in the shared T30/T31 test file `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx`.
· **Assessment:** Duplicating the helpers would be a maintenance hazard. Importing them from a T30/T31-owned file creates cross-task coupling and violates the ownership model (T32 should not depend on T30's test internals). This tension already existed for T31 and was resolved by T30 owning the helpers; T32 reopens it.
· **Recommended brief amendment:** Decide whether to (a) move the scan helpers to a shared test-utility module such as `src/testing/scanHelpers.ts`, owned collectively, or (b) allow T32 to import them from the T30 test file with a documented ownership exception. Option (a) is cleaner and pays down the same debt T31 accumulated.

### Issue: AC7 surface sweep is vacuous without a commissioned consumer
· **Severity:** **Minor**
· **Evidence:** OQ-T32-1 notes that no task commissions a signed-in "your email is not verified" surface. AC7 requires a surface sweep that no production code will trigger.
· **Assessment:** The brief acknowledges this as a forward guard, which is the right posture. However, without a dirty-proof that writes a verification flag to a surface and proves the sweep catches it, AC7 is indistinguishable from a skipped test.
· **Recommended brief amendment:** Implement AC7 as a dirty-proof: a synthetic module that writes `emailVerified` / `email_verified` / `PENDING_VERIFICATION` to each swept surface and asserts the sweep reports them. Do not rely on a "no hits" report from code that never writes anything.

### Issue: AC2's claim is stronger than its instrument can support
· **Severity:** **Minor**
· **Evidence:** AC2 says "no component, route, store, hook or copy key is keyed on a token-borne verification flag". The proposed instrument is a spelling-based scan for `email_verified`.
· **Assessment:** A component keyed on a token-borne flag could receive the flag under a prop named `isEmailVerified`. The scan for `email_verified` would not catch this. The component would only be caught if it decoded the token itself. So AC2 overstates what the instrument can prove.
· **Recommended brief amendment:** Soften AC2 to "no production module reads the token's `email_verified` claim" and move the broader consumer claim to documentation. Or, if the broader claim must hold, add a data-flow or naming convention instrument that catches camelCase verification flags whose provenance is not `readAccount` or `/userinfo`.

### Issue: The admission rule for the empty allowlist is not tested
· **Severity:** **Minor**
· **Evidence:** The brief says I2's allowlist starts empty and carries an admission rule: a `/userinfo`-sourced read may be added. The usual package convention is that every allowlist is asserted as both upper and lower bound.
· **Assessment:** An empty allowlist has no lower bound to assert. The brief substitutes I5 (synthetic dirty-proofs), but if I5 is implemented as "scan stays silent on `/userinfo` read" without actually adding an allowlist entry, the allowlist is decorative.
· **Recommended brief amendment:** If I2 is kept as an allowlist design, include a dirty-proof that adds a synthetic `/userinfo`-sourced entry to the allowlist and proves the scan then accepts it, and another that adds a synthetic token-sourced entry and proves the scan rejects it. This exercises the allowlist mechanism as both upper and lower bound.

---

## Summary

The brief is strongest when it treats T32 as a source-side prohibition: no production file decodes a token and reads `email_verified` from it. It becomes inconsistent when it tries to also forbid tree-wide consumers by spelling alone while permitting `/userinfo`-sourced reads of the same spelling. The design challenge should resolve this before Phase 4 by choosing a channel-discriminating instrument rather than a spelling-discriminating one.

**Phase 3 complete.** Eight findings returned; no code or contract changed.
