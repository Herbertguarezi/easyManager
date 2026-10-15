package com.guarezi.easymanager.auth.application.ports.in;

public interface ResetPasswordUseCase {
    void resetPassword(String rawToken, String newPassword);
}
