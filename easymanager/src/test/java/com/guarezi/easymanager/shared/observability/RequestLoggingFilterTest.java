package com.guarezi.easymanager.shared.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestLoggingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        loggerFor(RequestLoggingFilter.class).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        loggerFor(RequestLoggingFilter.class).detachAppender(appender);
    }

    @Test
    void logsMethodPathAndStatusForEveryRequest() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());

        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(logged).contains("GET");
        assertThat(logged).contains("/products");
        assertThat(logged).contains("200");
    }

    @Test
    void generatesCorrelationIdAndReturnsItInResponseHeaderWhenAbsent() throws Exception {
        MvcResult result = mockMvc.perform(get("/products")).andExpect(status().isOk()).andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(correlationId).isNotBlank();

        boolean logIncludesCorrelationId = appender.list.stream()
                .anyMatch(event -> correlationId.equals(event.getMDCPropertyMap().get("correlationId")));
        assertThat(logIncludesCorrelationId).isTrue();
    }

    @Test
    void reusesCorrelationIdProvidedInRequestHeader() throws Exception {
        String providedCorrelationId = "test-correlation-id-123";

        MvcResult result = mockMvc.perform(get("/products").header("X-Correlation-ID", providedCorrelationId))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-ID")).isEqualTo(providedCorrelationId);

        boolean logIncludesCorrelationId = appender.list.stream()
                .anyMatch(event -> providedCorrelationId.equals(event.getMDCPropertyMap().get("correlationId")));
        assertThat(logIncludesCorrelationId).isTrue();
    }

    private static Logger loggerFor(Class<?> type) {
        return (Logger) LoggerFactory.getLogger(type);
    }
}
