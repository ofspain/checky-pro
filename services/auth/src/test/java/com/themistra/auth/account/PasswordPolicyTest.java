package com.themistra.auth.account;

import com.themistra.auth.account.PasswordPolicyProperties.BreachCheck;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.authn.BreachCheckClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordPolicy} — R8 (length), R9 (breach rejection), R10 (fail-open +
 * audit). Plain JUnit + Mockito, no Spring context, per {@code agents.md}.
 */
@ExtendWith(MockitoExtension.class)
class PasswordPolicyTest {

    private static final String URL_PREFIX = "https://api.pwnedpasswords.com/range/";

    @Mock
    private BreachCheckClient breachCheckClient;

    @Mock
    private AuditService auditService;

    private PasswordPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PasswordPolicy(propertiesWithBreachCheckEnabled(true), breachCheckClient, auditService);
    }

    @Test
    void shouldRejectPasswordShorterThan12OrLongerThan128() {
        assertThatThrownBy(() -> policy.validate("a".repeat(11)))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> policy.validate("a".repeat(129)))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);

        verifyNoInteractions(breachCheckClient);
    }

    @Test
    void shouldAcceptPasswordAtExactly12And128CharacterBoundaries() {
        when(breachCheckClient.isBreached(anyString())).thenReturn(false);

        assertThatCode(() -> policy.validate("a".repeat(12))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("a".repeat(128))).doesNotThrowAnyException();

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldRejectNullOrBlankPassword() {
        assertThatThrownBy(() -> policy.validate(null))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> policy.validate(""))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> policy.validate("            "))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);

        verifyNoInteractions(breachCheckClient);
    }

    @Test
    void shouldRejectBreachedPasswordUsingHibpRange() {
        when(breachCheckClient.isBreached("correct-horse-battery")).thenReturn(true);

        assertThatThrownBy(() -> policy.validate("correct-horse-battery"))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure() {
        when(breachCheckClient.isBreached("correct-horse-battery"))
                .thenThrow(new BreachCheckClient.BreachCheckUnavailableException("down", new RuntimeException()));

        assertThatCode(() -> policy.validate("correct-horse-battery")).doesNotThrowAnyException();

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        RecordAuditEventRequest recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo("password.breach_check_failed");
        assertThat(recorded.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(recorded.accountUuid()).isNull();
        assertThat(recorded.actorUuid()).isNull();
    }

    @Test
    void shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen() {
        when(breachCheckClient.isBreached("correct-horse-battery"))
                .thenThrow(new BreachCheckClient.BreachCheckUnavailableException("down", new RuntimeException()));
        doThrow(new RuntimeException("db unavailable")).when(auditService).record(any());

        assertThatCode(() -> policy.validate("correct-horse-battery")).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipBreachCheckWhenDisabledInConfig() {
        PasswordPolicy disabledPolicy =
                new PasswordPolicy(propertiesWithBreachCheckEnabled(false), breachCheckClient, auditService);

        assertThatCode(() -> disabledPolicy.validate("correct-horse-battery")).doesNotThrowAnyException();

        verifyNoInteractions(breachCheckClient);
    }

    private static PasswordPolicyProperties propertiesWithBreachCheckEnabled(boolean enabled) {
        return new PasswordPolicyProperties(12, 128, new BreachCheck(enabled, URL_PREFIX, 3000));
    }
}
