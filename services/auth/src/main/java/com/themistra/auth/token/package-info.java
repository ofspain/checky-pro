/**
 * SAS configuration — registered clients, claims customizer, JWKS and dual-key rotation (D-011), authorization persistence with hashed refresh tokens and families (D-003).
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.token;
