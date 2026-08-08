package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

public class StoreNotFoundException extends FulfilmentDomainException {

  public StoreNotFoundException(Long storeId) {
    super(DomainErrorType.NOT_FOUND, "No store found for id " + storeId);
  }
}
