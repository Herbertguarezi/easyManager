package com.guarezi.easymanager.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1), clock);

    @Test
    void allowsUpToTheLimitWithinTheWindowThenRejects() {
        assertThat(limiter.allow("key")).isTrue();
        assertThat(limiter.allow("key")).isTrue();
        assertThat(limiter.allow("key")).isTrue();
        assertThat(limiter.allow("key")).isFalse();
    }

    @Test
    void resetsOnceTheWindowHasPassed() {
        limiter.allow("key");
        limiter.allow("key");
        limiter.allow("key");
        assertThat(limiter.allow("key")).isFalse();

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        assertThat(limiter.allow("key")).isTrue();
    }

    @Test
    void tracksDifferentKeysIndependently() {
        limiter.allow("a");
        limiter.allow("a");
        limiter.allow("a");

        assertThat(limiter.allow("a")).isFalse();
        assertThat(limiter.allow("b")).isTrue();
    }
}
