package com.guarezi.easymanager.shared.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marks a field whose value must never appear in logs — tokens, passwords,
// encryption keys (see docs/easy-manager-software-engineering.md sec.
// 10.3/9.5). LogSanitizer replaces it with a fixed mask before logging.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensitive {
}
