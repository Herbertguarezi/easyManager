package com.guarezi.easymanager.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityKeysPropertiesTest {

    private static final String JWT_KEY_PRESENT = "app.security.keys.jwt-secret=jwt-secret-value";
    private static final String MARKETPLACE_KEY_PRESENT = "app.security.keys.marketplace-encryption-key=marketplace-key-value";
    private static final String WEBHOOK_KEY_PRESENT = "app.security.keys.webhook-secret=webhook-secret-value";
    private static final String EMAIL_TOKEN_KEY_PRESENT = "app.security.keys.email-token-secret=email-token-secret-value";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void startsSuccessfullyWhenAllKeysArePresent() {
        contextRunner
                .withPropertyValues(JWT_KEY_PRESENT, MARKETPLACE_KEY_PRESENT, WEBHOOK_KEY_PRESENT, EMAIL_TOKEN_KEY_PRESENT)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsFastWithClearMessageWhenJwtKeyIsMissing() {
        contextRunner
                .withPropertyValues(MARKETPLACE_KEY_PRESENT, WEBHOOK_KEY_PRESENT, EMAIL_TOKEN_KEY_PRESENT)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("jwtSecret");
                });
    }

    @Test
    void failsFastWithClearMessageWhenMarketplaceKeyIsMissing() {
        contextRunner
                .withPropertyValues(JWT_KEY_PRESENT, WEBHOOK_KEY_PRESENT, EMAIL_TOKEN_KEY_PRESENT)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("marketplaceEncryptionKey");
                });
    }

    @Test
    void failsFastWithClearMessageWhenWebhookKeyIsMissing() {
        contextRunner
                .withPropertyValues(JWT_KEY_PRESENT, MARKETPLACE_KEY_PRESENT, EMAIL_TOKEN_KEY_PRESENT)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("webhookSecret");
                });
    }

    @Test
    void failsFastWithClearMessageWhenEmailTokenKeyIsMissing() {
        contextRunner
                .withPropertyValues(JWT_KEY_PRESENT, MARKETPLACE_KEY_PRESENT, WEBHOOK_KEY_PRESENT)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("emailTokenSecret");
                });
    }

    @Configuration
    @EnableConfigurationProperties(SecurityKeysProperties.class)
    static class TestConfig {
    }
}
