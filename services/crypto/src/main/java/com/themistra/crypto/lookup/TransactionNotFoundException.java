package com.themistra.crypto.lookup;

/** All 3 providers agree the transaction hash does not exist (R29: the 404 case). */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException() {
        super("all 3 providers agree the transaction does not exist");
    }
}
