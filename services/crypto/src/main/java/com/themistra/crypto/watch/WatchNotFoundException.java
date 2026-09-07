package com.themistra.crypto.watch;

import java.util.UUID;

/** {@code DELETE /internal/v1/watches/{watchId}} (R19) against a {@code watchId} matching no row at
 * all - a genuine caller error (e.g. a typo'd UUID), distinct from the idempotent no-op case (an
 * existing but already-{@code UNREGISTERED}/{@code EXPIRED} row), which is not an error. Mapped to
 * {@code 404} by {@link WatchExceptionHandler}. */
class WatchNotFoundException extends RuntimeException {

    WatchNotFoundException(UUID watchId) {
        super("No watch found for watchId: " + watchId);
    }
}
