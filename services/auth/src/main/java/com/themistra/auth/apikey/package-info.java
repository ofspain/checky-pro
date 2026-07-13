/**
 * Merchant API keys — hashed at rest, exchanged for standard JWTs at /api-keys/token; resource servers only ever validate JWTs.
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.apikey;
