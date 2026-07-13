/**
 * Account lifecycle — registration, email verification, profile, suspension. Emits auth.user.lifecycle events via the outbox.
 *
 * <p>Module boundaries are enforced by ArchUnit: modules expose services, never entities;
 * see services/auth/docs/architecture/target-design.md §2.</p>
 */
package com.themistra.auth.account;
