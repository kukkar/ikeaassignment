package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;

public interface GetWarehouseOperation {

  /**
   * @throws com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException
   *     if no warehouse is currently active for that code
   */
  Warehouse getActiveByBusinessUnitCode(String businessUnitCode);
}
