package com.guarezi.easymanager.shared.exceptions;

// Thrown when the CSRF double-submit check fails on a cookie-authenticated
// endpoint (sec. 4.2) — missing/mismatched cookie vs. header. Never
// echoes its own message, same reasoning as AuthenticationException.
public class CsrfValidationException extends RuntimeException {
    public CsrfValidationException(String message) {
        super(message);
    }
}
