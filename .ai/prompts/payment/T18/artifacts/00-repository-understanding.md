# payment · T18 · Phase 0 — Repository Understanding

Artifact: `.ai/prompts/payment/T18/artifacts/00-repository-understanding.md`

## 1. Architecture summary

`payment-service` (`services/payment`, package `com.themistra.payment`). T15–T17 built the
attest client, digest mechanism, and the orchestration/`HELD` guard — `ReceiptService.
issueIfEligible` currently returns a `ReceiptIssueResult.Signed` on success but persists
nothing (confirmed, T17's own disclosed scope: no `Receipt` entity exists yet, and the
`receipts` table's own `s3_bucket`/`s3_key NOT NULL` columns make a row impossible to insert
before this task). T18 is the first task to actually write anything to S3 from
`payment-service`, and the first to need Apache PDFBox anywhere in this service.

## 2. Existing code this task touches

**Already exists, read/extend:**
- `common/config/ReceiptProperties.java` (T03) — `bucket`/`kmsPublicKeysUrl`, both `@NotBlank`.
  **Missing a `region` field** — confirmed by direct inspection; `S3Client` construction needs
  one, and no other config record in this service carries it either.
- `receipt/ReceiptPayload.java`/`ReceiptDigest.java` (T16) — the exact 12-field O4 shape this
  task's own JSON object must contain (plus `signature`/`kmsKeyId`/`signedAt` once attested, per
  O4's own PDF text).
- `receipt/ReceiptIssueResult.java` (T17) — `Signed(UUID receiptUuid, AttestationResult.Signed
  signed)` is the exact data this task's own store method will be called with.
- `services/payment/pom.xml` — has the AWS S3 SDK dependency (added at T01, explicitly "for the
  receipt store only" — this task is that store) but **no Apache PDFBox dependency yet** —
  confirmed by direct `grep`, net new.

**Cross-service precedent (not imported — `agents.md`: "never depend on another service's
source" — the *pattern* is mirrored, not the code):**
- `crypto-service`'s own `ObservationSnapshotStore`/`ObservationSnapshotStoreConfig` (its only
  existing S3-client-wiring precedent anywhere in this codebase) — a `@Configuration` class
  building a plain `S3Client` bean (region from config, a fixed `Duration` API-call timeout, no
  credentials set — the AWS SDK's own default credential chain applies, L13), and a store class
  taking that `S3Client` + its own properties record. **Critically different in kind from this
  task's own requirement**: that store's own Javadoc explicitly frames S3 as "a supplementary
  durability layer, not a blocking dependency" and **never throws** on an S3 failure (returns
  `Optional.empty()`, logs, lets the real Postgres write proceed regardless). T18's own L9/R23 is
  the opposite: the S3 write **is** the load-bearing operation this task exists to make durable
  — a failure here cannot be silently swallowed the same way.

**Does not exist yet (net new):**
- `receipt/ReceiptStore.java` — this task's own primary deliverable.
- An `S3Client` Spring bean/config class for `payment-service` (none exists at all yet).
- Any Apache PDFBox usage anywhere in `payment-service`.
- `receipt/Receipt.java`/`ReceiptRepository.java` — **confirmed NOT part of this task's own
  scope**, same reasoning T17's own Phase 0 already established: this task's text says nothing
  about persisting a `Receipt` row, only about the S3 write itself. The actual `receipts` table
  insert (now that the S3 key would finally be known) is most plausibly T19's own job, bundled
  with the ledger append and the `ATTESTED` transition (R24/R25's own "same transaction" text) —
  not re-confirmed here, since T19 is out of this task's own scope to investigate further.

## 3. Established patterns to follow

- **S3 client wiring**: mirror `ObservationSnapshotStoreConfig`'s own shape — a plain
  `S3Client.builder().region(...).overrideConfiguration(...)` bean, no explicit credentials.
- **Config records fail startup on missing/invalid values in non-local profiles** (L13,
  `agents.md`) — any new required field (e.g. a `region` addition to `ReceiptProperties`) must
  be `@NotBlank`/validated the same way the existing two fields already are.
- **PDF rendering**: no existing precedent anywhere in this codebase for PDFBox or any PDF
  library — this is genuinely new ground for the whole `checky-pro` monorepo, not just this
  service.

## 4. Testing conventions

- Unit, mocked `S3Client`, no Spring context, for `ReceiptStore`'s own business logic (mirrors
  `CryptoClientTest`'s/`ObservationSnapshotStoreTest`-shaped precedent for a stateless client
  wrapper — though that specific test file wasn't directly inspected here, out of scope for a
  different service's own test suite).
- A real Object Lock/compliance-mode S3 bucket cannot be exercised by a plain unit test — Object
  Lock is a bucket-level AWS feature, not something a mocked `S3Client` call can meaningfully
  prove. Whether this task needs a LocalStack-backed integration test (mirroring
  `KmsSignerLocalStackIntegrationTest`'s own precedent, referenced in this session's own memory
  for `crypto-service`) to actually prove Object Lock parameters are set correctly on the real
  request, or whether a unit test asserting the right `PutObjectRequest` fields (`objectLockMode`,
  `objectLockRetainUntilDate`) is sufficient, is a genuine Phase 1/2 testing-strategy question.

## 5. Known gaps / unknowns

**Genuine open question, not resolved by anything in the spec package**: L9 says "written once...
in compliance mode" and "the object key embeds the `receiptUuid`; a second write to the same key
is a bug, not an update" — but names no retention **duration** for the Object Lock compliance
hold. AWS's own `PutObjectRequest` Object Lock fields require either an explicit
`objectLockRetainUntilDate` or a bucket-level default retention period (in which case the
application code sets no per-object override at all). I do not know which this task intends, or
what duration, if a per-object override is required.

**Genuine open question**: does `ReceiptStore` need to defensively guard against writing to an
already-existing key (e.g. a `headObject` existence check before `putObject`), or does L9's own
"a second write to the same key is a bug" framing mean the application correctly never attempts
one, with Object Lock itself as the safety net for a scenario that should never occur? Not
resolved by the spec.

**Known, not a gap — confirmed above, not assumed**: `ReceiptProperties` needs a new `region`
field; no S3 client bean exists yet in this service; no PDFBox dependency exists yet in this
service's own `pom.xml`.
