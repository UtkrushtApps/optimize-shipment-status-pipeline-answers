package com.example.shipment.repository;

import com.example.shipment.domain.Shipment;

import java.util.List;

/**
 * Repository for loading shipments by batch ID.
 * In a real system this would be backed by a database (e.g. JPA).
 */
public interface ShipmentRepository {

    List<Shipment> findByBatchId(String batchId);
}
