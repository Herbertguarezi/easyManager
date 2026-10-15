package com.guarezi.easymanager.auth.infrastructure.security;

import com.guarezi.easymanager.auth.application.ports.out.BreachedPasswordChecker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// "Pwned Passwords" via k-anonymity: only the first 5 hex chars of the
// SHA-1 hash are sent to the API; the password itself, and even its full
// hash, never leave this process (RF-AU14, see PROGRESS.md decisions log).
@Component
public class PwnedPasswordsBreachedPasswordChecker implements BreachedPasswordChecker {

    private final RestClient restClient;

    public PwnedPasswordsBreachedPasswordChecker(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://api.pwnedpasswords.com").build();
    }

    @Override
    public boolean isBreached(String rawPassword) {
        String sha1Hex = sha1Hex(rawPassword);
        String prefix = sha1Hex.substring(0, 5);
        String suffix = sha1Hex.substring(5);

        String body = restClient.get()
                .uri("/range/{prefix}", prefix)
                .retrieve()
                .body(String.class);

        if (body == null) {
            return false;
        }

        return body.lines().anyMatch(line -> line.startsWith(suffix));
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available on this JVM", e);
        }
    }
}
