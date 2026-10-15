package com.guarezi.easymanager.billing.infrastructure.security;

import com.guarezi.easymanager.billing.domain.Module;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Test-only controller used to exercise RequiresModuleInterceptor through
// the real dispatcher/filter chain.
@RestController
public class RequiresModuleTestController {

    @RequiresModule(Module.PRODUCTS)
    @GetMapping("/test/products-only")
    public String productsOnly() {
        return "ok";
    }

    @RequiresModule(value = Module.ADS_ANALYZER, execution = true)
    @GetMapping("/test/ads-analysis/{storeId}")
    public String runAnalysis(@PathVariable String storeId) {
        return "ok";
    }

    @GetMapping("/test/no-module-required")
    public String noModuleRequired() {
        return "ok";
    }
}
