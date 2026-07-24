package com.themistra.auth.account;

import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.authn.BreachCheckClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * NIST 800-63B password policy (L2 / R8–R10): length bounds plus Have I Been Pwned breach
 * screening. A breach-check failure fails open — the password is allowed and a
 * {@code password.breach_check_failed} audit event is recorded; a failure recording that audit
 * event is itself swallowed so it can never block a password change (R10).
 */
@Service
public class PasswordPolicy {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicy.class);
    private static final String BREACH_CHECK_FAILED_EVENT = "password.breach_check_failed";

    private final PasswordPolicyProperties properties;
    private final BreachCheckClient breachCheckClient;
    private final AuditService auditService;

    public PasswordPolicy(PasswordPolicyProperties properties, BreachCheckClient breachCheckClient,
                          AuditService auditService) {
        this.properties = properties;
        this.breachCheckClient = breachCheckClient;
        this.auditService = auditService;
    }

    /**
     * @throws PasswordPolicyViolationException if the password is blank, outside the configured
     * length bounds, or has appeared in a known breach.
     */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new PasswordPolicyViolationException("Password must not be blank");
        }
        validateLength(rawPassword);
        validateNotBreached(rawPassword);
    }

    private void validateLength(String rawPassword) {
        int length = rawPassword.length();
        if (length < properties.minLength() || length > properties.maxLength()) {
            throw new PasswordPolicyViolationException(
                    "Password must be between " + properties.minLength() + " and "
                            + properties.maxLength() + " characters");
        }
    }

    private void validateNotBreached(String rawPassword) {
        if (!properties.breachCheck().enabled()) {
            return;
        }
        try {
            if (breachCheckClient.isBreached(rawPassword)) {
                throw new PasswordPolicyViolationException("Password has appeared in a known data breach");
            }
        } catch (BreachCheckClient.BreachCheckUnavailableException e) {
            recordBreachCheckFailedAudit();
        }
    }

    private void recordBreachCheckFailedAudit() {
        try {
            auditService.record(new RecordAuditEventRequest(
                    BREACH_CHECK_FAILED_EVENT, AuditOutcome.FAILURE, null, null, null, null, null, null));
        } catch (Exception e) {
            log.warn("Failed to record {} audit event", BREACH_CHECK_FAILED_EVENT, e);
        }
    }

    public static class PasswordPolicyViolationException extends RuntimeException {

        public PasswordPolicyViolationException(String message) {
            super(message);
        }
    }
}
