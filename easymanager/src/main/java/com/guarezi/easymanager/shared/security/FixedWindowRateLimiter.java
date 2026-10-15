package com.guarezi.easymanager.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Simple in-memory fixed-window counter — no external dependency needed
// for this MVP's single-instance scale (see PROGRESS.md Fase 1 decisions
// log). Known limitations, deliberately not addressed here: resets on
// restart, doesn't coordinate across instances if the app ever scales
// horizontally, and the key map grows unbounded (no eviction of stale
// keys) — would need a real distributed limiter (e.g. Redis-backed) or
// at least a bounded cache before that becomes a problem in production.
public class FixedWindowRateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    public boolean allow(String key) {
        Instant now = clock.instant();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.start.plus(window).isBefore(now)) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return current.count.get() <= limit;
    }

    private record Window(Instant start, AtomicInteger count) {
    }
}
