<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T27 · Phase 12 — Specification Verification

Compares the final test and its manifest (Phases 6–11) against `requirements.md`, `design.md`, `tasks.md`, and the frozen brief for **T27 only**. `spec/auth-service/` confirmed byte-for-byte unchanged since T27 began (`git diff ca742e0...HEAD -- spec/auth-service/` — empty, `ca742e0` being T26's final commit).

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R30** — create via `POST /api-keys` | Yes (pre-existing, T26, unmodified) | `ApiKeyLifecycleIntegrationTest.java:111-121` (step 1) | Named test `shouldCreateApiKeyAndShowPlaintextExactlyOnce` already covers this in isolation (T26); this task's own step 1 additionally exercises it as the flow's entry point, exact-field-set-asserted | No | No |
| **R31** — exchange via `POST /api-keys/token` | Yes (pre-existing, T25, unmodified) | `ApiKeyLifecycleIntegrationTest.java:129-137` (step 3) | Named test `shouldExchangeValidApiKeyForMerchantJwt` (T25) covers isolation; step 3 here proves it as a link in the chain, decoding `sub`/`scope`/`amr` | No | No |
| **R32** *(referenced, D1)* — `last_used_at` updates on exchange | Yes (pre-existing, T24, unmodified) | `ApiKeyLifecycleIntegrationTest.java:123-127,139-143` (steps 2, 4) | This task's own genuine new contribution — no existing test observes this transition via HTTP within one continuous key lifecycle | No | No |
| **R33** — revoked key exchange → uniform 401 | Yes (pre-existing, T25, unmodified) | `ApiKeyLifecycleIntegrationTest.java:157-176` (steps 7-8) | Named test `shouldRejectRevokedOrUnknownApiKeyWithUniform401` covers isolation (T24 service-layer, T25 HTTP-layer with fresh keys per cause); steps 7-8 here prove it against the *same* key that just succeeded, plus the D2 byte-for-byte-against-a-second-cause comparison | No | No |
| **R34** *(referenced, D1)* — `GET /api-keys` as the observation point | Yes (pre-existing, T26, unmodified) | `ApiKeyLifecycleIntegrationTest.java:123-127,139-143,149-155` | Same as R32 — this task's genuine contribution | No | No |
| **R35** — revoke via `DELETE /api-keys/{keyUuid}` | Yes (pre-existing, T26, unmodified) | `ApiKeyLifecycleIntegrationTest.java:145-147` (step 5) | Named test `shouldListAndRevokeOwnApiKeys` covers isolation (T26); step 5 here proves it as the pivot between "works" and "doesn't" | No | No |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence | Test? | Deviation? |
|---|---|---|---|---|
| **L7** — key format / SHA-256-only / plaintext-once | Yes (exercised, not re-verified in isolation — already exhaustive in `ApiKeyServiceIntegrationTest`/`ApiKeyHasherTest`) | Step 1's shape assertion (`ApiKeyLifecycleIntegrationTest.java:120`) | Exercised | No |
| **L8** — JWT claim contract | Yes (partial, deliberately — `sub`/`scope`/`amr` only, per frozen brief; exhaustive L9 coverage lives in `ApiKeyTokenIssuerTest`/`ApiKeyExchangeIntegrationTest`) | `ApiKeyLifecycleIntegrationTest.java:133-137` | Yes | No — frozen brief explicitly scopes this narrowly (D-not-numbered, stated directly in Locked Decisions) |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | Steps 1-3: create then exchange succeeds; JWT decoded, `sub`/`scope`/`amr` asserted, not just status |
| AC2 | **Met** | Step 7: identical key, post-revocation, 401 |
| AC3 | **Met, strengthened at Phase 11** | Step 7-8: byte-for-byte body comparison against an independent rejection cause, **plus** (Kimi Phase 11 Gap 1) explicit `type`/`title` assertions matching `ProblemTypes.API_KEY_EXCHANGE_REJECTED` — closing the gap where byte-equality alone wouldn't catch both sides drifting to a different-but-consistent type |
| AC4 | **Met** | Every step is a `TestRestTemplate` call against `@SpringBootTest(RANDOM_PORT)` + `TestcontainersConfiguration`; no direct `ApiKeyService` method call anywhere in the file |
| AC5 | **Met** | Steps 2, 4, 6: `lastUsedAt`/`revokedAt` observed via `GET /api-keys`, each preceded by an explicit 200 + `application/json` Content-Type assertion (Phase 9 + Phase 11 hardening) |

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes, within T27's authorized (test-only) scope. The single required test exists, compiles cleanly, was self-reviewed (no findings), independently reviewed by Kimi twice (Phase 8: 3 findings, 2 accepted; Phase 11: 5 findings, 3 accepted), and every acceptance criterion has direct evidence in the final file. As with T25 and T26, the one category of incompleteness is environmental: Docker has been unavailable this entire multi-day stretch of the pipeline, so the test has never actually executed against a real server.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC5 all have direct code evidence, strengthened twice over the course of review (Phase 9's status/content-type/no-detail additions, Phase 11's exact-type/title and exact-field-set additions).

**(3) Does it violate any LOCKED decision?** No. T27 introduced no production code and no LOCKED-decision deviations at all — the cleanest of the three most recent tasks in this respect, since there was no implementation to make trade-offs against; only a test-design question (D1–D4 at this task's own Phase 4 gate) to resolve, and none of those touch a LOCKED decision's substance.

**(4) Remaining risks:**
- **Unexecuted test (highest-priority residual, same pattern as T25/T26, now compounding across three tasks).** `ApiKeyLifecycleIntegrationTest` has never run against a real server this session. Per the frozen brief's D3, if it fails on first real execution, T25's `ApiKeyTokenIssuer`/`JwtEncoder` infrastructure is a plausible root cause to check before assuming a defect in this test's own logic.
- **Kimi Phase 11 Gap 4 (not applied, by design):** the exchanged JWT's real usability against a resource-server endpoint is proven only by decoding, not by using it to make an authenticated call. Deliberately left as-is — the frozen brief's AC1 wording is explicitly satisfied by decoding, and Kimi itself rated this low-priority/scope-expanding.
- **Contract files still don't exist** (`contracts/api/auth.yaml`/`token-claims.md`) — same gap noted at every API-key task since T25; not blocking for a behavioral test.
- **Cumulative Docker-down risk across T25/T26/T27:** three consecutive tasks now carry a fully-written-but-unexecuted Testcontainers suite. Whoever picks up any of these should run the full `apikey`-related integration suite together, in dependency order (T25's `SasLoginIntegrationTest` and `ApiKeyExchangeIntegrationTest` first, since T26 and T27 both build on that infrastructure working correctly), rather than assuming each task's tests are independently verified.

---

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion has direct code evidence in a single, thoroughly-reviewed test; the only gaps are the pre-existing, disclosed environmental limitation (Docker) shared with every recent task, and one explicitly-scoped-out enhancement (Kimi Gap 4) that the frozen brief's own wording doesn't require.

---

**Phase 12 complete — PASS.** Proceed to Phase 13 (PR / Commit Preparation).
