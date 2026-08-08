package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.DuplicateFulfilmentAssignmentException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductWarehouseLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreWarehouseLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.WarehouseProductTypeLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.AssignWarehouseToProductForStoreOperation;
import com.fulfilment.application.monolith.fulfilment.domain.ports.CatalogGateway;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Creates a fulfilment assignment, enforcing existence, duplication, and the three business
 * limits under a fixed lock order (store, then store+product, then warehouse - see {@link
 * FulfilmentAssignmentStore#acquireAssignmentLocks}) so concurrent requests can never together
 * exceed a limit, and never deadlock against each other.
 *
 * <p>Reuses the existing {@link WarehouseStore} port to resolve {@code warehouseBusinessUnitCode}
 * to the currently active warehouse - the same port {@code CreateWarehouseUseCase} and {@code
 * ReplaceWarehouseUseCase} use - so a warehouse replacement (same code, new active row) is
 * transparent here: nothing needs to change for existing assignments to keep resolving correctly.
 *
 * <p>The active-warehouse check uses {@link WarehouseStore#lockActiveByBusinessUnitCode}, not the
 * plain read - the same row lock {@code ReplaceWarehouseUseCase} already takes on itself. Without
 * it, the check-then-insert here would race a concurrent archive/replace of the same warehouse: a
 * plain read holds no lock, so nothing would stop that other transaction from archiving the
 * warehouse and committing between this check and the insert below, leaving a brand new assignment
 * pointing at an archived-only warehouse. Taking the row lock here means a concurrent archive
 * blocks until this transaction commits (fine - the assignment was created against a genuinely
 * active warehouse, archiving it a moment later is the normal "archive without replacement"
 * behaviour), and a concurrent archive/replace that gets there first causes this call to correctly
 * observe "not found" once its blocked read re-checks the row's committed state - never a stale
 * "active" read. See README.md ("Concurrency: active-warehouse race").
 */
@ApplicationScoped
public class AssignWarehouseToProductForStoreUseCase implements AssignWarehouseToProductForStoreOperation {

  static final int MAX_WAREHOUSES_PER_PRODUCT_PER_STORE = 2;
  static final int MAX_WAREHOUSES_PER_STORE = 3;
  static final int MAX_PRODUCT_TYPES_PER_WAREHOUSE = 5;

  private final FulfilmentAssignmentStore fulfilmentAssignmentStore;
  private final CatalogGateway catalogGateway;
  private final WarehouseStore warehouseStore;
  private final Clock clock;

  public AssignWarehouseToProductForStoreUseCase(
      FulfilmentAssignmentStore fulfilmentAssignmentStore,
      CatalogGateway catalogGateway,
      WarehouseStore warehouseStore,
      Clock clock) {
    this.fulfilmentAssignmentStore = fulfilmentAssignmentStore;
    this.catalogGateway = catalogGateway;
    this.warehouseStore = warehouseStore;
    this.clock = clock;
  }

  @Override
  @Transactional
  public FulfilmentAssignment assign(Long storeId, Long productId, String warehouseBusinessUnitCode) {
    FulfilmentAssignmentValidator.validateBasicFields(storeId, productId, warehouseBusinessUnitCode);

    if (!catalogGateway.storeExists(storeId)) {
      throw new StoreNotFoundException(storeId);
    }
    if (!catalogGateway.productExists(productId)) {
      throw new ProductNotFoundException(productId);
    }
    if (warehouseStore.lockActiveByBusinessUnitCode(warehouseBusinessUnitCode) == null) {
      throw new WarehouseNotFoundException(warehouseBusinessUnitCode);
    }

    fulfilmentAssignmentStore.acquireAssignmentLocks(storeId, productId, warehouseBusinessUnitCode);

    Set<String> warehousesForProduct =
        fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(storeId, productId);
    if (warehousesForProduct.contains(warehouseBusinessUnitCode)) {
      throw new DuplicateFulfilmentAssignmentException(storeId, productId, warehouseBusinessUnitCode);
    }
    if (warehousesForProduct.size() + 1 > MAX_WAREHOUSES_PER_PRODUCT_PER_STORE) {
      throw new ProductWarehouseLimitExceededException(
          storeId, productId, MAX_WAREHOUSES_PER_PRODUCT_PER_STORE);
    }

    Set<String> warehousesForStore = fulfilmentAssignmentStore.distinctWarehousesForStore(storeId);
    if (!warehousesForStore.contains(warehouseBusinessUnitCode)
        && warehousesForStore.size() + 1 > MAX_WAREHOUSES_PER_STORE) {
      throw new StoreWarehouseLimitExceededException(storeId, MAX_WAREHOUSES_PER_STORE);
    }

    Set<Long> productsForWarehouse =
        fulfilmentAssignmentStore.distinctProductsForWarehouse(warehouseBusinessUnitCode);
    if (!productsForWarehouse.contains(productId)
        && productsForWarehouse.size() + 1 > MAX_PRODUCT_TYPES_PER_WAREHOUSE) {
      throw new WarehouseProductTypeLimitExceededException(
          warehouseBusinessUnitCode, MAX_PRODUCT_TYPES_PER_WAREHOUSE);
    }

    FulfilmentAssignment assignment = new FulfilmentAssignment();
    assignment.storeId = storeId;
    assignment.productId = productId;
    assignment.warehouseBusinessUnitCode = warehouseBusinessUnitCode;
    assignment.warehouseActive = true;
    assignment.createdAt = LocalDateTime.now(clock);

    fulfilmentAssignmentStore.create(assignment);

    return assignment;
  }
}
