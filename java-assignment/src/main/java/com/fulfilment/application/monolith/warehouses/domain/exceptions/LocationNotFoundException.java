package com.fulfilment.application.monolith.warehouses.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/** Raised for null, blank, or unknown location identifiers, consistently. */
public class LocationNotFoundException extends WarehouseDomainException {

  public LocationNotFoundException(String identifier) {
    super(
        DomainErrorType.NOT_FOUND,
        identifier == null || identifier.isBlank()
            ? "Location identifier must not be null or blank"
            : "No location found for identifier '" + identifier + "'");
  }
}
