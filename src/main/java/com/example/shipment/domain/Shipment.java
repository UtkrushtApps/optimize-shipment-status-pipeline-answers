package com.example.shipment.domain;

import java.util.Objects;

/**
 * Simple shipment model.
 */
public class Shipment {

    private final String id;
    private final String batchId;
    private final String trackingNumber;

    public Shipment(String id, String batchId, String trackingNumber) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
    }

    public String getId() {
        return id;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
