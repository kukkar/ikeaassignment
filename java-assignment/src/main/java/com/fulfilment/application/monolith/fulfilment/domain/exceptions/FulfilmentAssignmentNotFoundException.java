package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

public class FulfilmentAssignmentNotFoundException extends FulfilmentDomainException {

  public FulfilmentAssignmentNotFoundException(Long assignmentId, Long storeId) {
    super(
        DomainErrorType.NOT_FOUND,
        "No fulfilment assignment with id " + assignmentId + " found for store " + storeId);
  }
}
