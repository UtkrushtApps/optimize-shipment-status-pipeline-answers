package com.example.shipment.domain;

/**
 * High-level shipment status codes used across carriers.
 */
public enum ShipmentStatusCode {
    IN_TRANSIT,
    DELIVERED,
    DELAYED,
    FAILED,
    TIMEOUT,
    UNKNOWN
}
