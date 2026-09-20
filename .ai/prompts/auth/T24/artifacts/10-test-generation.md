# auth · T24 · Phase 10 — Test Generation

`ApiKeyHasherTest` (plain JUnit) and `ApiKeyServiceIntegrationTest` (Testcontainers) created. No production code changed this phase, except one test-driven fix discovered while writing tests (see below).

**Verification performed:** `mvn -pl services/auth -am test -Dtest=ApiKeyHasherTest` — 3/3 pass. `mvn -pl services/auth -am test -Dtest=ApiKeyServiceIntegrationTest` — 12/12 pass. Full suite (`mvn -pl services/auth -am test`) — 513 tests, same 6 pre-existing/unrelated errors as the established baseline (Kafka delivery-timing flake, audit-FK ordering ×3, Mockito strict-stubbing ×2), unchanged by this task.

One test-infrastructure issue was found and fixed while writing the integration test, not a production defect: `ApiKeyRepository.revokeIfActive` and a raw `EntityManager` native update (needed to construct an expired-key fixture, since `create()` never sets `expiresAt`) both failed with `TransactionRequiredException` when called directly from a non-`@Transactional` test method — the same class of issue `MfaPersistenceIntegrationTest` already documented and solved. Fixed by adding the same `inOwnTransaction(Runnable)` helper and wrapping both call sites.

## Test manifest

| Test | Maps to | What it proves |
|---|---|---|
| `ApiKeyHasherTest.matchesReturnsTrueForTheCorrectKey` | AC7 | The hash-then-compare round trip works for a correct key. |
| `ApiKeyHasherTest.matchesReturnsFalseForAnIncorrectKey` | AC7 | A different key's hash doesn't match. |
| `ApiKeyHasherTest.matchesRejectsMismatchesRegardlessOfPosition` | AC7 (Phase 9 finding #6 context) | Correctness proxy: a mismatch at the first vs. last character of the hash is rejected identically — not a real timing measurement, documented as such. |
| `shouldCreateApiKeyAndShowPlaintextExactlyOnce` | AC1–AC4, AC10 (named test, R30) | Full create flow: plaintext matches the `ck_live_<24>.\<32>` shape, the stored row holds only the hash (never the plaintext), `prefix` is 32 characters (proving the `V7`/entity-mapping fix from Phase 9 finding #1 actually works against a real DB), and `api_key.created` is audited. |
| `createRejectsNonMerchantAccount` | AC1 | Role gate. |
| `createRejectsUnconfirmedMfa` | AC1 | MFA gate. |
| `createRejectsNonActiveAccount` | AC1 (Phase 4 disposition #6) | Account-status gate — the precondition Phase 3 caught as missing from the original TIB. |
| `createRejectsBlankOrOverlongName` | AC10 | Name validation (blank and >100 chars). |
| `shouldRejectRevokedOrUnknownApiKeyWithUniform401` | AC9 (named test, R33) | All four R33 causes in one test: malformed input, unknown prefix, correct-prefix-wrong-secret, revoked key, and expired key (constructed via direct repository access since `create()` never sets an expiry) — every one throws the same `ApiKeyExchangeRejectedException`. |
| `exchangeUpdatesLastUsedAt` | AC8 (R32) | `last_used_at` is null before, non-null after a successful exchange. |
| `exchangeChecksEveryPrefixCollisionCandidate` | Frozen brief disposition #4 | Two keys constructed with the same prefix, different accounts; exchange with the second key's secret still finds and returns the correct (second) account — proves the loop doesn't stop at the first candidate. |
| `exchangeAuditsTheMatchedAccountEvenWhenItIsNotTheFirstCandidate` | Phase 8/9 finding #4's fix | Two prefix-sharing keys, the *second* one (not first) has the matching-but-revoked hash; exchange rejects, and the audit event lands on the second account, not the first — directly exercises the fix applied in Phase 9. |
| `listReturnsOnlyTheCallersOwnKeysWithNoSecretMaterial` | AC5 | Two accounts' keys don't cross-leak; `ApiKeyMetadata` structurally cannot carry hash/secret data. |
| `revokeIsIdempotent` | Phase 9 finding #2/#5's fix | Revoking an already-revoked key produces exactly one `api_key.revoked` audit event, not two — directly exercises the fix applied in Phase 9 (this test would have failed against the pre-Phase-9 implementation). |
| `revokeOfNonOwnedKeyFails` | AC6, ownership boundary | A non-owner's revoke attempt fails with `ApiKeyNotFoundException` — same exception whether the key doesn't exist or isn't theirs. |

Every acceptance criterion in the frozen brief (AC1–AC10) and every Phase 9-resolved finding that had test-observable behavior now has a corresponding, passing, Testcontainers-verified test — including the two findings (#1 and #4/#5) that were real, would-have-been-invisible-without-a-test bugs.
