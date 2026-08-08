package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Raised when trying to create a warehouse with a business unit code that is already active. */
public class DuplicateBusinessUnitCodeException extends WarehouseDomainException {

  public DuplicateBusinessUnitCodeException(String businessUnitCode) {
    super(
        DomainErrorType.CONFLICT,
        "An active warehouse already exists for business unit code '" + businessUnitCode + "'");
  }
}
