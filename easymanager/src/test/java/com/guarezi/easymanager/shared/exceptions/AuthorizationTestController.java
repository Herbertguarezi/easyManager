package com.guarezi.easymanager.shared.exceptions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only controller used to force an AuthorizationException through
// the real dispatcher, so GlobalExceptionHandlerTest can assert on the
// resulting HTTP response shape.
@RestController
public class AuthorizationTestController {

    @GetMapping("/test/authorization-failure")
    public String fail() {
        throw new AuthorizationException("seller lacks role ADMIN for module ADS_ANALYZER");
    }
}
