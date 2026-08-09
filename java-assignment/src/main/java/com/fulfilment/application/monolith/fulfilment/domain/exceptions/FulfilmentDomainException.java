package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/**
 * Base type for every business-rule failure raised by the fulfilment-assignment domain. Uses the
 * shared {@link DomainErrorType} (owned by {@code common}, not by any single module) rather than
 * cloning it, and maps to the shared {@code common.ApiError} response body the same way - so the
 * fulfilment domain depends on generic, module-neutral infrastructure, not on the Warehouse
 * module's own packages, for a concept that was never warehouse-specific to begin with.
 */
public abstract class FulfilmentDomainException extends RuntimeException {

  private final DomainErrorType errorType;

  protected FulfilmentDomainException(DomainErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public DomainErrorType errorType() {
    return errorType;
  }
}
