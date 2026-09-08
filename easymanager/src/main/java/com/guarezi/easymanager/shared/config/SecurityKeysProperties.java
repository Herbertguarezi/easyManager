package com.guarezi.easymanager.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

// One field per purpose, bound to one environment variable each — never
// reused across purposes (see docs/easy-manager-software-engineering.md
// sec. 4.4). Blank/missing values fail application startup immediately
// instead of surfacing as a runtime error the first time the key is used.
@Component
@ConfigurationProperties(prefix = "app.security.keys")
@Validated
public class SecurityKeysProperties {

    @NotBlank
    private String jwtSecret;

    @NotBlank
    private String marketplaceEncryptionKey;

    @NotBlank
    private String webhookSecret;

    @NotBlank
    private String emailTokenSecret;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getMarketplaceEncryptionKey() {
        return marketplaceEncryptionKey;
    }

    public void setMarketplaceEncryptionKey(String marketplaceEncryptionKey) {
        this.marketplaceEncryptionKey = marketplaceEncryptionKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getEmailTokenSecret() {
        return emailTokenSecret;
    }

    public void setEmailTokenSecret(String emailTokenSecret) {
        this.emailTokenSecret = emailTokenSecret;
    }
}
