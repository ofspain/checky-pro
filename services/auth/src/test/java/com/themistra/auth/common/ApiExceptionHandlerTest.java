package com.themistra.auth.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private record LoginForm(@Size(min = 12) String password) {
    }

    @Test
    void validationProblemListsFieldsButNeverRejectedValues() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError(
                "request", "password", "hunter2-secret", false, null, null, "size must be at least 12"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(mock(MethodParameter.class), binding);

        ProblemDetail problem = handler.onValidationFailure(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION_ERROR);

        @SuppressWarnings("unchecked")
        var violations = (List<ApiExceptionHandler.Violation>) problem.getProperties().get("violations");
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().field()).isEqualTo("password");

        // the submitted secret must not appear anywhere in the problem body
        assertThat(problem.toString()).doesNotContain("hunter2-secret");
    }

    @Test
    void constraintViolationsMapToValidationProblem() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var violations = validator.validate(new LoginForm("short"));
            ProblemDetail problem =
                    handler.onConstraintViolation(new ConstraintViolationException(violations));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION_ERROR);
            assertThat(problem.toString()).doesNotContain("short");
        }
    }

    @Test
    void unexpectedExceptionsAreOpaqueWithTraceId() {
        ProblemDetail problem =
                handler.onUnexpected(new IllegalStateException("secret internal detail: db password"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.INTERNAL_ERROR);
        assertThat(problem.getProperties().get("trace_id")).isNotNull();

        // internal exception details must never reach the response body
        assertThat(problem.toString()).doesNotContain("secret internal detail");
    }
}
