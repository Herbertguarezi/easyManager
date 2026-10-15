package com.guarezi.easymanager.auth.application.ports.in;

public interface ConfirmEmailUseCase {
    void confirmEmail(String rawToken);
}
