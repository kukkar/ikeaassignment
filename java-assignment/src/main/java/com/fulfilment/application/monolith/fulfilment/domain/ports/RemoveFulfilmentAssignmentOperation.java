package com.fulfilment.application.monolith.fulfilment.domain.ports;

public interface RemoveFulfilmentAssignmentOperation {

  /**
   * @throws com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentAssignmentNotFoundException
   *     if no assignment with this id exists for this store
   */
  void remove(Long storeId, Long assignmentId);
}
