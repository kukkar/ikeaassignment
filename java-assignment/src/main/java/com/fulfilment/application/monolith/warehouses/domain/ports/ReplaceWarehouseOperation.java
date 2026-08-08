package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;

public interface ReplaceWarehouseOperation {

  /**
   * Atomically archives the currently active warehouse for {@code businessUnitCode} and inserts
   * {@code newWarehouseData} as its replacement, re-using the same business unit code.
   *
   * @return the newly active warehouse, as persisted
   */
  Warehouse replace(String businessUnitCode, Warehouse newWarehouseData);
}
