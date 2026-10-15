package com.guarezi.easymanager.shared.exceptions;

// Thrown when credentials are missing/invalid/expired. Never echoes its
// own message to the client (the handler always returns a fixed generic
// detail) — unlike DomainValidationException, an auth failure message
// could hint at *why* it failed (wrong password vs. unknown email),
// which is exactly the enumeration risk RF-AU16 guards against.
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
