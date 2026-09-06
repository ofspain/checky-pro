## Purpose

Who may call this service, what a caller must present to be believed, and what the service does
when it is deployed without the means to check — given that it holds the only role permitted to
sign attestations.

## ADDED Requirements

### Requirement: Internal endpoints require an authenticated caller
Requests to internal endpoints SHALL carry a valid bearer token issued by the platform's issuer,
and SHALL be rejected otherwise.

#### Scenario: No credentials presented
- **IF** a request to an internal endpoint carries no bearer token
- **THEN** it is rejected as unauthorised and no work is performed

#### Scenario: A token from an unknown issuer
- **IF** a token was issued by an issuer other than the configured one
- **THEN** it is rejected, so a token minted elsewhere cannot register watches

#### Scenario: An expired token
- **IF** a presented token has expired
- **THEN** it is rejected

#### Scenario: A tampered token
- **IF** a token's signature does not verify against the issuer's published keys
- **THEN** it is rejected

### Requirement: A token is not an authorisation
An authenticated caller SHALL additionally hold the scope naming this capability. Possession of a
valid token issued for another purpose SHALL NOT grant access.

#### Scenario: Valid token, wrong scope
- **IF** a caller presents a valid token lacking the required scope
- **THEN** the request is rejected as forbidden, distinct from being unauthenticated

#### Scenario: Valid token with the required scope
- **WHEN** a caller presents a valid token carrying the required scope
- **THEN** the request proceeds

#### Scenario: The rejection is diagnosable
- **WHEN** a caller is rejected for a missing scope
- **THEN** the failure is distinguishable from a missing or invalid token, so a misconfigured
  client is diagnosed rather than guessed at

### Requirement: A deployment without an issuer refuses to start
Outside local development the service SHALL require an issuer to be configured, and SHALL fail to
start when one is absent.

#### Scenario: Deployed with no issuer configured
- **IF** the service starts in a non-local profile with no issuer configured
- **THEN** startup fails, rather than running with authentication effectively disabled

#### Scenario: Local development
- **WHEN** the service runs in local development without an issuer
- **THEN** it starts, so the service remains workable before an issuer exists

#### Scenario: The requirement cannot be silently disabled
- **WHEN** the flag requiring an issuer is inspected in a deployed environment
- **THEN** it is enabled by default, so disabling it is a deliberate, visible act

### Requirement: Probes do not require credentials
Liveness and readiness endpoints SHALL be reachable without authentication, so orchestration can
determine service health.

#### Scenario: Kubernetes probes the service
- **WHEN** liveness or readiness is requested without credentials
- **THEN** the response is served

#### Scenario: Other management endpoints are protected
- **IF** a management endpoint that could disclose configuration is requested without credentials
- **THEN** it is rejected

### Requirement: Authentication failures disclose nothing
A rejection SHALL NOT reveal whether a watch, chain, or caller exists, nor any configuration
detail.

#### Scenario: Unauthenticated request for a specific watch
- **WHEN** an unauthenticated caller requests a watch identifier
- **THEN** the response is identical whether or not that watch exists
