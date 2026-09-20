package com.themistra.auth.authn;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit, no Spring context — {@link MockHttpServletRequest} is a plain object, not a running
 * server. Phase 11 finding #4: the {@code .strip()} fix (T20 Phase 9) had no direct test — every
 * existing caller passed already-clean strings, so a regression removing it would have gone
 * unnoticed. This is the actual unit responsible for that behavior (not {@link
 * TotpAuthenticationProvider}, which trusts its input already stripped).
 */
class TotpAuthenticationDetailsSourceTest {

    private final TotpAuthenticationDetailsSource detailsSource = new TotpAuthenticationDetailsSource();

    @Test
    void stripsLeadingAndTrailingWhitespaceFromMfaCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("mfaCode", "  123456  ");

        TotpAuthenticationDetailsSource.TotpAuthenticationDetails details = detailsSource.buildDetails(request);

        assertThat(details.mfaCode()).isEqualTo("123456");
    }

    @Test
    void leavesAnAlreadyCleanCodeUnchanged() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("mfaCode", "123456");

        TotpAuthenticationDetailsSource.TotpAuthenticationDetails details = detailsSource.buildDetails(request);

        assertThat(details.mfaCode()).isEqualTo("123456");
    }

    @Test
    void leavesMfaCodeNullWhenTheFormFieldIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        TotpAuthenticationDetailsSource.TotpAuthenticationDetails details = detailsSource.buildDetails(request);

        assertThat(details.mfaCode()).isNull();
    }

    @Test // Phase 8 finding #5's other half: the raw code must never leak via a default toString()
    void toStringRedactsTheMfaCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("mfaCode", "123456");

        String rendered = detailsSource.buildDetails(request).toString();

        assertThat(rendered).doesNotContain("123456").contains("REDACTED");
    }
}
