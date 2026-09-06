## Purpose

How the crypto service decides that something about a chain is true: which providers must agree,
what counts as a vote, and what is retained so that a decision made today can be defended years
later.

## ADDED Requirements

### Requirement: No single provider is authoritative
The system SHALL NOT treat any one provider's answer as fact. A configured quorum threshold below
two SHALL be rejected, and the service SHALL refuse to start rather than run with one.

#### Scenario: Configured with a threshold of one
- **WHEN** the service starts with a quorum threshold of 1
- **THEN** startup fails with an error naming the constraint, and the service does not serve traffic

#### Scenario: Configured with a threshold exceeding available providers
- **IF** the threshold is greater than the number of configured providers
- **THEN** startup fails, because the threshold could never be met

#### Scenario: Asked to establish a fact with too few providers
- **IF** fewer providers are supplied than the threshold requires
- **THEN** the system SHALL reject the request rather than lower the bar

### Requirement: A fact requires threshold agreement
The system SHALL establish a fact only when at least the configured threshold of providers report
the same observation.

#### Scenario: All providers agree
- **WHEN** every provider reports the same transfer
- **THEN** the system establishes that observation as fact

#### Scenario: Threshold met with a dissenter
- **WHEN** two of three providers report the same transfer and the third does not
- **THEN** the system establishes the agreed observation as fact

### Requirement: Provider failure is not a vote
A provider that cannot answer SHALL NOT count toward agreement and SHALL NOT prevent the
remaining providers from reaching the threshold.

#### Scenario: One provider unreachable
- **WHEN** one provider fails and the other two agree
- **THEN** the threshold is met and the fact is established

#### Scenario: Too many providers failing
- **IF** enough providers fail that the threshold cannot be met by those remaining
- **THEN** the system establishes no fact, and does not fall back to a surviving provider's answer

#### Scenario: A failing provider does not deny the read
- **WHEN** a provider throws while being queried
- **THEN** the failure is captured as that provider's answer and the read continues

### Requirement: Absence is a vote
A provider reporting that a transaction is not on chain SHALL be treated as an answer, distinct
from a provider that could not answer. Threshold agreement on absence SHALL establish absence.

#### Scenario: Providers agree a transaction is absent
- **WHEN** the threshold of providers report the transaction is not on chain
- **THEN** the system establishes absence, distinguishable by consumers from disagreement

#### Scenario: Absence and observation split the providers
- **IF** some providers see a transaction and others report it absent, with neither reaching the
  threshold
- **THEN** the system establishes nothing

### Requirement: Disagreement is reported, never resolved
When no outcome reaches the threshold, the system SHALL report disagreement together with a reason,
and SHALL NOT select a winner among conflicting answers by any heuristic.

#### Scenario: Providers report conflicting amounts
- **WHEN** providers report different amounts for the same transaction
- **THEN** the system reports disagreement and establishes no fact

#### Scenario: Same transaction reported in different blocks
- **WHEN** providers place the same transaction in different blocks
- **THEN** the system reports disagreement rather than preferring either placement

#### Scenario: Callers cannot ignore disagreement
- **WHEN** a caller handles a quorum outcome
- **THEN** disagreement is a distinct outcome the caller must handle explicitly

### Requirement: Volatile fields are excluded from agreement
Fields that legitimately change between two providers' replies SHALL NOT be compared when
determining agreement.

#### Scenario: Confirmation depth advances mid-read
- **WHEN** two providers report the same transfer at different confirmation depths
- **THEN** they are treated as agreeing, because depth is not part of the compared fact

### Requirement: Every provider answer is retained
The system SHALL retain what each provider said, including failures and absences, alongside the
outcome, so that a past decision can be re-derived and defended.

#### Scenario: A fact is established
- **WHEN** the system establishes a fact
- **THEN** the outcome carries every provider's answer, not only the agreeing ones

#### Scenario: A provider failed
- **WHEN** a provider fails during a read
- **THEN** its failure and the reason are retained in the outcome

#### Scenario: Answers carry when they were observed
- **WHEN** any provider answers
- **THEN** the answer records the time it was observed

### Requirement: Providers are queried concurrently
The system SHALL query providers concurrently, so that establishing a fact costs the slowest
provider's latency rather than their sum.

#### Scenario: One provider is slow
- **WHEN** one provider is markedly slower than the others
- **THEN** the other providers are not queried after it, and the read is not serialised behind it

### Requirement: Provider labels carry no secrets
A provider SHALL be identified by a stable, non-sensitive label. Endpoint URLs and credentials
SHALL NOT appear in any retained answer or outcome.

#### Scenario: An outcome is logged or published
- **WHEN** a quorum outcome is inspected
- **THEN** providers appear as labels only, with no endpoint or credential anywhere in it
