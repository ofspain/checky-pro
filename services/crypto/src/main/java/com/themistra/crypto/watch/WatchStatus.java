package com.themistra.crypto.watch;

/** Mirrors {@code watches.status}'s own {@code chk_watch_status} CHECK constraint exactly
 * (`V1__chain_baseline.sql`) - three values, no others. This task (T15) only ever produces
 * {@link #REGISTERED} and {@link #UNREGISTERED}; {@link #EXPIRED} exists in the schema and this enum
 * for completeness (a future scheduler/task's job to set - not this task's). */
public enum WatchStatus {
    REGISTERED,
    UNREGISTERED,
    EXPIRED
}
