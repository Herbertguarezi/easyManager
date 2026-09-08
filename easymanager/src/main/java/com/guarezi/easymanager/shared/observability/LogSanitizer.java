package com.guarezi.easymanager.shared.observability;

import java.lang.reflect.Field;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Builds a loggable representation of an object with every @Sensitive
// field replaced by a fixed mask, so callers never have to remember to
// scrub secrets by hand before writing a log line.
public final class LogSanitizer {

    private static final String MASK = "***";

    private LogSanitizer() {
    }

    public static String sanitize(Object target) {
        if (target == null) {
            return "null";
        }

        String fields = Stream.of(target.getClass().getDeclaredFields())
                .map(field -> describe(target, field))
                .collect(Collectors.joining(", "));

        return target.getClass().getSimpleName() + "{" + fields + "}";
    }

    private static String describe(Object target, Field field) {
        field.setAccessible(true);
        try {
            Object value = field.isAnnotationPresent(Sensitive.class) ? MASK : field.get(target);
            return field.getName() + "=" + value;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read field " + field.getName() + " for logging", e);
        }
    }
}
