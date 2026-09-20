package com.themistra.auth.authz;

import com.themistra.auth.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** All admin-only, so no enumeration-safety concern applies here (unlike account registration).
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is load-bearing — see {@code SessionExceptionHandler}'s
 * Javadoc for why a domain-specific advice needs this to reliably outrank
 * {@code ApiExceptionHandler}'s catch-all {@code Exception.class} handler.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthzExceptionHandler {

    @ExceptionHandler({RoleNotFoundException.class, RoleTemplateNotFoundException.class})
    ProblemDetail onNotFound(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.NOT_FOUND);
        problem.setTitle("Role or role template not found");
        problem.setDetail(e.getMessage());
        return problem;
    }

    @ExceptionHandler({DuplicateRoleException.class, DuplicateRoleTemplateException.class})
    ProblemDetail onDuplicate(RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(ProblemTypes.CONFLICT);
        problem.setTitle(e.getMessage());
        return problem;
    }
}
