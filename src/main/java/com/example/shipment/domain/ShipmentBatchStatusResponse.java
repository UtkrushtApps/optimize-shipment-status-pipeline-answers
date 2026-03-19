package com.example.shipment.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Response model for a batch of shipments.
 */
public class ShipmentBatchStatusResponse {

    private final String batchId;
    private final List<ShipmentStatusResponse> shipments;

    public ShipmentBatchStatusResponse(String batchId, List<ShipmentStatusResponse> shipments) {
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.shipments = List.copyOf(Objects.requireNonNull(shipments, "shipments must not be null"));
    }

    public String getBatchId() {
        return batchId;
    }

    public List<ShipmentStatusResponse> getShipments() {
        return Collections.unmodifiableList(shipments);
    }
}
