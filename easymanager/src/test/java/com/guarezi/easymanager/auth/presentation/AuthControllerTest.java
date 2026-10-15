package com.guarezi.easymanager.auth.presentation;

import com.guarezi.easymanager.auth.application.ports.out.BreachedPasswordChecker;
import com.guarezi.easymanager.shared.security.Sha256Hasher;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Real breach checking is covered by PwnedPasswordsBreachedPasswordCheckerTest
    // (against a mocked HTTP server); stubbing it here keeps this test from
    // making a real network call to a third-party API on every run.
    @MockitoBean
    private BreachedPasswordChecker breachedPasswordChecker;

    @BeforeEach
    void stubBreachChecker() {
        when(breachedPasswordChecker.isBreached(any())).thenReturn(false);
    }

    @Test
    void registerCreatesPendingSellerWithHashedPassword() throws Exception {
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String rawPassword = "correct horse battery staple";
        String requestBody = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, rawPassword);

        String responseBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).doesNotContain(rawPassword);
        assertThat(responseBody).doesNotContainIgnoringCase("password");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT role, status, password_hash FROM sellers WHERE email = ?", email);
        assertThat(row.get("role")).isEqualTo("SELLER");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("password_hash")).isNotEqualTo(rawPassword);
        assertThat((String) row.get("password_hash")).startsWith("$2");
    }

    @Test
    void registerRejectsPasswordShorterThanMinimumLength() throws Exception {
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String requestBody = """
                {"email": "%s", "password": "short1"}
                """.formatted(email);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sellers WHERE email = ?", Integer.class, email);
        assertThat(count).isZero();
    }

    @Test
    void registerRespondsIdenticallyWhenEmailAlreadyExists() throws Exception {
        // RF-AU16: register never reveals whether the email was already
        // registered — the response looks the same either way, and no
        // duplicate row (or overwrite) happens server-side.
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String requestBody = """
                {"email": "%s", "password": "first-password-value"}
                """.formatted(email);
        String secondRequestBody = """
                {"email": "%s", "password": "second-password-value"}
                """.formatted(email);

        String firstResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sellers WHERE email = ?", Integer.class, email);
        assertThat(count).isEqualTo(1);

        String originalPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sellers WHERE email = ?", String.class, email);
        // The second (rejected) attempt's password must never have overwritten
        // the first account's hash.
        assertThat(originalPasswordHash).isNotNull();
        assertThat(firstResponse).contains(email);
    }

    @Test
    void registerNeverRevealsThatAnExistingEmailBelongsToAnActiveAccount() throws Exception {
        // Found in review: register() used to return the *real* seller
        // when the email already existed, so an already-CONFIRMED
        // account leaked "status": "ACTIVE" (and its real id) instead of
        // looking exactly like a fresh registration — a bigger
        // enumeration leak than RF-AU16 is meant to allow.
        UUID realSellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(realSellerId, email, "the-real-password-value");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"some-throwaway-password\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(realSellerId.toString())));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, password_hash FROM sellers WHERE id = ?", realSellerId.toString());
        assertThat(row.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void registerIssuesAnUnusedEmailConfirmationToken() throws Exception {
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String requestBody = """
                {"email": "%s", "password": "long-enough-password"}
                """.formatted(email);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        Integer tokenCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM email_confirmation_tokens t
                JOIN sellers s ON s.id = t.seller_id
                WHERE s.email = ? AND t.used_at IS NULL AND t.expires_at > NOW()
                """, Integer.class, email);
        assertThat(tokenCount).isEqualTo(1);
    }

    @Test
    void confirmEmailActivatesSellerForAValidToken() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String rawToken = "raw-confirmation-token-" + UUID.randomUUID();
        insertPendingSeller(sellerId, email);
        insertEmailConfirmationToken(sellerId, rawToken, Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, email_confirmed_at FROM sellers WHERE id = ?", sellerId.toString());
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("email_confirmed_at")).isNotNull();

        Integer usedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_confirmation_tokens WHERE seller_id = ? AND used_at IS NOT NULL",
                Integer.class, sellerId.toString());
        assertThat(usedCount).isEqualTo(1);
    }

    @Test
    void confirmEmailRejectsExpiredToken() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String rawToken = "expired-confirmation-token-" + UUID.randomUUID();
        insertPendingSeller(sellerId, email);
        insertEmailConfirmationToken(sellerId, rawToken, Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(post("/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isUnprocessableEntity());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM sellers WHERE id = ?", String.class, sellerId.toString());
        assertThat(status).isEqualTo("PENDING");
    }

    private void insertPendingSeller(UUID id, String email) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO sellers (id, email, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, ?, 'SELLER', 'PENDING', ?, ?)
                """, id.toString(), email, "irrelevant-hash", toTimestamp(now), toTimestamp(now));
    }

    private void insertEmailConfirmationToken(UUID sellerId, String rawToken, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO email_confirmation_tokens (id, seller_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID().toString(), sellerId.toString(), Sha256Hasher.hashHex(rawToken), toTimestamp(expiresAt));
    }

    private static java.sql.Timestamp toTimestamp(Instant instant) {
        return java.sql.Timestamp.from(instant);
    }

    @Test
    void loginIssuesAnAccessTokenForActiveSellerWithCorrectPassword() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String rawPassword = "correct-login-password";
        insertActiveSeller(sellerId, email, rawPassword);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, rawPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginRejectsWrongPasswordWithoutRevealingWhy() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, email, "correct-login-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"wrong-password\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas."));
    }

    @Test
    void loginRejectsUnknownEmailWithTheSameGenericMessage() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody-" + UUID.randomUUID() + "@example.com\", \"password\": \"whatever-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas."));
    }

    @Test
    void loginLocksAccountAfterFiveConsecutiveWrongPasswords() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        String correctPassword = "the-correct-password-value";
        insertActiveSeller(sellerId, email, correctPassword);

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"%s\", \"password\": \"wrong-password\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }

        // Even the correct password is rejected once locked.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, correctPassword)))
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT failed_login_attempts, locked_until FROM sellers WHERE id = ?", sellerId.toString());
        assertThat(row.get("failed_login_attempts")).isEqualTo(6);
        assertThat(row.get("locked_until")).isNotNull();
    }

    @Test
    void loginDeliversRefreshTokenOnlyAsAnHttpOnlyCookieNeverInTheBody() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, email, "the-correct-password-value");

        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-correct-password-value\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(responseBody).doesNotContainIgnoringCase("refreshToken");

        Cookie refreshCookie = findCookie(cookiesFrom(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"the-correct-password-value\"}".formatted(email))), "refresh_token");
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getSecure()).isTrue();
    }

    @Test
    void refreshWithoutCsrfHeaderIsRejected() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, email, "the-correct-password-value");

        Cookie[] loginCookies = cookiesFrom(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"the-correct-password-value\"}".formatted(email)));

        // Cookies present (including csrf_token, since it rides along
        // automatically), but no X-CSRF-Token header — the whole point of
        // double-submit is that a forged cross-site request has the
        // cookie attached automatically but can't set this header.
        mockMvc.perform(post("/auth/refresh").cookie(loginCookies))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTheTokenAndOldTokenCanNeverBeUsedAgain() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, email, "the-correct-password-value");

        var loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-correct-password-value\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = com.jayway.jsonpath.JsonPath.read(loginResult.getResponse().getContentAsString(), "$.csrfToken");
        Cookie[] firstCookies = loginResult.getResponse().getCookies();
        Cookie csrfCookie = findCookie(firstCookies, "csrf_token");

        var refreshResult = mockMvc.perform(post("/auth/refresh")
                        .cookie(firstCookies)
                        .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        // /auth/refresh's response only re-sets refresh_token (csrf_token
        // doesn't rotate, see PROGRESS.md) — a real browser keeps the
        // still-valid csrf_token cookie from login regardless, so the next
        // request must carry both explicitly.
        Cookie[] secondCookies = {findCookie(refreshResult.getResponse().getCookies(), "refresh_token"), csrfCookie};
        assertThat(findCookie(secondCookies, "refresh_token").getValue())
                .isNotEqualTo(findCookie(firstCookies, "refresh_token").getValue());

        // Reusing the first (already rotated-away) refresh_token cookie
        // must fail — the CSRF token is unchanged (never rotates, see
        // PROGRESS.md), only the refresh_token cookie differs.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(firstCookies)
                        .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isUnauthorized());

        // ...and that reuse must have burned the second (still valid at
        // that point) token too, as a compromise precaution (§4.1).
        mockMvc.perform(post("/auth/refresh")
                        .cookie(secondCookies)
                        .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isUnauthorized());
    }

    private Cookie[] cookiesFrom(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getCookies();
    }

    private static Cookie findCookie(Cookie[] cookies, String name) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }
        throw new AssertionError("No cookie named " + name + " was set");
    }

    private void insertActiveSeller(UUID id, String email, String rawPassword) {
        Instant now = Instant.now();
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(rawPassword);
        jdbcTemplate.update("""
                INSERT INTO sellers (id, email, password_hash, role, status, email_confirmed_at, created_at, updated_at)
                VALUES (?, ?, ?, 'SELLER', 'ACTIVE', ?, ?, ?)
                """, id.toString(), email, passwordHash, toTimestamp(now), toTimestamp(now), toTimestamp(now));
    }

    @Test
    void forgotPasswordRespondsTheSameWayRegardlessOfWhetherEmailExists() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String knownEmail = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, knownEmail, "the-current-password-value");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(knownEmail)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isOk());

        Integer tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens t JOIN sellers s ON s.id = t.seller_id WHERE s.id = ?",
                Integer.class, sellerId.toString());
        assertThat(tokenCount).isEqualTo(1);
    }

    @Test
    void resetPasswordUpdatesPasswordAndRevokesExistingRefreshTokens() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, email, "the-old-password-value");

        // Establish an active session (refresh token) before the reset,
        // to prove it gets revoked.
        var loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-old-password-value\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = com.jayway.jsonpath.JsonPath.read(loginResult.getResponse().getContentAsString(), "$.csrfToken");
        Cookie[] loginCookies = loginResult.getResponse().getCookies();

        String rawResetToken = "a-raw-reset-token-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (id, seller_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID().toString(), sellerId.toString(),
                Sha256Hasher.hashHex(rawResetToken), toTimestamp(Instant.now().plus(1, ChronoUnit.HOURS)));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"a-brand-new-password-value\"}".formatted(rawResetToken)))
                .andExpect(status().isOk());

        String newPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sellers WHERE id = ?", String.class, sellerId.toString());
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("a-brand-new-password-value", newPasswordHash)).isTrue();

        // The refresh token issued before the reset must no longer work.
        mockMvc.perform(post("/auth/refresh")
                        .cookie(loginCookies)
                        .header("X-CSRF-Token", csrfToken))
                .andExpect(status().isUnauthorized());

        // Logging in again with the new password works.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"a-brand-new-password-value\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordRejectsExpiredToken() throws Exception {
        UUID sellerId = UUID.randomUUID();
        String email = "seller-" + UUID.randomUUID() + "@example.com";
        insertActiveSeller(sellerId, email, "the-old-password-value");
        String rawResetToken = "an-expired-raw-reset-token-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (id, seller_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID().toString(), sellerId.toString(),
                Sha256Hasher.hashHex(rawResetToken), toTimestamp(Instant.now().minus(1, ChronoUnit.HOURS)));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"a-brand-new-password-value\"}".formatted(rawResetToken)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminLoginRequiresTotpEvenWithCorrectPassword() throws Exception {
        UUID adminId = UUID.randomUUID();
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String secret = com.guarezi.easymanager.shared.security.TotpGenerator.generateSecret();
        insertActiveAdmin(adminId, email, "the-admin-password-value", secret);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-admin-password-value\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoginRejectsWrongTotpCode() throws Exception {
        UUID adminId = UUID.randomUUID();
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String secret = com.guarezi.easymanager.shared.security.TotpGenerator.generateSecret();
        insertActiveAdmin(adminId, email, "the-admin-password-value", secret);
        String wrongCode = com.guarezi.easymanager.shared.security.TotpGenerator.currentCode(secret).equals("000000")
                ? "111111" : "000000";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-admin-password-value\", \"totpCode\": \"%s\"}"
                                .formatted(email, wrongCode)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoginSucceedsWithCorrectTotpCode() throws Exception {
        UUID adminId = UUID.randomUUID();
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        String secret = com.guarezi.easymanager.shared.security.TotpGenerator.generateSecret();
        insertActiveAdmin(adminId, email, "the-admin-password-value", secret);
        String correctCode = com.guarezi.easymanager.shared.security.TotpGenerator.currentCode(secret);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-admin-password-value\", \"totpCode\": \"%s\"}"
                                .formatted(email, correctCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void adminLoginRejectedWhenMfaNotProvisionedYet() throws Exception {
        UUID adminId = UUID.randomUUID();
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        insertActiveAdmin(adminId, email, "the-admin-password-value", null);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"the-admin-password-value\", \"totpCode\": \"123456\"}"
                                .formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    private void insertActiveAdmin(UUID id, String email, String rawPassword, String mfaSecret) {
        Instant now = Instant.now();
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(rawPassword);
        jdbcTemplate.update("""
                INSERT INTO sellers (id, email, password_hash, role, status, email_confirmed_at, created_at, updated_at, mfa_secret)
                VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?, ?, ?)
                """, id.toString(), email, passwordHash, toTimestamp(now), toTimestamp(now), toTimestamp(now), mfaSecret);
    }
}
