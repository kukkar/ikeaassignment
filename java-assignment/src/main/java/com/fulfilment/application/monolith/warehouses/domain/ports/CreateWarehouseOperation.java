package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;

public interface CreateWarehouseOperation {

  /**
   * Validates and persists a new active warehouse. On success, {@code warehouse} is mutated
   * in-place with the assigned {@code createdAt} (and {@code archivedAt} left {@code null}).
   */
  void create(Warehouse warehouse);
}
