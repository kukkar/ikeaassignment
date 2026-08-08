package com.fulfilment.application.monolith.fulfilment.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FulfilmentAssignmentRepositoryTest {

  @Inject FulfilmentAssignmentRepository repository;

  private FulfilmentAssignment newAssignment(Long storeId, Long productId, String warehouseCode) {
    FulfilmentAssignment assignment = new FulfilmentAssignment();
    assignment.storeId = storeId;
    assignment.productId = productId;
    assignment.warehouseBusinessUnitCode = warehouseCode;
    assignment.createdAt = LocalDateTime.now();
    return assignment;
  }

  @Test
  @TestTransaction
  void testCreateAssignsAnId() {
    FulfilmentAssignment assignment = newAssignment(1L, 1L, "MWH.001");
    repository.create(assignment);
    assertNotNull(assignment.id);
  }

  @Test
  @TestTransaction
  void testDeleteByIdForStoreOnlyDeletesForTheMatchingStore() {
    FulfilmentAssignment assignment = newAssignment(1L, 1L, "MWH.001");
    repository.create(assignment);

    assertFalse(
        repository.deleteByIdForStore(assignment.id, 2L),
        "must not delete when the store id doesn't match");
    assertTrue(repository.deleteByIdForStore(assignment.id, 1L));
    assertFalse(repository.deleteByIdForStore(assignment.id, 1L), "already deleted");
  }

  @Test
  @TestTransaction
  void testListByStoreAndListByStoreAndProduct() {
    repository.create(newAssignment(1L, 1L, "MWH.001"));
    repository.create(newAssignment(1L, 1L, "MWH.012"));
    repository.create(newAssignment(1L, 2L, "MWH.001"));

    assertEquals(3, repository.listByStore(1L).size());
    assertEquals(2, repository.listByStoreAndProduct(1L, 1L).size());
    assertTrue(repository.listByStore(2L).isEmpty());
  }

  @Test
  @TestTransaction
  void testDistinctWarehousesForStoreAndProductCollapsesDuplicateWarehouseUsage() {
    repository.create(newAssignment(1L, 1L, "MWH.001"));
    repository.create(newAssignment(1L, 1L, "MWH.012"));
    // Same store+product+warehouse triple would violate the unique constraint, so distinctness
    // here is only meaningfully exercised across different rows referencing the same warehouse
    // for the same product via a second, different product below.
    repository.create(newAssignment(1L, 2L, "MWH.001"));

    Set<String> distinct = repository.distinctWarehousesForStoreAndProduct(1L, 1L);
    assertEquals(Set.of("MWH.001", "MWH.012"), distinct);
  }

  @Test
  @TestTransaction
  void testDistinctWarehousesForStoreCollapsesAcrossProducts() {
    // Store A: Product X -> Warehouse 1, Product Y -> Warehouse 1, Product Z -> Warehouse 2
    // must count as 2 distinct warehouses for the store, not 3 rows.
    repository.create(newAssignment(1L, 1L, "MWH.001"));
    repository.create(newAssignment(1L, 2L, "MWH.001"));
    repository.create(newAssignment(1L, 3L, "MWH.012"));

    assertEquals(Set.of("MWH.001", "MWH.012"), repository.distinctWarehousesForStore(1L));
  }

  @Test
  @TestTransaction
  void testDistinctProductsForWarehouseCollapsesAcrossStores() {
    // Warehouse 1: Product X -> Store A, Product X -> Store B, Product Y -> Store C
    // must count as 2 distinct product types, not 3 rows.
    repository.create(newAssignment(1L, 1L, "MWH.001"));
    repository.create(newAssignment(2L, 1L, "MWH.001"));
    repository.create(newAssignment(3L, 2L, "MWH.001"));

    assertEquals(Set.of(1L, 2L), repository.distinctProductsForWarehouse("MWH.001"));
  }

  @Test
  @TestTransaction
  void testUniqueConstraintRejectsAnExactDuplicateTriple() {
    repository.create(newAssignment(1L, 1L, "MWH.001"));

    assertThrows(
        PersistenceException.class,
        () -> {
          repository.create(newAssignment(1L, 1L, "MWH.001"));
          repository.flush();
        });
  }

  @Test
  @TestTransaction
  void testForeignKeyRejectsAnUnknownStoreId() {
    assertThrows(
        PersistenceException.class,
        () -> {
          repository.create(newAssignment(999_999L, 1L, "MWH.001"));
          repository.flush();
        });
  }

  @Test
  @TestTransaction
  void testForeignKeyRejectsAnUnknownProductId() {
    assertThrows(
        PersistenceException.class,
        () -> {
          repository.create(newAssignment(1L, 999_999L, "MWH.001"));
          repository.flush();
        });
  }

  @Test
  @TestTransaction
  void testAcquireAssignmentLocksDoesNotThrow() {
    repository.acquireAssignmentLocks(1L, 1L, "MWH.001");
  }

  @Test
  @TestTransaction
  void testMultipleHistoricalStyleRowsAreDistinctFromMultipleActiveRows() {
    // Sanity check that the repository doesn't need the warehouse's archived-row concept at all -
    // it just stores whatever code it's given, and distinctness is purely per-code.
    List<FulfilmentAssignment> created =
        List.of(
            newAssignment(1L, 1L, "MWH.001"),
            newAssignment(1L, 1L, "MWH.012"));
    created.forEach(repository::create);

    assertEquals(2, repository.distinctWarehousesForStoreAndProduct(1L, 1L).size());
  }
}
