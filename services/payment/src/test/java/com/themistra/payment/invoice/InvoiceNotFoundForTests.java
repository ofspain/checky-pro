package com.themistra.payment.invoice;

/** Lets tests in other packages throw the package-private {@link InvoiceNotFoundException}. */
public final class InvoiceNotFoundForTests {

    private InvoiceNotFoundForTests() {
    }

    public static RuntimeException exception() {
        return new InvoiceNotFoundException();
    }
}
