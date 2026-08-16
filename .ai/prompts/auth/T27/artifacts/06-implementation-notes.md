<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T27 · Phase 6 — Implementation Notes

Implements `artifacts/05-implementation-plan.md` against the frozen brief (`artifacts/04-frozen-task-brief.md`). This is a test-only task — the file below **is** the deliverable, not a Phase-10 addendum, matching the guardrail's own carve-out ("do NOT write tests here... unless the task itself is test-only").

---

## Files Created

### `apikey/ApiKeyLifecycleIntegrationTest.java`

One test method, `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle`, implementing the frozen brief's exact eight-step sequence:

1. `POST /api-keys` → 201, capture `keyUuid`/`plaintextKey`.
2. `GET /api-keys` → `lastUsedAt` null (D1).
3. `POST /api-keys/token` → 200, decode JWT, assert `sub`/`scope`/`amr` (AC1, L8).
4. `GET /api-keys` → `lastUsedAt` now non-null (D1/AC5).
5. `DELETE /api-keys/{keyUuid}` → 204.
6. `GET /api-keys` → `revokedAt` now non-null (D1/AC5).
7. `POST /api-keys/token` again, same key → 401 (AC2).
8. `POST /api-keys/token` with a malformed key → 401, body compared byte-for-byte against step 7's (D2/AC3).

Helpers mirror `ApiKeyExchangeIntegrationTest`/`ApiKeyCrudIntegrationTest`'s established shapes exactly (`bearerTokenFor` via `ApiKeyTokenIssuer`, `seedMerchantWithConfirmedMfa`, an independent RFC 6238 reference TOTP generator) — one new helper, `findByKeyUuid`, scans a `GET /api-keys` array response for a specific item, needed because this is the first test in the module that has to read a *specific* list item's fields rather than just its size/presence.

No production file created or modified. No file under `spec/` touched.

---

## Acceptance Criteria — mapping

| AC | Status | Evidence |
|---|---|---|
| AC1 | Done | Steps 1–3: create then exchange succeeds, JWT claims decoded and asserted, not just status |
| AC2 | Done | Step 7: identical key, post-revocation, 401 |
| AC3 | Done | Step 8: byte-for-byte body comparison against an independent rejection cause |
| AC4 | Done | Every step is a real `TestRestTemplate` HTTP call against `@SpringBootTest(RANDOM_PORT)` + `TestcontainersConfiguration` — no direct `ApiKeyService` method call anywhere in the test |
| AC5 | Done | Steps 2, 4, 6: `lastUsedAt`/`revokedAt` observed via `GET /api-keys` at each relevant point |

---

## Deviations Forced by Reality

None. Implementation matches the plan and frozen brief exactly.

---

## Build Verification

`mvn -q -pl services/auth -am test-compile` — clean, exit 0.

Run attempt: fails only with the same `ApplicationContext failure` (Testcontainers can't start, Docker down — confirmed via `docker info`) that every other `@SpringBootTest` class in this module currently produces this session — not a compile or logic error in the new test. No Docker-independent regression check needed: no production code changed, and no existing unit test's classpath is affected by adding one new, self-contained integration test file.

**Not verified this session (needs Docker):** the actual pass/fail of all eight assertions against a real server. Per D3, a failure here (once Docker is available) could stem from T25's `ApiKeyTokenIssuer`/`JwtEncoder` infrastructure rather than this test's own logic — worth checking that possibility first if it doesn't pass cleanly on the first real run.

---

**Phase 6 complete — implementation (test) written.** Proceed to Phase 7 (Self Review) on approval.
