package com.guarezi.easymanager.shared.exceptions;

// Thrown when the caller is authenticated (or the endpoint doesn't even
// require that) but isn't allowed to do this specific thing — role
// insufficient for a @RequiresRole endpoint (Fase 1.5 Block 0), and later
// reused for "module not entitled" (Fase 1.5 Block C, RF-AU22: 403, not
// 404 — the resource exists and is the seller's, it just isn't unlocked).
// Never echoes its own message, same reasoning as AuthenticationException.
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}
