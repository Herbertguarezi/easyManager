package com.guarezi.easymanager.shared.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.net.URI;

// Never returns a stack trace or the real exception message to the client
// (see docs/easy-manager-software-engineering.md sec. 10.5) — full detail
// goes only to the server log, the response body stays generic.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // DomainValidationException messages are authored for the client (see
    // the type's own javadoc) — safe to echo, unlike the generic handler
    // below. Registered before Exception.class so it takes precedence.
    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail handleDomainValidation(DomainValidationException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problemDetail.setTitle("Unprocessable Entity");
        problemDetail.setDetail(exception.getMessage());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    // Never echoes exception.getMessage() — even a detail as small as
    // "wrong password" vs "unknown email" is exactly the enumeration
    // signal RF-AU16 exists to prevent, so the response is always this
    // one fixed string regardless of what actually failed.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problemDetail.setTitle("Unauthorized");
        problemDetail.setDetail("Credenciais inválidas.");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    @ExceptionHandler(CsrfValidationException.class)
    public ProblemDetail handleCsrfValidation(CsrfValidationException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle("Forbidden");
        problemDetail.setDetail("Falha na validação CSRF.");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    // Fase 1.5, Block 0: role insufficient (@RequiresRole) today; module
    // not entitled (@RequiresModule) reuses this same handler from Block C
    // onward. Never echoes the message — same reasoning as
    // AuthenticationException, and the two failure kinds share one fixed
    // detail for the same reason those two share the 401 handler's.
    @ExceptionHandler(AuthorizationException.class)
    public ProblemDetail handleAuthorization(AuthorizationException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle("Forbidden");
        problemDetail.setDetail("Acesso negado.");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    // Fase 1.5, Block 0 (achado da Fase 1): without
    // spring.mvc.throw-exception-if-no-handler-found=true, an unmapped
    // route is either a plain container 404 that never reaches this
    // advice, or — worse, as originally found — gets picked up by the
    // handleUnexpectedException(Exception.class) catch-all below and
    // reported as a 500. This handler, registered ahead of that catch-all,
    // fixes both: a genuinely unmapped route is now a real 404.
    @ExceptionHandler(NoHandlerFoundException.class)
    public ProblemDetail handleNoHandlerFound(NoHandlerFoundException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle("Not Found");
        problemDetail.setDetail("Recurso não encontrado.");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), exception);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setDetail("Não foi possível processar a solicitação.");
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }
}
