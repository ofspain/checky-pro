package com.themistra.crypto.watch;

/**
 * Registration failures a caller can act on.
 *
 * <p>Split by kind so the API layer maps each to the right status without inspecting messages, and
 * so a conflicting retry is never confused with a bad request.
 */
public abstract class WatchException extends RuntimeException {

    protected WatchException(String message) {
        super(message);
    }

    /** The request could never produce a workable watch. */
    public static class Invalid extends WatchException {
        public Invalid(String message) {
            super(message);
        }
    }

    /**
     * The caller's reference already names a watch with different terms.
     *
     * <p>Neither silently returning the original nor overwriting it is safe: both let a watch
     * drift from the invoice it represents, in a way the caller never learns about.
     */
    public static class Conflict extends WatchException {
        public Conflict(String callerReference) {
            super("reference '" + callerReference + "' already names a watch with different terms");
        }
    }

    /** No such watch. */
    public static class NotFound extends WatchException {
        public NotFound(String what) {
            super("no watch found for " + what);
        }
    }
}
