package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.GetWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GetWarehouseUseCase implements GetWarehouseOperation {

  private final WarehouseStore warehouseStore;

  public GetWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  @Override
  public Warehouse getActiveByBusinessUnitCode(String businessUnitCode) {
    Warehouse warehouse = warehouseStore.findActiveByBusinessUnitCode(businessUnitCode);
    if (warehouse == null) {
      throw new WarehouseNotFoundException(businessUnitCode);
    }
    return warehouse;
  }
}
