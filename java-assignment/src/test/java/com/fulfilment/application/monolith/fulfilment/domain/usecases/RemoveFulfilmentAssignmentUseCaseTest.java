package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentAssignmentNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import org.junit.jupiter.api.Test;

public class RemoveFulfilmentAssignmentUseCaseTest {

  @Test
  void testRemovingAnExistingAssignmentSucceeds() {
    FulfilmentAssignmentStore store = mock(FulfilmentAssignmentStore.class);
    when(store.deleteByIdForStore(10L, 1L)).thenReturn(true);
    RemoveFulfilmentAssignmentUseCase useCase = new RemoveFulfilmentAssignmentUseCase(store);

    useCase.remove(1L, 10L);

    verify(store).deleteByIdForStore(10L, 1L);
  }

  @Test
  void testRemovingAnUnknownAssignmentThrowsNotFound() {
    FulfilmentAssignmentStore store = mock(FulfilmentAssignmentStore.class);
    when(store.deleteByIdForStore(999L, 1L)).thenReturn(false);
    RemoveFulfilmentAssignmentUseCase useCase = new RemoveFulfilmentAssignmentUseCase(store);

    assertThrows(FulfilmentAssignmentNotFoundException.class, () -> useCase.remove(1L, 999L));
  }

  @Test
  void testRemovingAnAssignmentBelongingToAnotherStoreThrowsNotFound() {
    // deleteByIdForStore scopes by (assignmentId, storeId) together, so an assignment id that
    // exists but under a different store must behave exactly like an unknown id.
    FulfilmentAssignmentStore store = mock(FulfilmentAssignmentStore.class);
    when(store.deleteByIdForStore(10L, 2L)).thenReturn(false);
    RemoveFulfilmentAssignmentUseCase useCase = new RemoveFulfilmentAssignmentUseCase(store);

    assertThrows(FulfilmentAssignmentNotFoundException.class, () -> useCase.remove(2L, 10L));
  }
}
