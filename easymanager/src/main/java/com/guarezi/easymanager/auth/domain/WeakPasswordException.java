package com.guarezi.easymanager.auth.domain;

import com.guarezi.easymanager.shared.exceptions.DomainValidationException;

public class WeakPasswordException extends DomainValidationException {
    public WeakPasswordException(String message) {
        super(message);
    }
}
