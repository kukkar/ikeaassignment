package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WarehouseRepositoryTest {

  @Inject WarehouseRepository warehouseRepository;
  @Inject ReplaceWarehouseOperation replaceWarehouseOperation;

  private Warehouse newWarehouse(String businessUnitCode, String location, int capacity, int stock) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    warehouse.createdAt = LocalDateTime.now();
    warehouse.archivedAt = null;
    return warehouse;
  }

  @Test
  @TestTransaction
  void testActiveQueryNeverReturnsAnArchivedVersion() {
    assertNotNull(warehouseRepository.findActiveByBusinessUnitCode("MWH.001"));

    boolean archived = warehouseRepository.archiveActive("MWH.001", LocalDateTime.now());
    assertTrue(archived);

    assertNull(warehouseRepository.findActiveByBusinessUnitCode("MWH.001"));
    assertTrue(warehouseRepository.getAll().stream().noneMatch(w -> "MWH.001".equals(w.businessUnitCode)));
  }

  @Test
  @TestTransaction
  void testCapacityAndCountQueriesExcludeArchivedRecords() {
    LocationUsage before = warehouseRepository.lockActiveUsageByLocation("ZWOLLE-001");
    assertEquals(1, before.activeWarehouseCount());
    assertEquals(40, before.activeCapacity());

    warehouseRepository.archiveActive("MWH.001", LocalDateTime.now());

    LocationUsage after = warehouseRepository.lockActiveUsageByLocation("ZWOLLE-001");
    assertEquals(0, after.activeWarehouseCount());
    assertEquals(0, after.activeCapacity());
  }

  @Test
  @TestTransaction
  void testMultipleHistoricalRowsMayShareABusinessUnitCodeButOnlyOneStaysActive() {
    warehouseRepository.archiveActive("MWH.001", LocalDateTime.now());
    warehouseRepository.create(newWarehouse("MWH.001", "ZWOLLE-001", 35, 10));

    long totalRowsForCode = warehouseRepository.count("businessUnitCode", "MWH.001");
    assertEquals(2, totalRowsForCode, "one archived historical row plus one new active row");

    Warehouse active = warehouseRepository.findActiveByBusinessUnitCode("MWH.001");
    assertNotNull(active);
    assertEquals(35, active.capacity);
    assertNull(active.archivedAt);
  }

  @Test
  @TestTransaction
  void testOnlyOneActiveVersionIsPermittedByThePartialUniqueIndex() {
    // MWH.001 is still active from the seed data - inserting another active row with the same
    // code must be rejected by the database's partial unique index, independent of any
    // application-level check.
    Warehouse duplicate = newWarehouse("MWH.001", "ZWOLLE-001", 10, 5);

    assertThrows(
        PersistenceException.class,
        () -> {
          warehouseRepository.create(duplicate);
          warehouseRepository.flush();
        });
  }

  @Test
  @TestTransaction
  void testCreatingAnArchivedHistoricalRowWithADuplicateCodeIsAllowed() {
    warehouseRepository.archiveActive("MWH.001", LocalDateTime.now());

    Warehouse anotherArchivedRow = newWarehouse("MWH.001", "ZWOLLE-001", 15, 2);
    anotherArchivedRow.archivedAt = LocalDateTime.now();

    warehouseRepository.create(anotherArchivedRow);
    warehouseRepository.flush();

    assertEquals(2, warehouseRepository.count("businessUnitCode", "MWH.001"));
    assertFalse(warehouseRepository.getAll().stream().anyMatch(w -> "MWH.001".equals(w.businessUnitCode)));
  }

  /**
   * Direct proof, with explicit transaction control (needs multiple real, concurrently-open
   * transactions, so no {@code @TestTransaction}), that {@link
   * WarehouseRepository#lockActiveByBusinessUnitCode} genuinely blocks a concurrent {@link
   * WarehouseRepository#archiveActive} at the database level - the mechanism the active-warehouse
   * race fix relies on. Uses a dedicated scratch warehouse, not the shared MWH.001 seed row, since
   * this archive is real and not rolled back.
   */
  @Test
  void testLockActiveByBusinessUnitCodeBlocksAConcurrentArchive() throws Exception {
    String code = "TEST.LOCK.RACE";
    QuarkusTransaction.requiringNew()
        .run(() -> warehouseRepository.create(newWarehouse(code, "ZWOLLE-002", 10, 1)));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch lockAcquired = new CountDownLatch(1);
      CountDownLatch releaseLock = new CountDownLatch(1);
      AtomicBoolean archiveCompleted = new AtomicBoolean(false);

      Future<?> holder =
          executor.submit(
              () ->
                  QuarkusTransaction.requiringNew()
                      .run(
                          () -> {
                            warehouseRepository.lockActiveByBusinessUnitCode(code);
                            lockAcquired.countDown();
                            try {
                              releaseLock.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                            }
                          }));

      assertTrue(lockAcquired.await(5, TimeUnit.SECONDS), "lock holder must acquire the row lock");

      Future<?> archiver =
          executor.submit(
              () -> {
                QuarkusTransaction.requiringNew()
                    .run(() -> warehouseRepository.archiveActive(code, LocalDateTime.now()));
                archiveCompleted.set(true);
              });

      // Give the archiver a real chance to run if it is (incorrectly) not blocked.
      Thread.sleep(500);
      assertFalse(
          archiveCompleted.get(), "archive must block while another transaction holds the row lock");

      releaseLock.countDown();
      holder.get(5, TimeUnit.SECONDS);
      archiver.get(5, TimeUnit.SECONDS);

      assertTrue(archiveCompleted.get(), "archive must complete once the lock is released");
    } finally {
      executor.shutdown();
      QuarkusTransaction.requiringNew().run(() -> warehouseRepository.archiveActive(code, LocalDateTime.now()));
    }
  }

  /**
   * Same proof as {@link #testLockActiveByBusinessUnitCodeBlocksAConcurrentArchive}, but against
   * {@code ReplaceWarehouseUseCase.replace} - it takes the same row lock as its own first step, so
   * it must block on a concurrently-held lock exactly like archive does.
   */
  @Test
  void testLockActiveByBusinessUnitCodeBlocksAConcurrentReplace() throws Exception {
    String code = "TEST.LOCK.REPL.RACE";
    QuarkusTransaction.requiringNew()
        .run(() -> warehouseRepository.create(newWarehouse(code, "ZWOLLE-002", 10, 5)));

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch lockAcquired = new CountDownLatch(1);
      CountDownLatch releaseLock = new CountDownLatch(1);
      AtomicBoolean replaceCompleted = new AtomicBoolean(false);

      Future<?> holder =
          executor.submit(
              () ->
                  QuarkusTransaction.requiringNew()
                      .run(
                          () -> {
                            // Simulates AssignWarehouseToProductForStoreUseCase's own first step.
                            warehouseRepository.lockActiveByBusinessUnitCode(code);
                            lockAcquired.countDown();
                            try {
                              releaseLock.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                            }
                          }));

      assertTrue(lockAcquired.await(5, TimeUnit.SECONDS), "lock holder must acquire the row lock");

      Future<?> replacer =
          executor.submit(
              () -> {
                QuarkusTransaction.requiringNew()
                    .run(
                        () -> {
                          Warehouse replacement = newWarehouse(code, "ZWOLLE-002", 12, 5);
                          replaceWarehouseOperation.replace(code, replacement);
                        });
                replaceCompleted.set(true);
              });

      // Give the replacer a real chance to run if it is (incorrectly) not blocked.
      Thread.sleep(500);
      assertFalse(
          replaceCompleted.get(), "replace must block while another transaction holds the row lock");

      releaseLock.countDown();
      holder.get(5, TimeUnit.SECONDS);
      replacer.get(5, TimeUnit.SECONDS);

      assertTrue(replaceCompleted.get(), "replace must complete once the lock is released");

      Warehouse active = warehouseRepository.findActiveByBusinessUnitCode(code);
      assertNotNull(active);
      assertEquals(12, active.capacity, "the replacement, not the original, must be active afterward");
    } finally {
      executor.shutdown();
      QuarkusTransaction.requiringNew().run(() -> warehouseRepository.archiveActive(code, LocalDateTime.now()));
    }
  }

  /**
   * The activation lock's own blocking behaviour, proven directly since a real {@code create()}
   * call can't be paused mid-flight. {@code FulfilmentAssignmentResourceTest
   * #testReactivatingAnArchivedWarehouseCodeBlocksAConcurrentStoreLimitEvaluation} proves the real
   * end-to-end scenario on top of this.
   */
  @Test
  void testLockForActivationSerializesConcurrentAcquisitionsForTheSameCode() throws Exception {
    String code = "TEST.LOCK.ACTIVATION.RACE";

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch lockAcquired = new CountDownLatch(1);
      CountDownLatch releaseLock = new CountDownLatch(1);
      AtomicBoolean secondAcquired = new AtomicBoolean(false);

      Future<?> holder =
          executor.submit(
              () ->
                  QuarkusTransaction.requiringNew()
                      .run(
                          () -> {
                            warehouseRepository.lockForActivation(code);
                            lockAcquired.countDown();
                            try {
                              releaseLock.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                            }
                          }));

      assertTrue(lockAcquired.await(5, TimeUnit.SECONDS), "first transaction must acquire the activation lock");

      Future<?> contender =
          executor.submit(
              () -> {
                QuarkusTransaction.requiringNew().run(() -> warehouseRepository.lockForActivation(code));
                secondAcquired.set(true);
              });

      // Give the contender a real chance to run if it is (incorrectly) not blocked.
      Thread.sleep(500);
      assertFalse(
          secondAcquired.get(),
          "a second transaction must block while another holds the activation lock for the same code");

      releaseLock.countDown();
      holder.get(5, TimeUnit.SECONDS);
      contender.get(5, TimeUnit.SECONDS);

      assertTrue(secondAcquired.get(), "the second transaction must acquire the lock once the first releases it");
    } finally {
      executor.shutdown();
    }
  }
}
