package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WarehouseRepositoryTest {

  @Inject WarehouseRepository warehouseRepository;

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
}
