package com.guarezi.easymanager.shared.exceptions;

// Base type for exceptions whose message is safe to return to the client
// as-is — a validation/business-rule failure the caller can act on,
// unlike an arbitrary internal exception (see GlobalExceptionHandler,
// which never echoes those). Bounded contexts throw their own
// subclasses; this stays in `shared` only as the common contract, it
// never depends on a specific bounded context.
public class DomainValidationException extends RuntimeException {
    public DomainValidationException(String message) {
        super(message);
    }
}
