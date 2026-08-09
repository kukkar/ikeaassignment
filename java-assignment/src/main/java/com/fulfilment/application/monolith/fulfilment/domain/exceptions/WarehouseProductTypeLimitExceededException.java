package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/** Rule 3: a warehouse may store at most N distinct product types, across all stores. */
public class WarehouseProductTypeLimitExceededException extends FulfilmentDomainException {

  public WarehouseProductTypeLimitExceededException(String warehouseBusinessUnitCode, int max) {
    super(
        DomainErrorType.CONFLICT,
        "Warehouse '"
            + warehouseBusinessUnitCode
            + "' already stores the maximum of "
            + max
            + " distinct product type(s)");
  }
}
