package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Raised when no active warehouse exists for the given business unit code. */
public class WarehouseNotFoundException extends WarehouseDomainException {

  public WarehouseNotFoundException(String businessUnitCode) {
    super(
        DomainErrorType.NOT_FOUND,
        "No active warehouse found for business unit code '" + businessUnitCode + "'");
  }
}
