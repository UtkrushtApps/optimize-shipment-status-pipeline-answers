package com.example.shipment.config;

import com.example.shipment.logging.CorrelationIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that ensures every request has a correlation ID and that it is stored in MDC
 * so it can be propagated into async tasks.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String incomingId = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = Optional.ofNullable(incomingId)
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        CorrelationIdHolder.set(correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            log.debug("Handling request {} {} with correlationId={}", request.getMethod(), request.getRequestURI(), correlationId);
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdHolder.clear();
        }
    }
}
