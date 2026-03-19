package com.example.shipment.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Status information returned from a single carrier.
 */
public class CarrierStatus {

    private final CarrierType carrier;
    private final ShipmentStatusCode statusCode;
    private final String description;
    private final Instant statusTimestamp;
    private final String errorCode;
    private final String errorMessage;

    public CarrierStatus(CarrierType carrier,
                         ShipmentStatusCode statusCode,
                         String description,
                         Instant statusTimestamp,
                         String errorCode,
                         String errorMessage) {
        this.carrier = Objects.requireNonNull(carrier, "carrier must not be null");
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode must not be null");
        this.description = description;
        this.statusTimestamp = statusTimestamp != null ? statusTimestamp : Instant.now();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static CarrierStatus success(CarrierType carrier, ShipmentStatusCode statusCode, String description) {
        return new CarrierStatus(carrier, statusCode, description, Instant.now(), null, null);
    }

    public static CarrierStatus failure(CarrierType carrier, String errorCode, String errorMessage) {
        return new CarrierStatus(carrier, ShipmentStatusCode.FAILED, null, Instant.now(), errorCode, errorMessage);
    }

    public static CarrierStatus timeout(CarrierType carrier, String errorMessage) {
        return new CarrierStatus(carrier, ShipmentStatusCode.TIMEOUT, null, Instant.now(), "TIMEOUT", errorMessage);
    }

    public CarrierType getCarrier() {
        return carrier;
    }

    public ShipmentStatusCode getStatusCode() {
        return statusCode;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStatusTimestamp() {
        return statusTimestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSuccessful() {
        return errorCode == null && errorMessage == null && statusCode != ShipmentStatusCode.FAILED && statusCode != ShipmentStatusCode.TIMEOUT;
    }
}
