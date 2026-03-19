package com.example.shipment.repository;

import com.example.shipment.domain.ShipmentStatusSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryShipmentStatusSnapshotRepository implements ShipmentStatusSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryShipmentStatusSnapshotRepository.class);

    private final List<ShipmentStatusSnapshot> snapshots = new CopyOnWriteArrayList<>();

    @Override
    public void saveAll(Collection<ShipmentStatusSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        this.snapshots.addAll(snapshots);
        log.debug("Persisted {} shipment status snapshots", snapshots.size());
    }

    @Override
    public List<ShipmentStatusSnapshot> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(snapshots));
    }
}
