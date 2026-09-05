package com.themistra.auth.token;

import com.themistra.auth.common.ProblemTypes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of the handler method itself — mirrors {@code ApiKeyExceptionHandlerTest}'s
 * established shape for this exact kind of single-cause-hidden rejection.
 */
class SessionExceptionHandlerTest {

    private final SessionExceptionHandler handler = new SessionExceptionHandler();

    @Test // R37, R46 - fixed status/type/title, no variable detail
    void onNotFoundReturnsUniform404() {
        ProblemDetail problem = handler.onNotFound(new SessionNotFoundException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.SESSION_NOT_FOUND);
        assertThat(problem.getTitle()).isEqualTo("Session not found");
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).isNull();
    }

    @Test // R37 - identical regardless of which of the two causes constructed the exception
    void onNotFoundResponseIsIdenticalRegardlessOfConstructionSite() {
        ProblemDetail first = handler.onNotFound(new SessionNotFoundException());
        ProblemDetail second = handler.onNotFound(new SessionNotFoundException());

        assertThat(first.getStatus()).isEqualTo(second.getStatus());
        assertThat(first.getType()).isEqualTo(second.getType());
        assertThat(first.getTitle()).isEqualTo(second.getTitle());
        assertThat(first.getDetail()).isEqualTo(second.getDetail());
    }
}
