# auth · T16 — Phase 7: Self Review

Consumes `artifacts/06-implementation-notes.md`. Findings only — no fixes applied here (Phase 9).

Reviewed files: `TotpGenerator.java`, `MfaSeedEncryption.java`, `MfaProperties.java`,
`MfaEncryptionException.java`, plus the `pom.xml`/`application.properties` diffs.

Beyond static reading, two additional checks were run to close verification gaps the Phase 6
manual smoke tests left open (Docker/Testcontainers is unavailable in this sandbox, so no
`@SpringBootTest` can run — [[docker-testcontainers-handshake-issue]]):
- A minimal `AnnotationConfigApplicationContext` scoped to only `com.themistra.auth.mfa`
  (bypassing DataSource/Kafka/Testcontainers entirely) confirmed the `@Autowired`
  one-arg-vs-package-private-two-arg constructor split resolves correctly under real Spring DI,
  `@ConfigurationPropertiesScan` picks up `MfaProperties`, and an encrypt/decrypt round-trip works
  through the actual Spring-managed bean — not just direct/reflective instantiation.
- No new finding resulted from that check; recorded here so Phase 9/10 don't have to re-derive
  why constructor-injection ambiguity isn't a concern.

## Findings

**1. Issue:** No ArchUnit rule enforces D-025/ADR-0003's "only `MfaSeedEncryption` may import the
AWS SDK" invariant.
**Severity:** Medium
**Evidence:** `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` (whole file)
already encodes several module-boundary decisions as permanent, CI-enforced `ArchRule`s — its own
class doc says each rule exists because "a specific design decision said 'no module may do X'...
this is where that stops being a comment and starts being something that fails the build."
`MfaSeedEncryption.java:24-25`'s Javadoc makes exactly this kind of claim ("the one narrow, named
exception... permitting AWS SDK use in this service"), and `agents.md`'s Security section states
the same, but nothing mechanically stops a future class anywhere in `services/auth` from importing
`software.amazon.awssdk.*` without a test catching it.
**Recommendation:** Add a rule to `ArchitectureTest.java` (e.g. `noClasses().that()
.doNotHaveFullyQualifiedName("com.themistra.auth.mfa.MfaSeedEncryption").should()
.dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")`), mirroring the file's
existing style. This is a test-only addition; natural home is Phase 10 (Test Generation) rather
than Phase 9, since `ArchitectureTest.java` isn't in the frozen brief's Files-to-Modify list and
this is a test artifact, not application code.

**2. Issue:** `decrypt`/`parseEnvelope` don't translate a malformed or truncated envelope into
`MfaEncryptionException` — raw JDK exceptions leak out instead.
**Severity:** Medium
**Evidence:** `MfaSeedEncryption.java:110` (`byte version = envelope[0];` — throws
`ArrayIndexOutOfBoundsException` on an empty array, `NullPointerException` on `null`) and
`:203-213` (`parseEnvelope`'s `ByteBuffer.get(...)` calls throw `BufferUnderflowException` if
`wrappedKeyLength` or the overall envelope is shorter than the version byte claims — e.g. a
corrupted/truncated `secret_encrypted` value). AC8 only required handling an *unsupported version
byte*, which is done correctly, but a structurally malformed envelope of a *known* version byte
surfaces as an undocumented unchecked exception type instead of the dedicated exception the class
exists to provide.
**Recommendation:** Wrap the version-byte read and `parseEnvelope`'s body in a try/catch
translating `IndexOutOfBoundsException`/`BufferUnderflowException`/`NullPointerException` into
`MfaEncryptionException`.

**3. Issue:** Zeroing the KMS-derived plaintext data key after use is only partially effective.
**Severity:** Low-Medium
**Evidence:** `MfaSeedEncryption.java:139` / `:161` construct `new SecretKeySpec(plaintextKey,
"AES")` — the JDK's `SecretKeySpec(byte[], String)` constructor clones the input array internally
— *before* the `finally` block's `Arrays.fill(plaintextKey, (byte) 0)` runs at `:142` / `:163`.
Zeroing the original `plaintextKey` array has no effect on the independent copy inside the
`SecretKeySpec` (or whatever the `Cipher` derives from it), which stays in memory un-scrubbed until
GC. Not a regression against the frozen brief (defensive zeroing wasn't a stated requirement, it
was added on top), but the code's apparent hygiene guarantee is weaker than it reads.
**Recommendation:** Either accept this as best-effort (JCE has no fully reliable in-memory key
scrubbing short of `Destroyable`, which `SecretKeySpec` supports inconsistently across JDK
versions) and soften the implication, or call `key.destroy()` where supported. Low priority either
way — no externally observable difference.

**4. Issue:** The `KmsClient` (an `AutoCloseable` SDK resource) is never closed.
**Severity:** Low
**Evidence:** `MfaSeedEncryption.java:96` builds `KmsClient.builder().build()` and stores it for
the bean's lifetime; nothing calls `.close()` on it, and `MfaSeedEncryption` doesn't implement
`AutoCloseable`/`DisposableBean`, so Spring has no hook to close it on context shutdown.
`agents.md`'s deployment section and `application.properties`'s `server.shutdown=graceful` suggest
this team cares about clean shutdown behavior.
**Recommendation:** Have `MfaSeedEncryption` implement `AutoCloseable` (or `DisposableBean`) and
close `kmsClient` if non-null. Cosmetic in practice (JVM exit reclaims the connection pool anyway)
but consistent with the graceful-shutdown posture already established.

**5. Issue (readability):** The class Javadoc points to an ephemeral pipeline artifact.
**Severity:** Low
**Evidence:** `MfaSeedEncryption.java:31` ("deviates from the frozen brief's literal wording — see
implementation notes"). `artifacts/06-implementation-notes.md` is part of this task's `.ai/`
pipeline trail, not necessarily retained or discoverable once T16 is merged and the pipeline
artifacts age out of relevance.
**Recommendation:** Keep the reasoning (already mostly self-contained in the same doc comment,
lines 31-39) but drop the artifact cross-reference, or restate the "why" fully inline so the
comment stands on its own for a future reader with no access to `.ai/prompts/auth/T16/`.

## Categories Checked, No Finding

- **Correctness:** verified via manual round-trip checks (Phase 6) plus the Spring-context check
  above; all 8 ACs behave as specified.
- **Thread-safety:** 200 concurrent encrypt+decrypt calls across 32 threads on one shared
  `MfaSeedEncryption` instance all completed correctly (new `Cipher` per call, as required).
  `SecureRandom.nextBytes` is documented thread-safe.
- **Transaction boundaries:** N/A — no persistence in this task, matches frozen brief.
- **Module boundaries (L12):** no cross-module imports from `mfa` into `account`/`authn`/`authz`/
  `apikey`/`audit`/`events`/`token`/`common`; the one `{@link}` Javadoc reference to
  `SigningKeyMaterial` creates no compiled dependency ArchUnit would see.
- **Idempotency:** N/A — no Kafka/outbox interaction in this task.
- **Money types:** N/A.
- **Enumeration-safety / secret-handling:** raw secret and provisioning URI are never logged;
  the one `log.warn` (local-key-mode notice) contains no secret material; ciphertext confirmed to
  never contain the raw secret as a substring (AC6).
- **Null-safety:** `seedKekArn` blank-vs-non-blank (the one in-scope null/blank path per the frozen
  brief's Constraints section) is handled correctly and exercised in both the guard and the mode
  switch; other null-argument cases were explicitly ruled out of scope by the frozen brief and are
  not re-litigated here.
- **Complexity:** private helper methods are each single-purpose; envelope assemble/parse logic is
  proportionate to ADR-0003's byte layout, not over-engineered.
