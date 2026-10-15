package com.guarezi.easymanager.auth.presentation;

// The refresh token itself never appears here — it travels only as an
// HttpOnly cookie (sec. 4.2). csrfToken is the double-submit value the
// frontend must read from this body and resend as X-CSRF-Token on every
// POST /auth/refresh call.
public record LoginResponse(String accessToken, String csrfToken) {
}
