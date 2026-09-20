## Purpose

How chain providers are declared, how a chain's adapters are resolved from that declaration, and
what the service does when a deployment cannot meet the quorum requirement it claims to enforce.

## ADDED Requirements

### Requirement: Providers are declared per chain
Each supported chain SHALL be declared with its namespaced chain identifier and the set of
independent providers serving it. Each provider SHALL carry a stable, non-sensitive label and an
endpoint.

#### Scenario: A chain is declared with three providers
- **WHEN** a chain is configured with three labelled providers
- **THEN** the service resolves three adapters for that chain, one per provider

#### Scenario: Two chains are declared
- **WHEN** more than one chain is configured
- **THEN** each chain resolves only its own providers, and no adapter serves a chain it was not
  declared for

#### Scenario: No chains are declared
- **WHEN** the service starts with no chains configured
- **THEN** it starts successfully with no chains available, because a service that has not yet
  been given work is not misconfigured

### Requirement: Endpoints never appear in configuration files
Provider endpoints and any credentials embedded in them SHALL be resolvable from the environment,
and SHALL NOT be required to appear in a file committed to the repository.

#### Scenario: An endpoint is supplied by the environment
- **WHEN** a provider's endpoint is supplied through an environment variable
- **THEN** the service uses it, and the committed configuration contains only the variable
  reference

#### Scenario: An endpoint is missing at startup
- **IF** a declared provider has no resolvable endpoint
- **THEN** startup fails naming the provider label, and the message SHALL NOT contain any
  partially-resolved endpoint value

### Requirement: A deployment below quorum refuses to start
A chain declared with fewer providers than the configured quorum threshold SHALL cause startup to
fail.

#### Scenario: A chain declared with one provider
- **IF** a chain is declared with one provider and the threshold is two
- **THEN** startup fails naming the chain and the shortfall

#### Scenario: A chain declared with exactly the threshold
- **WHEN** a chain is declared with exactly as many providers as the threshold requires
- **THEN** the service starts, since the threshold is satisfiable — though it has no tolerance for
  a single provider failing

#### Scenario: One chain misconfigured among several
- **IF** any declared chain has too few providers
- **THEN** startup fails, rather than starting with the remaining chains and silently serving a
  subset

### Requirement: Provider labels are unique within a chain
Two providers serving the same chain SHALL NOT share a label, so that a retained answer
unambiguously identifies its source.

#### Scenario: Duplicate labels on one chain
- **IF** a chain declares two providers with the same label
- **THEN** startup fails, because the observation log could not attribute an answer to a provider

#### Scenario: The same label on different chains
- **WHEN** two different chains each declare a provider with the same label
- **THEN** the service starts, since answers are attributed within a chain

### Requirement: Adapters are resolved by chain
Callers SHALL obtain the adapters for a chain by naming that chain, and SHALL NOT assemble a
provider set themselves.

#### Scenario: A caller requests a configured chain
- **WHEN** a caller asks for the adapters serving a configured chain
- **THEN** it receives every provider declared for that chain

#### Scenario: A caller requests an unconfigured chain
- **IF** a caller asks for a chain that was never declared
- **THEN** the request fails clearly, rather than returning an empty set that would later look
  like total provider failure

### Requirement: Adapter type follows the chain namespace
The adapter constructed for a provider SHALL be the one matching that chain's namespace.

#### Scenario: An EVM chain is declared
- **WHEN** a chain in the EVM namespace is configured
- **THEN** its providers resolve to EVM adapters

#### Scenario: A namespace with no adapter
- **IF** a chain is declared in a namespace the service has no adapter for
- **THEN** startup fails naming the namespace, rather than starting a chain nothing can observe

### Requirement: Configured chains are observable to operators
The service SHALL report which chains are configured and how many providers each has, without
disclosing endpoints.

#### Scenario: An operator inspects service health
- **WHEN** health is inspected
- **THEN** it reports each configured chain and its provider count

#### Scenario: Health output is inspected for secrets
- **WHEN** health output is inspected
- **THEN** it contains no endpoint, credential, or partially-redacted endpoint value
