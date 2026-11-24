package com.themistra.payment.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A wrong method must be a 405, not a 500.
 *
 * <p>Spring raises {@link HttpRequestMethodNotSupportedException} before any handler runs, and
 * with no mapping for it the catch-all {@code Exception} handler turned every wrong-method request
 * into an internal error with a support reference. That was true of the whole API and nothing
 * caught it, because no test ever sent a wrong method; it surfaced only when a public path lost
 * its {@code GET} handler and an ordinary browser request began returning 500.</p>
 */
class ApiExceptionHandlerMethodNotAllowedTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void reportsFourZeroFiveRatherThanAnInternalError() {
        ResponseEntity<ProblemDetail> response = handler.onMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("GET", Set.of("POST")));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(405);
        assertThat(response.getBody().getType()).isEqualTo(ProblemTypes.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getDetail()).contains("GET");
    }

    @Test
    void namesTheMethodsThatWouldHaveWorked() {
        ResponseEntity<ProblemDetail> response = handler.onMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE", Set.of("GET", "POST")));

        // A 405 with no Allow header tells the caller nothing about what to try instead.
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void survivesAnExceptionThatNamesNoSupportedMethods() {
        ResponseEntity<ProblemDetail> response = handler.onMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).isEmpty();
    }
}
