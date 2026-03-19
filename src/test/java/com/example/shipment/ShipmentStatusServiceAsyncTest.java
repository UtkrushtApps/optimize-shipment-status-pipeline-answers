package com.example.shipment;

import com.example.shipment.domain.*;
import com.example.shipment.logging.CorrelationIdHolder;
import com.example.shipment.repository.InMemoryShipmentStatusSnapshotRepository;
import com.example.shipment.repository.ShipmentRepository;
import com.example.shipment.service.CarrierClient;
import com.example.shipment.service.ShipmentStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@Import(ShipmentStatusServiceAsyncTest.TestCarrierConfig.class)
public class ShipmentStatusServiceAsyncTest {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusServiceAsyncTest.class);

    @Autowired
    private ShipmentStatusService shipmentStatusService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private InMemoryShipmentStatusSnapshotRepository snapshotRepository;

    @AfterEach
    void clearCorrelationId() {
        CorrelationIdHolder.clear();
    }

    /**
     * Verifies that multiple carrier lookups per shipment are executed in parallel
     * and that correlation ID is visible inside async carrier calls.
     */
    @Test
    void carriersAreQueriedInParallelAndCorrelationIdIsPropagated() throws Exception {
        String batchId = "test-batch"; // seeded by InMemoryShipmentRepository
        String correlationId = "test-corr-123";
        CorrelationIdHolder.set(correlationId);

        int shipmentCount = shipmentRepository.findByBatchId(batchId).size();
        Assertions.assertTrue(shipmentCount > 0, "Expected seeded shipments in repository");

        long start = System.nanoTime();
        CompletableFuture<ShipmentBatchStatusResponse> future = shipmentStatusService.getBatchStatusAsync(batchId);
        ShipmentBatchStatusResponse response = future.get(5, TimeUnit.SECONDS);
        long durationMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        log.info("Async batch lookup for {} shipments and 3 carriers took {} ms", shipmentCount, durationMillis);

        // Each carrier sleeps 400ms. Sequential execution for 3 carriers would be ~1200ms per shipment.
        // We expect the total time to be significantly less than that due to parallelism.
        Assertions.assertTrue(durationMillis < 1000,
                "Expected parallel execution of carrier calls, duration was " + durationMillis + " ms");

        Assertions.assertEquals(batchId, response.getBatchId());
        Assertions.assertEquals(shipmentCount, response.getShipments().size());

        // Snapshots should have been persisted for each shipment.
        Assertions.assertFalse(snapshotRepository.findAll().isEmpty(), "Expected persisted snapshots");

        // All carrier invocations should have seen the same correlation ID set on the calling thread.
        List<String> seenCorrelationIds = TestCarrierConfig.SEEN_CORRELATION_IDS;
        Assertions.assertFalse(seenCorrelationIds.isEmpty(), "Expected carrier invocations with correlation IDs");
        for (String seenId : seenCorrelationIds) {
            Assertions.assertEquals(correlationId, seenId, "Correlation ID should propagate into async threads");
        }
    }

    @TestConfiguration
    static class TestCarrierConfig {

        // Thread-safe list to capture correlation IDs observed in carrier clients.
        static final List<String> SEEN_CORRELATION_IDS = Collections.synchronizedList(new ArrayList<>());

        @Bean(name = "carrierExecutor")
        public Executor carrierExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(5);
            executor.setMaxPoolSize(10);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("test-carrier-");
            executor.initialize();
            return executor;
        }

        @Bean
        public CarrierClient dhlTestCarrier() {
            return new RecordingSlowCarrierClient(CarrierType.DHL, 400);
        }

        @Bean
        public CarrierClient fedexTestCarrier() {
            return new RecordingSlowCarrierClient(CarrierType.FEDEX, 400);
        }

        @Bean
        public CarrierClient upsTestCarrier() {
            return new RecordingSlowCarrierClient(CarrierType.UPS, 400);
        }

        /**
         * Simple slow carrier client that records the correlation ID it sees via MDC.
         */
        static class RecordingSlowCarrierClient implements CarrierClient {

            private final CarrierType carrier;
            private final long delayMs;

            RecordingSlowCarrierClient(CarrierType carrier, long delayMs) {
                this.carrier = carrier;
                this.delayMs = delayMs;
            }

            @Override
            public CarrierType getCarrier() {
                return carrier;
            }

            @Override
            public CarrierStatus fetchStatus(com.example.shipment.domain.Shipment shipment) {
                // Record the current correlation ID visible to this async thread
                String correlationId = CorrelationIdHolder.get();
                SEEN_CORRELATION_IDS.add(correlationId);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return CarrierStatus.success(carrier, ShipmentStatusCode.IN_TRANSIT,
                        "Simulated status for tracking=" + shipment.getTrackingNumber());
            }
        }
    }
}
