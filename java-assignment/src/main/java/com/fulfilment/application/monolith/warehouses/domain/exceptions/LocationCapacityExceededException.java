package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/**
 * Raised when the aggregate active capacity of a location (existing active warehouses plus the
 * requested one) would exceed {@code Location.maxCapacity}.
 */
public class LocationCapacityExceededException extends WarehouseDomainException {

  public LocationCapacityExceededException(String locationIdentifier, int maxCapacity) {
    super(
        DomainErrorType.CONFLICT,
        "Location '"
            + locationIdentifier
            + "' cannot accommodate the requested capacity, maximum aggregate capacity is "
            + maxCapacity);
  }
}
