package com.example.shipment.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot of a shipment's aggregated status for persistence/audit.
 */
public class ShipmentStatusSnapshot {

    private final String id;
    private final String shipmentId;
    private final String batchId;
    private final ShipmentStatusCode overallStatus;
    private final Instant createdAt;
    private final List<CarrierStatus> carrierStatuses;

    public ShipmentStatusSnapshot(String id,
                                  String shipmentId,
                                  String batchId,
                                  ShipmentStatusCode overallStatus,
                                  Instant createdAt,
                                  List<CarrierStatus> carrierStatuses) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.shipmentId = Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.overallStatus = Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.carrierStatuses = List.copyOf(Objects.requireNonNull(carrierStatuses, "carrierStatuses must not be null"));
    }

    public static ShipmentStatusSnapshot fromAggregated(String batchId, ShipmentStatusResponse response) {
        return new ShipmentStatusSnapshot(
                UUID.randomUUID().toString(),
                response.getShipmentId(),
                batchId,
                response.getOverallStatus(),
                Instant.now(),
                response.getCarrierStatuses()
        );
    }

    public String getId() {
        return id;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public String getBatchId() {
        return batchId;
    }

    public ShipmentStatusCode getOverallStatus() {
        return overallStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<CarrierStatus> getCarrierStatuses() {
        return Collections.unmodifiableList(carrierStatuses);
    }
}
