package com.guarezi.easymanager.auth.application.ports.in;

public record AuthTokens(String accessToken, String refreshToken) {
}
