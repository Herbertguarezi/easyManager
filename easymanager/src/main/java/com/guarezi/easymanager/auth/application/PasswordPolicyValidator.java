package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.application.ports.out.BreachedPasswordChecker;
import com.guarezi.easymanager.auth.domain.WeakPasswordException;
import org.springframework.stereotype.Component;

// RF-AU14: minimum length + known-breach check (Pwned Passwords,
// k-anonymity — see PROGRESS.md Fase 1 decisions log).
@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 10;

    private final BreachedPasswordChecker breachedPasswordChecker;

    public PasswordPolicyValidator(BreachedPasswordChecker breachedPasswordChecker) {
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    // Client-facing text: kept in Portuguese, same as the rest of the
    // product's user-facing copy (see GlobalExceptionHandler's generic
    // detail message, sec. 10.5) — only code stays in English.
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new WeakPasswordException("A senha deve ter no mínimo " + MIN_LENGTH + " caracteres.");
        }
        if (breachedPasswordChecker.isBreached(rawPassword)) {
            throw new WeakPasswordException("Esta senha já apareceu em um vazamento de dados conhecido.");
        }
    }
}
