package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

/** Rule 1: a product may be fulfilled by at most N distinct warehouses per store. */
public class ProductWarehouseLimitExceededException extends FulfilmentDomainException {

  public ProductWarehouseLimitExceededException(Long storeId, Long productId, int max) {
    super(
        DomainErrorType.CONFLICT,
        "Product "
            + productId
            + " at store "
            + storeId
            + " is already fulfilled by the maximum of "
            + max
            + " distinct warehouse(s)");
  }
}
