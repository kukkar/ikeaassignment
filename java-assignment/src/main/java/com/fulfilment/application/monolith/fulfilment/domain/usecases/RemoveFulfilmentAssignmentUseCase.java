package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentAssignmentNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import com.fulfilment.application.monolith.fulfilment.domain.ports.RemoveFulfilmentAssignmentOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Deletion is physical, not soft: an assignment row has no historical value once removed (unlike
 * a Warehouse, there is no replacement/versioning concept for an assignment - see README.md).
 * Removing an assignment can never violate any of the three limits (it only ever frees capacity),
 * so - unlike creation - no locking is needed here.
 */
@ApplicationScoped
public class RemoveFulfilmentAssignmentUseCase implements RemoveFulfilmentAssignmentOperation {

  private final FulfilmentAssignmentStore fulfilmentAssignmentStore;

  public RemoveFulfilmentAssignmentUseCase(FulfilmentAssignmentStore fulfilmentAssignmentStore) {
    this.fulfilmentAssignmentStore = fulfilmentAssignmentStore;
  }

  @Override
  @Transactional
  public void remove(Long storeId, Long assignmentId) {
    boolean deleted = fulfilmentAssignmentStore.deleteByIdForStore(assignmentId, storeId);
    if (!deleted) {
      throw new FulfilmentAssignmentNotFoundException(assignmentId, storeId);
    }
  }
}
