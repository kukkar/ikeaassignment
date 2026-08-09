package com.fulfilment.application.monolith.warehouses.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/**
 * Base type for every business-rule failure raised by the warehouse (and location) domain.
 * REST adapters map {@link #errorType()} to an HTTP status, keeping JAX-RS concerns out of the
 * domain layer.
 */
public abstract class WarehouseDomainException extends RuntimeException {

  private final DomainErrorType errorType;

  protected WarehouseDomainException(DomainErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public DomainErrorType errorType() {
    return errorType;
  }
}
