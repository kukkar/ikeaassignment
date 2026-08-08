package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Raised when a replacement warehouse's capacity cannot accommodate the transferred stock. */
public class InsufficientCapacityException extends WarehouseDomainException {

  public InsufficientCapacityException(int capacity, int stockToAccommodate) {
    super(
        DomainErrorType.CONFLICT,
        "Capacity ("
            + capacity
            + ") cannot accommodate the transferred stock ("
            + stockToAccommodate
            + ")");
  }
}
