# Solution Steps

1. Create the Spring Boot entry point `ShipmentStatusApplication` so the application can start and component scanning works under the `com.example.shipment` package.

2. Introduce async infrastructure: add `AsyncConfig` with a dedicated `ThreadPoolTaskExecutor` bean named `carrierExecutor` and attach `CorrelationIdTaskDecorator` so MDC (including correlation IDs) is propagated into async threads.

3. Implement `CorrelationIdTaskDecorator` to capture the submitting thread’s MDC context and restore it around execution of each async task.

4. Add `CorrelationIdHolder` as a small utility wrapping MDC access for the `correlationId` key, providing `set`, `get`, and `clear` methods.

5. Create `CorrelationIdFilter` (a `OncePerRequestFilter`) that runs early for every HTTP request: read `X-Correlation-Id` header (or generate a UUID if missing), store it via `CorrelationIdHolder`, and echo it back on the response header, clearing MDC in a finally block.

6. Define core domain enums and models: `CarrierType`, `ShipmentStatusCode`, `Shipment`, `CarrierStatus`, `ShipmentStatusResponse`, `ShipmentBatchStatusResponse`, and `ShipmentStatusSnapshot` with appropriate fields, constructors, getters, and static factory methods for success/failure/timeout and snapshot creation.

7. Create repository interfaces: `ShipmentRepository` with `findByBatchId(String)` and `ShipmentStatusSnapshotRepository` with `saveAll` and `findAll` methods to abstract storage for shipments and status snapshots.

8. Provide in‑memory implementations `InMemoryShipmentRepository` (seeded with a `test-batch` containing a few shipments) and `InMemoryShipmentStatusSnapshotRepository` (using a thread-safe list) so the service and tests can run without a real database.

9. Define `CarrierClient` interface to represent an external carrier API client, with methods `getCarrier()` and `fetchStatus(Shipment)`; add `CarrierClientException` to distinguish transient vs permanent client errors via an `isTransientError()` flag.

10. Implement `ShipmentStatusService` as the core async orchestration layer: inject `ShipmentRepository`, `ShipmentStatusSnapshotRepository`, `List<CarrierClient>`, and `@Qualifier("carrierExecutor") Executor` along with configuration properties for timeout, max retries, and backoff.

11. In `ShipmentStatusService.getBatchStatusAsync(batchId)`, read the current correlation ID from `CorrelationIdHolder`, load shipments for the batch, short‑circuit to an empty response if none, then for each shipment start `aggregateShipmentStatusAsync`, combine all results via `CompletableFuture.allOf`, and after completion persist snapshots and build a `ShipmentBatchStatusResponse`.

12. Implement `aggregateShipmentStatusAsync(Shipment)` in `ShipmentStatusService` to, for each configured `CarrierClient`, start a `CompletableFuture<CarrierStatus>` via `queryCarrierWithResilience`, wait on all with `CompletableFuture.allOf`, then collect the carrier statuses, derive an overall `ShipmentStatusCode` using business rules, and wrap them in a `ShipmentStatusResponse`.

13. In `ShipmentStatusService.queryCarrierWithResilience`, wrap the blocking `CarrierClient.fetchStatus` call in `CompletableFuture.supplyAsync(..., carrierExecutor)`, delegate to `callWithRetry` for retry logic, then apply `exceptionally` to convert final failures into a `CarrierStatus.failure` and `completeOnTimeout` with a `CarrierStatus.timeout` fallback to enforce a per‑carrier timeout.

14. Implement `callWithRetry` in `ShipmentStatusService` to loop attempts, calling the provided supplier, catching `CompletionException`, checking if the cause is a `CarrierClientException` marked as transient; when transient and attempts are below the configured max, sleep for an increasing backoff (using `initialBackoffMillis * attempt`) and retry, otherwise rethrow the exception.

15. Add `deriveOverallStatus` helper in `ShipmentStatusService` to compute a single shipment status from a collection of `CarrierStatus` using precedence (DELIVERED > FAILED/TIMEOUT > DELAYED > IN_TRANSIT > UNKNOWN), and `persistSnapshots` to map `ShipmentStatusResponse` objects to `ShipmentStatusSnapshot` and call `snapshotRepository.saveAll` in one batch.

16. Expose the async pipeline via `ShipmentStatusController` with `@GetMapping("/api/shipments/status")` that takes `batchId`, logs the call, and simply returns the `CompletableFuture<ShipmentBatchStatusResponse>` from `ShipmentStatusService.getBatchStatusAsync`, allowing Spring MVC to handle async responses.

17. Write an async‑aware integration test `ShipmentStatusServiceAsyncTest` annotated with `@SpringBootTest` and `@Import(TestCarrierConfig.class)` to bootstrap the real service but override carrier clients and executor with deterministic test doubles.

18. Inside `TestCarrierConfig`, define a test `carrierExecutor` `ThreadPoolTaskExecutor` and three `RecordingSlowCarrierClient` beans implementing `CarrierClient`, each sleeping for a fixed delay (e.g., 400 ms) and recording the correlation ID they see via `CorrelationIdHolder` into a shared thread‑safe list.

19. In `ShipmentStatusServiceAsyncTest.carriersAreQueriedInParallelAndCorrelationIdIsPropagated`, set a known correlation ID via `CorrelationIdHolder.set`, call `getBatchStatusAsync("test-batch")`, measure elapsed time to assert it’s significantly less than the sum of carrier delays (confirming parallelism), verify response contents and that snapshots were persisted, and finally assert that all recorded correlation IDs from `RecordingSlowCarrierClient` equal the original correlation ID, confirming MDC‑based correlation propagation across async boundaries.

20. Add a cleanup `@AfterEach` in the test to clear the correlation ID from MDC after each test run to avoid cross‑test interference.

