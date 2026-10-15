package com.guarezi.easymanager.billing.infrastructure.external;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(AwesomeApiExchangeRateProvider.class)
class AwesomeApiExchangeRateProviderTest {

    @Autowired
    private AwesomeApiExchangeRateProvider provider;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void parsesTheBidFieldFromTheUsdBrlQuote() {
        server.expect(requestTo("https://economia.awesomeapi.com.br/last/USD-BRL"))
                .andRespond(withSuccess(
                        "{\"USDBRL\":{\"code\":\"USD\",\"codein\":\"BRL\",\"bid\":\"5.4200\",\"ask\":\"5.4250\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.fetchUsdBrlRate()).isEqualTo(new BigDecimal("5.4200"));
    }
}
