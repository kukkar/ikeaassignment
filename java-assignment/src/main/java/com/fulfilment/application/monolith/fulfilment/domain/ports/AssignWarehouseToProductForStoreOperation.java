package com.fulfilment.application.monolith.fulfilment.domain.ports;

import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;

public interface AssignWarehouseToProductForStoreOperation {

  /**
   * Validates and persists a new fulfilment assignment.
   *
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductNotFoundException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.WarehouseNotFoundException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.DuplicateFulfilmentAssignmentException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductWarehouseLimitExceededException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreWarehouseLimitExceededException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.WarehouseProductTypeLimitExceededException
   */
  FulfilmentAssignment assign(Long storeId, Long productId, String warehouseBusinessUnitCode);
}
