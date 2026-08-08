package com.fulfilment.application.monolith.warehouses.domain.ports;

public interface ArchiveWarehouseOperation {

  /**
   * Archives the currently active warehouse for the given business unit code.
   *
   * @throws com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException
   *     if no warehouse is currently active for that code
   */
  void archive(String businessUnitCode);
}
