package com.fulfilment.application.monolith.fulfilment.domain.ports;

import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import java.util.List;
import java.util.Set;

/**
 * Persistence port for fulfilment assignments.
 *
 * <p>The three "distinct" queries return the actual set (not just a count), and every row
 * regardless of warehouse status - this port has no notion of "active"; filtering for the three
 * business limits is {@code AssignWarehouseToProductForStoreUseCase}'s job, see README.md
 * ("Assignment lifecycle policy").
 */
public interface FulfilmentAssignmentStore {

  /** Inserts a new assignment row. On return, {@code assignment.id} is populated. */
  void create(FulfilmentAssignment assignment);

  /** @return {@code true} if a row for this id and store was found and deleted. */
  boolean deleteByIdForStore(Long assignmentId, Long storeId);

  List<FulfilmentAssignment> listByStore(Long storeId);

  List<FulfilmentAssignment> listByStoreAndProduct(Long storeId, Long productId);

  /** Whether a row already exists for this exact store+product+warehouse-code triple, any warehouse status. */
  boolean existsExact(Long storeId, Long productId, String warehouseBusinessUnitCode);

  /** Distinct warehouse business unit codes currently assigned to this store+product pair. */
  Set<String> distinctWarehousesForStoreAndProduct(Long storeId, Long productId);

  /** Distinct warehouse business unit codes currently assigned to this store, across all products. */
  Set<String> distinctWarehousesForStore(Long storeId);

  /** Distinct product ids currently assigned to this warehouse, across all stores. */
  Set<Long> distinctProductsForWarehouse(String warehouseBusinessUnitCode);

  /**
   * Acquires transaction-scoped advisory locks for the three dimensions an assignment write can
   * affect, in a fixed global order - store, then store+product, then warehouse - so concurrent
   * requests that touch overlapping dimensions always request locks in the same order and can
   * never deadlock against each other. Held until the current transaction commits or rolls back.
   */
  void acquireAssignmentLocks(Long storeId, Long productId, String warehouseBusinessUnitCode);
}
