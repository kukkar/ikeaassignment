package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

/** Rule 2: a store may be fulfilled by at most N distinct warehouses in total. */
public class StoreWarehouseLimitExceededException extends FulfilmentDomainException {

  public StoreWarehouseLimitExceededException(Long storeId, int max) {
    super(
        DomainErrorType.CONFLICT,
        "Store "
            + storeId
            + " is already fulfilled by the maximum of "
            + max
            + " distinct warehouse(s)");
  }
}
