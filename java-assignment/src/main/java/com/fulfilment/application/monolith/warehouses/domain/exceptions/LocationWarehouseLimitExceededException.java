package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Raised when a location has already reached its maximum number of active warehouses. */
public class LocationWarehouseLimitExceededException extends WarehouseDomainException {

  public LocationWarehouseLimitExceededException(String locationIdentifier, int maxNumberOfWarehouses) {
    super(
        DomainErrorType.CONFLICT,
        "Location '"
            + locationIdentifier
            + "' has already reached its maximum of "
            + maxNumberOfWarehouses
            + " active warehouse(s)");
  }
}
