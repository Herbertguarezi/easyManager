package com.guarezi.easymanager.billing.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.guarezi.easymanager.billing.application.ports.out.ExchangeRateProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

// RN-TOKEN-002: public, keyless, free — chosen with the user for daily
// USD/BRL quotes (see PROGRESS.md Fase 1.5 Block D decisions log).
// Same RestClient pattern as PwnedPasswordsBreachedPasswordChecker.
@Component
public class AwesomeApiExchangeRateProvider implements ExchangeRateProvider {

    private final RestClient restClient;

    public AwesomeApiExchangeRateProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://economia.awesomeapi.com.br").build();
    }

    @Override
    public BigDecimal fetchUsdBrlRate() {
        AwesomeApiResponse response = restClient.get()
                .uri("/last/USD-BRL")
                .retrieve()
                .body(AwesomeApiResponse.class);

        if (response == null || response.usdBrl() == null) {
            throw new IllegalStateException("AwesomeAPI returned no USD-BRL quote");
        }

        return new BigDecimal(response.usdBrl().bid());
    }

    // AwesomeAPI's response shape: {"USDBRL": {"bid": "5.4200", ...}}.
    private record AwesomeApiResponse(@JsonProperty("USDBRL") UsdBrlQuote usdBrl) {
    }

    private record UsdBrlQuote(String bid) {
    }
}
