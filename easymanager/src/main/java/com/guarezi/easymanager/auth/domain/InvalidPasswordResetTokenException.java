package com.guarezi.easymanager.auth.domain;

import com.guarezi.easymanager.shared.exceptions.DomainValidationException;

public class InvalidPasswordResetTokenException extends DomainValidationException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}
