package com.fulfilment.application.monolith.warehouses.domain.exceptions;

import java.util.Collections;
import java.util.Map;

/** Raised when the request data itself is malformed: null/blank fields, non-positive capacity, etc. */
public class WarehouseValidationException extends WarehouseDomainException {

  private final Map<String, String> violations;

  public WarehouseValidationException(String message) {
    this(message, Collections.emptyMap());
  }

  public WarehouseValidationException(String message, Map<String, String> violations) {
    super(DomainErrorType.VALIDATION, message);
    this.violations = Map.copyOf(violations);
  }

  public Map<String, String> violations() {
    return violations;
  }
}
