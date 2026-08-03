# auth · T16 — Phase 10: Test Generation

Consumes `artifacts/09-review-resolution.md`. Production code untouched — this phase only added
test files. Both are plain JUnit (no Spring context), per `agents.md`'s testing conventions and
the Phase 5 plan; no fixed `Clock` is needed (neither class is time-dependent).

- `services/auth/src/test/java/com/themistra/auth/mfa/TotpGeneratorTest.java` (9 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaSeedEncryptionTest.java` (18 tests,
  `@ExtendWith(MockitoExtension.class)`, `KmsClient` mocked, `Environment` stood in for by
  `org.springframework.mock.env.MockEnvironment`)

No named test from `package.md` §8 is satisfied here — as Phase 1/2 already established,
`shouldReturnTotpProvisioningUriOnEnrollmentBegin` requires the persistence layer and the
`POST /accounts/me/mfa/totp` endpoint (tasks 17/19), neither of which exists yet.

Full suite verified: `mvn -pl services/auth -am test -Dtest='!*IntegrationTest'` → 340 tests
(313 pre-existing + 27 new), 5 errors — the same pre-existing baseline from Phase 6/7/9
(`AuthServiceApplicationTests.contextLoads`'s [[docker-testcontainers-handshake-issue]];
`AdminAccountRoleControllerTest`, `ReuseDetectingAuthorizationServiceTest`,
`TokenClaimsCustomizerTest` ×2 — unrelated pre-existing strict-stubbing/NPE issues). Zero new
failures; all 27 new tests pass.

## Test Manifest

### `TotpGeneratorTest`

| Test | Verifies |
|---|---|
| `generateSecretReturns20RandomBytes` | AC1 — 20-byte (160-bit) secret |
| `generateSecretProducesDifferentValuesAcrossCalls` | AC1 — randomness sanity (not a statistical entropy proof) |
| `buildProvisioningUriHasExpectedStructureForAKnownSecret` | AC2 — exact expected URI for a deterministic all-zero-byte fixture |
| `buildProvisioningUriCarriesL6Parameters` | AC2, L6 — `algorithm=SHA1&digits=6&period=30`, issuer present |
| `buildProvisioningUriSecretIsUppercaseUnpaddedBase32` | AC2 — `[A-Z2-7]{32}`, no `=` padding |
| `buildProvisioningUriBase32SecretRoundTripsToOriginalBytes` | AC2 — round-trips through an independent (test-only) Base32 decoder, not the production encoder tested against itself |
| `buildProvisioningUriEncodesSpacesInIssuerAndLabel` | AC2, RFC 3986 — space → `%20` in both issuer and label |
| `buildProvisioningUriDoesNotOverEncodeRfc3986UnreservedCharacters` | AC2, RFC 3986, Phase 8/9 fix #11 — `@`/`+` stay literal (not form-encoded like `URLEncoder` would) |
| `buildProvisioningUriIsSyntacticallyValid` | AC2 — the result is a parseable `URI` |

### `MfaSeedEncryptionTest`

| Test | Verifies |
|---|---|
| `constructorRefusesToBootInNonLocalProfileWithBlankArn` | AC5 — non-`local` profile + blank ARN → `IllegalStateException` at construction |
| `constructorSucceedsInLocalProfileWithBlankArn` | AC4 — no exception in the legal local-key configuration |
| `constructorSucceedsInNonLocalProfileWithArnConfigured` | Guard — non-`local` + configured ARN is legal |
| `publicConstructorNeverBuildsARealKmsClientInLocalMode` | AC4 — exercises the real `@Autowired`-facing constructor (not the test seam); a real AWS call would fail immediately without credentials in this JVM, so success proves no KMS attempt was made |
| `localModeRoundTripsAndNeverCallsKms` | AC4 — version `0x00`, round-trips |
| `localModeCiphertextNeverContainsRawSecretAsSubstring` | AC6 |
| `kmsModeProducesAdr0003EnvelopeLayoutAndRoundTrips` | AC3 — exact byte layout (version, 2-byte big-endian wrapped-key length, wrapped key bytes, 12-byte nonce, ciphertext+16-byte tag), round-trips; pins the mocked `KmsClient`'s `GenerateDataKey`/`Decrypt` contract per Phase 3 Finding #13 |
| `kmsModeCiphertextNeverContainsRawSecretAsSubstring` | AC6 |
| `wrongKeyDecryptFailsDistinctlyInsteadOfSilently` | AC6 — a KMS `Decrypt` rejection surfaces as `MfaEncryptionException`, never a silently-wrong plaintext |
| `generateDataKeyFailureIsWrappedAsMfaEncryptionException` | Phase 8/9 fix #4 — `generateDataKey` failures wrapped like `decrypt` failures |
| `unsupportedVersionByteThrowsMfaEncryptionException` | AC8 |
| `nullEnvelopeThrowsMfaEncryptionException` | Phase 8/9 fix #3 |
| `emptyEnvelopeThrowsMfaEncryptionException` | Phase 8/9 fix #3 |
| `truncatedEnvelopeThrowsMfaEncryptionException` | Phase 8/9 fix #3 |
| `localEnvelopeWithNonZeroWrappedKeyLengthIsRejected` | Phase 8/9 fix #12 — ADR-0003's `N=0` for version `0x00` |
| `concurrentEncryptAndDecryptAreThreadSafe` | AC7 — 100 concurrent encrypt+decrypt round-trips across a 16-thread pool on one shared instance |
| `destroyClosesKmsClientWhenPresent` | Phase 8/9 fix #6 — `DisposableBean.destroy()` closes the KMS client |
| `destroyDoesNothingWhenNoKmsClientWasBuilt` | Phase 8/9 fix #6 — no NPE when no client was ever built (local mode) |

## Open Questions

None.
