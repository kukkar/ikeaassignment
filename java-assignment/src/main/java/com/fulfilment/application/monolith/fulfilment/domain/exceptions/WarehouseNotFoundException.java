package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

/**
 * Raised when {@code warehouseBusinessUnitCode} does not resolve to a currently active warehouse
 * - either the code is unknown, or every row for it has been archived. Deliberately distinct from
 * {@link com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException}
 * so the fulfilment domain's error handling doesn't depend on the warehouse domain's exception
 * type, only on its {@code WarehouseStore} port.
 */
public class WarehouseNotFoundException extends FulfilmentDomainException {

  public WarehouseNotFoundException(String businessUnitCode) {
    super(
        DomainErrorType.NOT_FOUND,
        "No active warehouse found for business unit code '" + businessUnitCode + "'");
  }
}
