package com.guarezi.easymanager.auth.application;

import com.guarezi.easymanager.auth.domain.WeakPasswordException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyValidatorTest {

    @Test
    void rejectsPasswordShorterThanMinimumLength() {
        PasswordPolicyValidator validator = new PasswordPolicyValidator(rawPassword -> false);

        assertThatThrownBy(() -> validator.validate("short1"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void rejectsBreachedPasswordEvenIfLongEnough() {
        PasswordPolicyValidator validator = new PasswordPolicyValidator(rawPassword -> true);

        assertThatThrownBy(() -> validator.validate("longenoughpassword"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void acceptsLongEnoughUnbreachedPassword() {
        PasswordPolicyValidator validator = new PasswordPolicyValidator(rawPassword -> false);

        assertThatCode(() -> validator.validate("longenoughpassword"))
                .doesNotThrowAnyException();
    }
}
