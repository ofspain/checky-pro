package com.themistra.crypto.lookup;

/** A provider could not answer, so the 3 answers the 2-of-3 rule needs do not exist (L1). */
public class ProvidersUnavailableException extends RuntimeException {

    public ProvidersUnavailableException(String message) {
        super(message);
    }
}
