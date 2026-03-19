package com.example.shipment.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated status for a single shipment across all carriers.
 */
public class ShipmentStatusResponse {

    private final String shipmentId;
    private final String batchId;
    private final String trackingNumber;
    private final ShipmentStatusCode overallStatus;
    private final List<CarrierStatus> carrierStatuses;

    public ShipmentStatusResponse(String shipmentId,
                                  String batchId,
                                  String trackingNumber,
                                  ShipmentStatusCode overallStatus,
                                  List<CarrierStatus> carrierStatuses) {
        this.shipmentId = Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        this.batchId = Objects.requireNonNull(batchId, "batchId must not be null");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        this.overallStatus = Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        this.carrierStatuses = List.copyOf(Objects.requireNonNull(carrierStatuses, "carrierStatuses must not be null"));
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatusCode getOverallStatus() {
        return overallStatus;
    }

    public List<CarrierStatus> getCarrierStatuses() {
        return Collections.unmodifiableList(carrierStatuses);
    }
}
