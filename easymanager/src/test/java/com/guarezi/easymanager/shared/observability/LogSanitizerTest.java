package com.guarezi.easymanager.shared.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    private static final org.slf4j.Logger SLF4J_LOGGER = LoggerFactory.getLogger(LogSanitizerTest.class);

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        ((Logger) SLF4J_LOGGER).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) SLF4J_LOGGER).detachAppender(appender);
    }

    @Test
    void maskedValueNeverReachesTheFinalLogLine() {
        ExampleCredentials credentials = new ExampleCredentials("seller@example.com", "super-secret-token-value");

        SLF4J_LOGGER.info(LogSanitizer.sanitize(credentials));

        String logged = appender.list.get(0).getFormattedMessage();
        assertThat(logged).doesNotContain("super-secret-token-value");
        assertThat(logged).contains("seller@example.com");
        assertThat(logged).contains("***");
    }

    static class ExampleCredentials {
        private final String email;

        @Sensitive
        private final String token;

        ExampleCredentials(String email, String token) {
            this.email = email;
            this.token = token;
        }
    }
}
