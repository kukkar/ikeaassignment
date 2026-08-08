package com.fulfilment.application.monolith.fulfilment.domain.ports;

import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import java.util.List;

public interface ListStoreFulfilmentAssignmentsOperation {

  /** @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException */
  List<FulfilmentAssignment> listForStore(Long storeId);

  /**
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductNotFoundException
   */
  List<FulfilmentAssignment> listForStoreAndProduct(Long storeId, Long productId);
}
