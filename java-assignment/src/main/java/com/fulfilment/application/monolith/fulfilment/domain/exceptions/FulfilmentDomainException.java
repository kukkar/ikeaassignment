package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

/**
 * Base type for every business-rule failure raised by the fulfilment-assignment domain. Reuses
 * {@link DomainErrorType} from the warehouse domain rather than cloning it: the enum
 * (VALIDATION/NOT_FOUND/CONFLICT) is generic, not warehouse-specific, and {@link
 * com.fulfilment.application.monolith.warehouses.adapters.restapi.ApiError} - the response body
 * shape this maps to - is reused the same way, keeping one stable error contract across the API.
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
