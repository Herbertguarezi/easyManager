package com.guarezi.easymanager.auth.presentation;

import com.guarezi.easymanager.auth.application.RefreshTokenService;
import com.guarezi.easymanager.auth.application.ports.in.AuthTokens;
import com.guarezi.easymanager.auth.application.ports.in.ConfirmEmailUseCase;
import com.guarezi.easymanager.auth.application.ports.in.ForgotPasswordUseCase;
import com.guarezi.easymanager.auth.application.ports.in.LoginUseCase;
import com.guarezi.easymanager.auth.application.ports.in.RefreshAccessTokenUseCase;
import com.guarezi.easymanager.auth.application.ports.in.RegisterSellerUseCase;
import com.guarezi.easymanager.auth.application.ports.in.ResetPasswordUseCase;
import com.guarezi.easymanager.auth.domain.Seller;
import com.guarezi.easymanager.shared.exceptions.AuthenticationException;
import com.guarezi.easymanager.shared.exceptions.CsrfValidationException;
import com.guarezi.easymanager.shared.security.SecureTokenGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {

    // sec. 4.2: exact cookie attributes are fixed, not "when the flow
    // allows" — HttpOnly (JS can never read the refresh token itself),
    // Secure (browsers won't send it over plain HTTP — a real HTTPS
    // origin, or an HTTPS-fronted dev proxy, is required to exercise this
    // from a browser; MockMvc doesn't enforce transport security so tests
    // are unaffected), SameSite=Strict, scoped to the one endpoint that
    // needs it.
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String CSRF_COOKIE = "csrf_token";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String REFRESH_PATH = "/auth/refresh";

    private final RegisterSellerUseCase registerSellerUseCase;
    private final ConfirmEmailUseCase confirmEmailUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    public AuthController(RegisterSellerUseCase registerSellerUseCase, ConfirmEmailUseCase confirmEmailUseCase,
                           LoginUseCase loginUseCase, RefreshAccessTokenUseCase refreshAccessTokenUseCase,
                           ForgotPasswordUseCase forgotPasswordUseCase, ResetPasswordUseCase resetPasswordUseCase) {
        this.registerSellerUseCase = registerSellerUseCase;
        this.confirmEmailUseCase = confirmEmailUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshAccessTokenUseCase = refreshAccessTokenUseCase;
        this.forgotPasswordUseCase = forgotPasswordUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        Seller seller = registerSellerUseCase.register(request.email(), request.password());
        RegisterResponse response = new RegisterResponse(seller.getId(), seller.getEmail(), seller.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@RequestBody ConfirmEmailRequest request) {
        confirmEmailUseCase.confirmEmail(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        AuthTokens tokens = loginUseCase.login(request.email(), request.password(), request.totpCode());

        // sec. 4.2: generated once at login, delivered via cookie AND this
        // response body — the frontend reads it from here and resends it
        // as X-CSRF-Token on every refresh call; no server-side storage
        // needed, validation is just "does the header match the cookie".
        String csrfToken = SecureTokenGenerator.generate();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookie(tokens.refreshToken()).toString(),
                        csrfCookie(csrfToken).toString())
                .body(new LoginResponse(tokens.accessToken(), csrfToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @CookieValue(name = CSRF_COOKIE, required = false) String csrfCookie,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader) {

        if (csrfCookie == null || csrfHeader == null || !csrfCookie.equals(csrfHeader)) {
            throw new CsrfValidationException("CSRF double-submit check failed for " + REFRESH_PATH);
        }
        if (refreshTokenCookie == null) {
            throw new AuthenticationException("missing " + REFRESH_TOKEN_COOKIE + " cookie");
        }

        AuthTokens tokens = refreshAccessTokenUseCase.refresh(refreshTokenCookie);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(tokens.refreshToken()).toString())
                .body(new RefreshResponse(tokens.accessToken()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        forgotPasswordUseCase.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    private static ResponseCookie refreshTokenCookie(String rawRefreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_PATH)
                .maxAge(Duration.ofDays(RefreshTokenService.REFRESH_TOKEN_TTL_DAYS))
                .build();
    }

    private static ResponseCookie csrfCookie(String csrfToken) {
        return ResponseCookie.from(CSRF_COOKIE, csrfToken)
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_PATH)
                .maxAge(Duration.ofDays(RefreshTokenService.REFRESH_TOKEN_TTL_DAYS))
                .build();
    }
}
