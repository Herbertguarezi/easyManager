package com.guarezi.easymanager.shared.exceptions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only controller used to force an AuthenticationException through
// the real dispatcher, so GlobalExceptionHandlerTest can assert on the
// resulting HTTP response shape.
@RestController
public class AuthenticationTestController {

    @GetMapping("/test/authentication-failure")
    public String fail() {
        throw new AuthenticationException("wrong password for seller@example.com");
    }
}
