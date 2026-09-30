package com.themistra.notification.channel;

/**
 * Thrown by an {@link EmailTransport} implementation when a send attempt fails. {@code EmailChannel}
 * lets this propagate unmodified to {@code DeliveryOrchestrator} (T11, unchanged), which already
 * converts any channel exception into a {@code FAILED} {@code delivery_log} row (L11 - no duplicated
 * outcome-recording responsibility here).
 *
 * <p><strong>{@link #EmailDeliveryException(String, Throwable)} is never used to wrap a caught AWS
 * SDK exception</strong> - only {@link #EmailDeliveryException(String)}, with an already-sanitized
 * message, is. SLF4J/Logback prints a {@code Throwable}'s full "Caused by:" chain by default, so
 * attaching the original SDK exception as this class's own {@code cause} would defeat the
 * sanitization {@code SesEmailTransport} exists to provide, the moment
 * {@code DeliveryOrchestrator}'s own existing {@code log.error(..., e)} logs it. The two-constructor
 * shape exists only for ordinary Java exception-convention completeness.</p>
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
