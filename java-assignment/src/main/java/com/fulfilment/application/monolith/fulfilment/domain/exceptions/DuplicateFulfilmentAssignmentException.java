package com.fulfilment.application.monolith.fulfilment.domain.exceptions;

import com.fulfilment.application.monolith.common.DomainErrorType;

/** Raised when the exact same Store + Product + Warehouse assignment already exists. */
public class DuplicateFulfilmentAssignmentException extends FulfilmentDomainException {

  public DuplicateFulfilmentAssignmentException(
      Long storeId, Long productId, String warehouseBusinessUnitCode) {
    super(
        DomainErrorType.CONFLICT,
        "A fulfilment assignment already exists for store "
            + storeId
            + ", product "
            + productId
            + ", warehouse '"
            + warehouseBusinessUnitCode
            + "'");
  }
}
