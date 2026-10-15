package com.guarezi.easymanager.auth.domain;

import com.guarezi.easymanager.shared.exceptions.DomainValidationException;

public class InvalidEmailConfirmationTokenException extends DomainValidationException {
    public InvalidEmailConfirmationTokenException(String message) {
        super(message);
    }
}
