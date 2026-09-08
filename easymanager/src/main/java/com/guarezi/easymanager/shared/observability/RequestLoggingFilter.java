package com.guarezi.easymanager.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Logs one line per request (method, path, status, duration, correlation
// id — see docs/easy-manager-software-engineering.md sec. 10.3/10.4)
// through LogSanitizer, so any field added later that turns out to hold
// sensitive data only needs the @Sensitive annotation, not a change here.
// Also reads/generates X-Correlation-ID, puts it in MDC for every log line
// emitted while handling the request, and echoes it back in the response.
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            RequestLogEntry entry = new RequestLogEntry(
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, correlationId);
            log.info(LogSanitizer.sanitize(entry));
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        String provided = request.getHeader(CORRELATION_ID_HEADER);
        return StringUtils.hasText(provided) ? provided : UUID.randomUUID().toString();
    }

    private record RequestLogEntry(String method, String path, int status, long durationMs, String correlationId) {
    }
}
