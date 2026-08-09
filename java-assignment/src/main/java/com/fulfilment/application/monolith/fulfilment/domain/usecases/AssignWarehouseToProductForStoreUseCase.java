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
import java.util.HashSet;
import java.util.Set;

/**
 * Creates a fulfilment assignment, enforcing existence, duplication, and the three business
 * limits under a fixed lock order (store, then store+product, then warehouse - see {@link
 * FulfilmentAssignmentStore#acquireAssignmentLocks}). Resolves {@code warehouseBusinessUnitCode}
 * via the shared {@link WarehouseStore} port, so warehouse replacement (same code, new active row)
 * is transparent here.
 *
 * <p>Three concurrency/policy details live in README.md rather than here, since each needs the
 * full before/after reasoning a short comment can't carry: why the active-warehouse check uses
 * {@link WarehouseStore#lockActiveByBusinessUnitCode} instead of a plain read ("Concurrency:
 * active-warehouse race"); why rules 1/2 filter to active-only warehouses via {@link #activeOnly}
 * but rule 3 deliberately doesn't ("Assignment lifecycle policy"); and why {@link #activeOnly} and
 * {@code CreateWarehouseUseCase} both take {@link WarehouseStore#lockForActivation} ("Concurrency:
 * warehouse reactivation race"). {@link FulfilmentAssignmentStore#existsExact} is unfiltered by
 * design too: it must catch a duplicate against any historical row, not just active ones, or the
 * database's unique constraint would reject it first as a raw conflict instead of a clean 409.
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

    if (fulfilmentAssignmentStore.existsExact(storeId, productId, warehouseBusinessUnitCode)) {
      throw new DuplicateFulfilmentAssignmentException(storeId, productId, warehouseBusinessUnitCode);
    }

    Set<String> activeWarehousesForProduct =
        activeOnly(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(storeId, productId));
    if (activeWarehousesForProduct.size() + 1 > MAX_WAREHOUSES_PER_PRODUCT_PER_STORE) {
      throw new ProductWarehouseLimitExceededException(
          storeId, productId, MAX_WAREHOUSES_PER_PRODUCT_PER_STORE);
    }

    Set<String> activeWarehousesForStore = activeOnly(fulfilmentAssignmentStore.distinctWarehousesForStore(storeId));
    if (!activeWarehousesForStore.contains(warehouseBusinessUnitCode)
        && activeWarehousesForStore.size() + 1 > MAX_WAREHOUSES_PER_STORE) {
      throw new StoreWarehouseLimitExceededException(storeId, MAX_WAREHOUSES_PER_STORE);
    }

    // Rule 3 is scoped by business-unit-code identity, not filtered by "currently active" - see
    // the class Javadoc.
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

  private Set<String> activeOnly(Set<String> warehouseBusinessUnitCodes) {
    Set<String> active = new HashSet<>();
    for (String code : warehouseBusinessUnitCodes) {
      // See the class Javadoc / README.md ("warehouse reactivation race") for why this lock is
      // needed before the plain read below.
      warehouseStore.lockForActivation(code);
      if (warehouseStore.findActiveByBusinessUnitCode(code) != null) {
        active.add(code);
      }
    }
    return active;
  }
}
