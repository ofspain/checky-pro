## 1. Configuration shape

- [x] 1.1 Add `ChainProviderProperties` bound from `themistra.crypto.chains.*`, carrying per chain
      an identifier and a list of providers with a label, an endpoint, and an optional
      subscription endpoint. Verify binding with a properties-based test.
- [x] 1.2 Add the properties to `application.properties` using the environment-variable-with-default
      pattern from `services/auth`. Verify no endpoint or credential literal appears in the file.

## 2. Startup validation

- [x] 2.1 Reject a chain with fewer providers than the quorum threshold, naming the chain and the
      shortfall. Verify the context fails to start.
- [x] 2.2 Reject duplicate provider labels within a chain. Verify a same-label-different-chain
      configuration still starts.
- [x] 2.3 Reject a chain whose namespace has no adapter implementation. Verify the message names
      the namespace.
- [x] 2.4 Reject a declared provider with a blank or unresolved endpoint, and verify the failure
      message contains no part of any endpoint value.
- [x] 2.5 Verify a configuration declaring no chains starts successfully.

## 3. Adapter registry

- [x] 3.1 Add a registry resolving a chain identifier to its adapters, constructing the adapter
      type matching the namespace. Verify each configured provider yields one adapter carrying its
      label.
- [x] 3.2 Fail clearly when asked for an unconfigured chain rather than returning an empty list.
      Verify the distinction from total provider failure is preserved.
- [x] 3.3 Verify the registry hands its adapters to `QuorumReader` unchanged, by establishing a
      fact through the registry with stubbed providers.

## 4. Operability

- [x] 4.1 Report configured chains and their provider counts through health. Verify the output
      lists each chain.
- [x] 4.2 Assert no endpoint or credential appears in health output. Verify by configuring an
      endpoint containing a recognisable token and grepping the rendered health response for it.
- [x] 4.3 Run the full module suite and confirm the existing 23 tests still pass.
