package com.example.shipment.logging;

import org.slf4j.MDC;

/**
 * Simple helper around MDC for correlation ID access.
 */
public final class CorrelationIdHolder {

    private static final String CORRELATION_ID_KEY = "correlationId";

    private CorrelationIdHolder() {
    }

    public static void set(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
    }

    public static String get() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
