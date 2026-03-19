package com.example.shipment.service;

import com.example.shipment.domain.*;
import com.example.shipment.logging.CorrelationIdHolder;
import com.example.shipment.repository.ShipmentRepository;
import com.example.shipment.repository.ShipmentStatusSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Core service that orchestrates parallel carrier lookups, aggregates statuses
 * and persists snapshots.
 */
@Service
public class ShipmentStatusService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusService.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentStatusSnapshotRepository snapshotRepository;
    private final List<CarrierClient> carrierClients;
    private final Executor carrierExecutor;

    private final long carrierTimeoutMillis;
    private final int maxRetries;
    private final long initialBackoffMillis;

    public ShipmentStatusService(ShipmentRepository shipmentRepository,
                                 ShipmentStatusSnapshotRepository snapshotRepository,
                                 List<CarrierClient> carrierClients,
                                 @Qualifier("carrierExecutor") Executor carrierExecutor,
                                 @Value("${shipment.status.carrier-timeout-ms:800}") long carrierTimeoutMillis,
                                 @Value("${shipment.status.max-retries:2}") int maxRetries,
                                 @Value("${shipment.status.initial-backoff-ms:100}") long initialBackoffMillis) {
        this.shipmentRepository = Objects.requireNonNull(shipmentRepository, "shipmentRepository must not be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.carrierClients = List.copyOf(Objects.requireNonNull(carrierClients, "carrierClients must not be null"));
        this.carrierExecutor = Objects.requireNonNull(carrierExecutor, "carrierExecutor must not be null");
        this.carrierTimeoutMillis = carrierTimeoutMillis;
        this.maxRetries = maxRetries;
        this.initialBackoffMillis = initialBackoffMillis;
    }

    /**
     * Public API used by the controller. Returns a {@link CompletableFuture} that completes
     * when all shipment statuses for the batch have been aggregated and snapshots persisted.
     */
    public CompletableFuture<ShipmentBatchStatusResponse> getBatchStatusAsync(String batchId) {
        String correlationId = CorrelationIdHolder.get();
        log.info("Starting batch status lookup for batchId={} correlationId={} shipmentsThread={}",
                batchId, correlationId, Thread.currentThread().getName());

        List<Shipment> shipments = shipmentRepository.findByBatchId(batchId);
        if (shipments.isEmpty()) {
            log.info("No shipments found for batchId={}", batchId);
            return CompletableFuture.completedFuture(new ShipmentBatchStatusResponse(batchId, List.of()));
        }

        List<CompletableFuture<ShipmentStatusResponse>> futures = shipments.stream()
                .map(this::aggregateShipmentStatusAsync)
                .collect(Collectors.toList());

        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        return allDone.thenApply(v -> {
            List<ShipmentStatusResponse> responses = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            persistSnapshots(batchId, responses);

            Duration maxLatency = responses.stream()
                    .map(ShipmentStatusService::latestStatusAge)
                    .max(Comparator.naturalOrder())
                    .orElse(Duration.ZERO);
            log.info("Finished batch status lookup for batchId={} correlationId={} shipments={} maxStatusAge={}ms",
                    batchId, correlationId, responses.size(), maxLatency.toMillis());

            return new ShipmentBatchStatusResponse(batchId, responses);
        });
    }

    private static Duration latestStatusAge(ShipmentStatusResponse response) {
        return response.getCarrierStatuses().stream()
                .map(CarrierStatus::getStatusTimestamp)
                .max(Comparator.naturalOrder())
                .map(ts -> Duration.between(ts, java.time.Instant.now()).abs())
                .orElse(Duration.ZERO);
    }

    /**
     * Aggregates carrier statuses for a single shipment, querying all carriers in parallel.
     */
    private CompletableFuture<ShipmentStatusResponse> aggregateShipmentStatusAsync(Shipment shipment) {
        if (carrierClients.isEmpty()) {
            // No carrier clients configured; return UNKNOWN status.
            ShipmentStatusResponse response = new ShipmentStatusResponse(
                    shipment.getId(),
                    shipment.getBatchId(),
                    shipment.getTrackingNumber(),
                    ShipmentStatusCode.UNKNOWN,
                    List.of()
            );
            return CompletableFuture.completedFuture(response);
        }

        List<CompletableFuture<CarrierStatus>> carrierFutures = carrierClients.stream()
                .map(client -> queryCarrierWithResilience(client, shipment))
                .collect(Collectors.toList());

        CompletableFuture<Void> allCarriersDone = CompletableFuture.allOf(carrierFutures.toArray(new CompletableFuture[0]));

        return allCarriersDone.thenApply(v -> {
            List<CarrierStatus> statuses = carrierFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            ShipmentStatusCode overall = deriveOverallStatus(statuses);
            return new ShipmentStatusResponse(
                    shipment.getId(),
                    shipment.getBatchId(),
                    shipment.getTrackingNumber(),
                    overall,
                    statuses
            );
        });
    }

    /**
     * Executes a carrier lookup with timeout and retries for transient errors.
     */
    private CompletableFuture<CarrierStatus> queryCarrierWithResilience(CarrierClient client, Shipment shipment) {
        String correlationId = CorrelationIdHolder.get();
        log.debug("Scheduling lookup for carrier={} shipmentId={} correlationId={} on thread={}",
                client.getCarrier(), shipment.getId(), correlationId, Thread.currentThread().getName());

        Supplier<CarrierStatus> supplier = () -> {
            try {
                return client.fetchStatus(shipment);
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        };

        CompletableFuture<CarrierStatus> future = CompletableFuture.supplyAsync(
                () -> callWithRetry(supplier, client, shipment),
                carrierExecutor
        );

        CarrierStatus timeoutFallback = CarrierStatus.timeout(
                client.getCarrier(),
                "Carrier lookup timed out after " + carrierTimeoutMillis + "ms");

        return future
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                    String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    log.warn("Carrier lookup failed after retries: carrier={} shipmentId={} msg={}",
                            client.getCarrier(), shipment.getId(), message);
                    return CarrierStatus.failure(client.getCarrier(), "ERROR", message);
                })
                .completeOnTimeout(timeoutFallback, carrierTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    private CarrierStatus callWithRetry(Supplier<CarrierStatus> supplier, CarrierClient client, Shipment shipment) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.debug("Calling carrier={} shipmentId={} attempt={} thread={} correlationId={}",
                        client.getCarrier(), shipment.getId(), attempt, Thread.currentThread().getName(), CorrelationIdHolder.get());
                return supplier.get();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof CarrierClientException cce) {
                    if (cce.isTransientError() && attempt <= maxRetries) {
                        sleepBackoff(attempt, client, shipment, cce);
                        continue;
                    }
                    throw ex;
                }
                throw ex;
            }
        }
    }

    private void sleepBackoff(int attempt, CarrierClient client, Shipment shipment, CarrierClientException ex) {
        long backoff = initialBackoffMillis * attempt;
        log.debug("Transient error from carrier={} shipmentId={} attempt={} backoff={}ms msg={}",
                client.getCarrier(), shipment.getId(), attempt, backoff, ex.getMessage());
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Aggregates high-level shipment status from individual carrier statuses.
     * This can be adjusted to business rules; here we use a simple precedence:
     * DELIVERED > FAILED/TIMEOUT > DELAYED > IN_TRANSIT > UNKNOWN
     */
    private ShipmentStatusCode deriveOverallStatus(Collection<CarrierStatus> statuses) {
        boolean anyDelivered = statuses.stream().anyMatch(s -> s.getStatusCode() == ShipmentStatusCode.DELIVERED);
        if (anyDelivered) {
            return ShipmentStatusCode.DELIVERED;
        }
        boolean anyFailedOrTimeout = statuses.stream().anyMatch(s ->
                s.getStatusCode() == ShipmentStatusCode.FAILED || s.getStatusCode() == ShipmentStatusCode.TIMEOUT);
        if (anyFailedOrTimeout) {
            return ShipmentStatusCode.DELAYED;
        }
        boolean anyDelayed = statuses.stream().anyMatch(s -> s.getStatusCode() == ShipmentStatusCode.DELAYED);
        if (anyDelayed) {
            return ShipmentStatusCode.DELAYED;
        }
        boolean anyInTransit = statuses.stream().anyMatch(s -> s.getStatusCode() == ShipmentStatusCode.IN_TRANSIT);
        if (anyInTransit) {
            return ShipmentStatusCode.IN_TRANSIT;
        }
        return ShipmentStatusCode.UNKNOWN;
    }

    /**
     * Persists status snapshots in batch after all shipments for the batch have been processed.
     */
    private void persistSnapshots(String batchId, List<ShipmentStatusResponse> responses) {
        List<ShipmentStatusSnapshot> snapshots = new ArrayList<>(responses.size());
        for (ShipmentStatusResponse response : responses) {
            snapshots.add(ShipmentStatusSnapshot.fromAggregated(batchId, response));
        }
        snapshotRepository.saveAll(snapshots);
    }
}
