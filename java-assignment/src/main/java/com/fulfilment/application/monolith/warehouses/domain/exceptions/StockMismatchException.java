package com.fulfilment.application.monolith.warehouses.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/** Raised when a replacement warehouse's stock does not exactly match the warehouse it replaces. */
public class StockMismatchException extends WarehouseDomainException {

  public StockMismatchException(int expectedStock, int actualStock) {
    super(
        DomainErrorType.CONFLICT,
        "Replacement stock ("
            + actualStock
            + ") must exactly match the stock of the warehouse being replaced ("
            + expectedStock
            + ")");
  }
}
