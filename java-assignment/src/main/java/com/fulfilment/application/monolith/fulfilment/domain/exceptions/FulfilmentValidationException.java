package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;
import java.util.Map;

/** Raised when the request data itself is malformed: null/non-positive ids, blank code, etc. */
public class FulfilmentValidationException extends FulfilmentDomainException {

  private final Map<String, String> violations;

  public FulfilmentValidationException(String message, Map<String, String> violations) {
    super(DomainErrorType.VALIDATION, message);
    this.violations = Map.copyOf(violations);
  }

  public Map<String, String> violations() {
    return violations;
  }
}
