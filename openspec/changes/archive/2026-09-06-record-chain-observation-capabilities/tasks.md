## 1. Record the capabilities

- [x] 1.1 Write the `chain/quorum-reads` delta spec from ARCHITECTURE §6.1 and the passing tests
      in `QuorumReaderTest` and `CryptoConfigurationTest`. Verify every requirement maps to at
      least one existing test.
- [x] 1.2 Write the `chain/evm-observation` delta spec from §6.2, §6.3 and the passing tests in
      `Erc20TransferDecoderTest`. Verify every requirement maps to at least one existing test.
- [x] 1.3 Confirm no requirement names a class, method, or library, so a refactor cannot
      invalidate the spec. Verify by reading both spec files for type names.
- [x] 1.4 Validate the change with `openspec validate --strict`.

## 2. Reconcile

- [x] 2.1 Record the absence-vote weakness as an open question rather than specifying it away.
      Verify it appears in `design.md` and is carried into a later change rather than lost.
- [x] 2.2 Archive the change so `openspec/specs/chain/` becomes the living record. Verify
      `openspec list` shows the change archived and both capabilities present under
      `openspec/specs/`.
