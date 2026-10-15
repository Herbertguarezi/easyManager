package com.guarezi.easymanager.auth.application.ports.out;

public interface BreachedPasswordChecker {
    boolean isBreached(String rawPassword);
}
