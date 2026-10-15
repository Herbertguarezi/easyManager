package com.guarezi.easymanager.shared.exceptions;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfValidationTestController {

    @GetMapping("/test/csrf-failure")
    public String fail() {
        throw new CsrfValidationException("cookie value abc123 did not match header value xyz789");
    }
}
