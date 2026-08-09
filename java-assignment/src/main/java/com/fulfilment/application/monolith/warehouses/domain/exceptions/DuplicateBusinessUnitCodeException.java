package com.fulfilment.application.monolith.warehouses.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/** Raised when trying to create a warehouse with a business unit code that is already active. */
public class DuplicateBusinessUnitCodeException extends WarehouseDomainException {

  public DuplicateBusinessUnitCodeException(String businessUnitCode) {
    super(
        DomainErrorType.CONFLICT,
        "An active warehouse already exists for business unit code '" + businessUnitCode + "'");
  }
}
