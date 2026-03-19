package com.example.shipment.service;

/**
 * Exception type for carrier client errors, allows distinguishing transient vs permanent.
 */
public class CarrierClientException extends RuntimeException {

    private final boolean transientError;

    public CarrierClientException(String message, boolean transientError) {
        super(message);
        this.transientError = transientError;
    }

    public CarrierClientException(String message, Throwable cause, boolean transientError) {
        super(message, cause);
        this.transientError = transientError;
    }

    public boolean isTransientError() {
        return transientError;
    }
}
