package com.guarezi.easymanager.shared.exceptions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only controller used to force a DomainValidationException through
// the real dispatcher, so GlobalExceptionHandlerTest can assert on the
// resulting HTTP response shape.
@RestController
public class DomainValidationTestController {

    @GetMapping("/test/domain-validation")
    public String fail() {
        throw new DomainValidationException("Password must be at least 10 characters long");
    }
}
