package com.guarezi.easymanager.shared.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

// Never returns a stack trace or the real exception message to the client
// (see docs/easy-manager-software-engineering.md sec. 10.5) — full detail
// goes only to the server log, the response body stays generic.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
