package com.example.shipment.repository;

import com.example.shipment.domain.ShipmentStatusSnapshot;

import java.util.Collection;
import java.util.List;

/**
 * Repository for persisting shipment status snapshots.
 */
public interface ShipmentStatusSnapshotRepository {

    void saveAll(Collection<ShipmentStatusSnapshot> snapshots);

    List<ShipmentStatusSnapshot> findAll();
}
