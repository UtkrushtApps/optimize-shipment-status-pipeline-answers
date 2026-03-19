package com.example.shipment.repository;

import com.example.shipment.domain.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory repository with some seeded data for demonstration/tests.
 */
@Repository
public class InMemoryShipmentRepository implements ShipmentRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryShipmentRepository.class);

    private final Map<String, List<Shipment>> shipmentsByBatchId = new ConcurrentHashMap<>();

    public InMemoryShipmentRepository() {
        // Seed a test batch with a few shipments so the API works out-of-the-box.
        String batchId = "test-batch";
        List<Shipment> shipments = new ArrayList<>();
        shipments.add(new Shipment(UUID.randomUUID().toString(), batchId, "TRACK-1001"));
        shipments.add(new Shipment(UUID.randomUUID().toString(), batchId, "TRACK-1002"));
        shipments.add(new Shipment(UUID.randomUUID().toString(), batchId, "TRACK-1003"));
        shipmentsByBatchId.put(batchId, Collections.unmodifiableList(shipments));

        log.info("Seeded InMemoryShipmentRepository with {} shipments for batchId={}", shipments.size(), batchId);
    }

    @Override
    public List<Shipment> findByBatchId(String batchId) {
        return shipmentsByBatchId.getOrDefault(batchId, List.of());
    }
}
