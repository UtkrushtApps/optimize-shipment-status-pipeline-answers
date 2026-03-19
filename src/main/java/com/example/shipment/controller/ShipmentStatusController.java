package com.example.shipment.controller;

import com.example.shipment.domain.ShipmentBatchStatusResponse;
import com.example.shipment.service.ShipmentStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentStatusController {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusController.class);

    private final ShipmentStatusService shipmentStatusService;

    public ShipmentStatusController(ShipmentStatusService shipmentStatusService) {
        this.shipmentStatusService = shipmentStatusService;
    }

    /**
     * Returns aggregated statuses for all shipments in a given batch.
     * The processing pipeline is asynchronous and parallelized across carriers.
     */
    @GetMapping("/status")
    public CompletableFuture<ShipmentBatchStatusResponse> getBatchStatus(@RequestParam("batchId") String batchId) {
        log.info("Received request for shipment batch status: batchId={}", batchId);
        return shipmentStatusService.getBatchStatusAsync(batchId);
    }
}
