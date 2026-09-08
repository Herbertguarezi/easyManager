package com.guarezi.easymanager.shared.exceptions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only controller used to force an unhandled exception through the
// real dispatcher, so GlobalExceptionHandlerTest can assert on the
// resulting HTTP response shape.
@RestController
public class BoomTestController {

    @GetMapping("/test/boom")
    public String boom() {
        throw new RuntimeException("internal detail that must never reach the client: password=hunter2");
    }
}
