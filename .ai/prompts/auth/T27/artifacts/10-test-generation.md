<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T27 · Phase 10 — Test Generation

**No new test written in this phase.** T27 is a test-only task (`agents.md`/pipeline convention: for a task whose entire deliverable is a test, the test itself is written and reviewed at Phases 6/7/9, since there is no separate production-code phase to follow with a distinct test-generation pass). The one required test — `apikey/ApiKeyLifecycleIntegrationTest.shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle` — was written in Phase 6, self-reviewed in Phase 7 (no findings), independently reviewed by Kimi in Phase 8 (3 findings), and resolved at the Phase 9 human gate (2 accepted, 1 rejected with reasoning). This phase's job is the traceability manifest the pipeline expects at this checkpoint.

---

## Test Manifest

### `ApiKeyLifecycleIntegrationTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`, real filter chain

| Step | HTTP call | Verifies |
|---|---|---|
| 1 | `POST /api-keys` | R30 — create, 201, plaintext key shape |
| 2 | `GET /api-keys` | D1/AC5 — `lastUsedAt` null before any exchange (200 status asserted first, Phase 9 fix) |
| 3 | `POST /api-keys/token` | R31, L8, AC1 — exchange succeeds, JWT decoded, `sub`/`scope`/`amr` asserted |
| 4 | `GET /api-keys` | R32, D1/AC5 — `lastUsedAt` now non-null (200 status asserted first) |
| 5 | `DELETE /api-keys/{keyUuid}` | R35 — revoke, 204 |
| 6 | `GET /api-keys` | D1/AC5 — `revokedAt` now non-null (200 status asserted first) |
| 7 | `POST /api-keys/token` (same key) | R33, AC2 — 401; `application/problem+json`, no `detail` (Phase 9 fix) |
| 8 | `POST /api-keys/token` (malformed key) | D2/AC3 — 401, `application/problem+json`, no `detail` (Phase 9 fix), body byte-for-byte identical to step 7's |

**AC4** (the entire sequence runs through real HTTP calls against a real running server and real Postgres) is satisfied by the test's structure itself — every step above is a `TestRestTemplate` call, none is a direct `ApiKeyService` method invocation.

**Named tests from this task's header** (`shouldCreateApiKeyAndShowPlaintextExactlyOnce`, `shouldExchangeValidApiKeyForMerchantJwt`, `shouldRejectRevokedOrUnknownApiKeyWithUniform401`, `shouldListAndRevokeOwnApiKeys`) are not written as four new methods here — each already exists, correctly, in `ApiKeyServiceIntegrationTest` (T24), `ApiKeyExchangeIntegrationTest` (T25), and `ApiKeyCrudIntegrationTest` (T26), and `package.md` §8 has no distinct row for a T27-specific name (Phase 1 finding, ratified at Phase 4). T27's single new test is the deliverable this task's actual task statement ("Test create→exchange→revoke→exchange-fails **flow**") describes.

### Regression

No production code changed at any point in this task, so no existing test is at risk of regression. The three pre-existing `apikey` integration test files (`ApiKeyServiceIntegrationTest`, `ApiKeyExchangeIntegrationTest`, `ApiKeyCrudIntegrationTest`) and `ArchitectureTest` are unaffected by this task by construction.

---

## Build Verification

`mvn -q -pl services/auth -am test-compile` — clean, exit 0 (re-confirmed after Phase 9's fixes).

**Written but unexecuted this session** — Docker remains unavailable (`docker info` fails), the same pre-existing environment gap as every phase since T25's Phase 6. The file compiles cleanly and fails only with the same `ApplicationContext failure` (Testcontainers can't start) every other `@SpringBootTest` class in this module currently produces — not a compile or logic error. Per the frozen brief's D3, if this test doesn't pass cleanly on its first real run, T25's `ApiKeyTokenIssuer`/`JwtEncoder` infrastructure is a plausible root cause to check first, not necessarily this test's own logic.

---

## Traceability Summary

Every acceptance criterion (AC1–AC5) and every business rule this task scopes (R30, R31, R33, R35) is covered by at least one step in the single test above, per the frozen brief's own eight-step Scope. No requirement or criterion is left unaddressed.

---

**Phase 10 complete.** Proceed to Phase 11 (Kimi Test Review) on approval.
