package com.guarezi.easymanager.auth.presentation;

public record ResetPasswordRequest(String token, String newPassword) {
}
