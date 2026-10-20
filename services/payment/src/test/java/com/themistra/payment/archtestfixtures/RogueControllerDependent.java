package com.themistra.payment.archtestfixtures;

import com.themistra.payment.invoice.InvoiceController;

/** FU-1 (open-issues sweep, 2026-10-19). A non-controller class depending on a controller, an
 * upward dependency, so the layering rule's second half can be shown to fail. Not a bean. */
public class RogueControllerDependent {

    private InvoiceController controller;

    InvoiceController controller() {
        return controller;
    }
}
