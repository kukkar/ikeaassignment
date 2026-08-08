package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DomainErrorType;

public class ProductNotFoundException extends FulfilmentDomainException {

  public ProductNotFoundException(Long productId) {
    super(DomainErrorType.NOT_FOUND, "No product found for id " + productId);
  }
}
