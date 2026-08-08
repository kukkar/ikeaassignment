package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.CatalogGateway;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import com.fulfilment.application.monolith.fulfilment.domain.ports.ListStoreFulfilmentAssignmentsOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assignment rows are never rewritten or hidden when their warehouse is later archived (see
 * README.md "Archive without replacement") - instead, every listed assignment is stamped with
 * whether its warehouse is <em>currently</em> active, computed here rather than stored, so it's
 * always up to date. The lookup is cached per distinct warehouse code within one call: a store can
 * reference at most 3 distinct warehouses (rule 2), so this is at most 3 extra reads regardless of
 * how many assignment rows are returned.
 */
@ApplicationScoped
public class ListStoreFulfilmentAssignmentsUseCase implements ListStoreFulfilmentAssignmentsOperation {

  private final FulfilmentAssignmentStore fulfilmentAssignmentStore;
  private final CatalogGateway catalogGateway;
  private final WarehouseStore warehouseStore;

  public ListStoreFulfilmentAssignmentsUseCase(
      FulfilmentAssignmentStore fulfilmentAssignmentStore,
      CatalogGateway catalogGateway,
      WarehouseStore warehouseStore) {
    this.fulfilmentAssignmentStore = fulfilmentAssignmentStore;
    this.catalogGateway = catalogGateway;
    this.warehouseStore = warehouseStore;
  }

  @Override
  public List<FulfilmentAssignment> listForStore(Long storeId) {
    if (!catalogGateway.storeExists(storeId)) {
      throw new StoreNotFoundException(storeId);
    }
    return withWarehouseActiveFlag(fulfilmentAssignmentStore.listByStore(storeId));
  }

  @Override
  public List<FulfilmentAssignment> listForStoreAndProduct(Long storeId, Long productId) {
    if (!catalogGateway.storeExists(storeId)) {
      throw new StoreNotFoundException(storeId);
    }
    if (!catalogGateway.productExists(productId)) {
      throw new ProductNotFoundException(productId);
    }
    return withWarehouseActiveFlag(fulfilmentAssignmentStore.listByStoreAndProduct(storeId, productId));
  }

  private List<FulfilmentAssignment> withWarehouseActiveFlag(List<FulfilmentAssignment> assignments) {
    Map<String, Boolean> activeByCode = new HashMap<>();
    for (FulfilmentAssignment assignment : assignments) {
      assignment.warehouseActive =
          activeByCode.computeIfAbsent(
              assignment.warehouseBusinessUnitCode,
              code -> warehouseStore.findActiveByBusinessUnitCode(code) != null);
    }
    return assignments;
  }
}
