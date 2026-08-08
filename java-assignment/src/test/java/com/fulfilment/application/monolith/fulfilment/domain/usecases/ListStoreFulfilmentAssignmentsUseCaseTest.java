package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.CatalogGateway;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ListStoreFulfilmentAssignmentsUseCaseTest {

  private static final Long STORE_ID = 1L;

  private FulfilmentAssignmentStore fulfilmentAssignmentStore;
  private CatalogGateway catalogGateway;
  private WarehouseStore warehouseStore;
  private ListStoreFulfilmentAssignmentsUseCase useCase;

  @BeforeEach
  void setUp() {
    fulfilmentAssignmentStore = mock(FulfilmentAssignmentStore.class);
    catalogGateway = mock(CatalogGateway.class);
    warehouseStore = mock(WarehouseStore.class);
    useCase =
        new ListStoreFulfilmentAssignmentsUseCase(fulfilmentAssignmentStore, catalogGateway, warehouseStore);
    when(catalogGateway.storeExists(STORE_ID)).thenReturn(true);
    when(catalogGateway.productExists(100L)).thenReturn(true);
  }

  private FulfilmentAssignment assignment(String code) {
    FulfilmentAssignment assignment = new FulfilmentAssignment();
    assignment.id = 1L;
    assignment.storeId = STORE_ID;
    assignment.productId = 100L;
    assignment.warehouseBusinessUnitCode = code;
    return assignment;
  }

  @Test
  void testListForUnknownStoreThrows() {
    when(catalogGateway.storeExists(STORE_ID)).thenReturn(false);
    assertThrows(StoreNotFoundException.class, () -> useCase.listForStore(STORE_ID));
  }

  @Test
  void testListForStoreAndUnknownProductThrows() {
    when(catalogGateway.productExists(999L)).thenReturn(false);
    assertThrows(
        ProductNotFoundException.class, () -> useCase.listForStoreAndProduct(STORE_ID, 999L));
  }

  @Test
  void testActiveWarehouseIsFlaggedActive() {
    when(fulfilmentAssignmentStore.listByStore(STORE_ID)).thenReturn(List.of(assignment("MWH.001")));
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());

    List<FulfilmentAssignment> result = useCase.listForStore(STORE_ID);

    assertTrue(result.get(0).warehouseActive);
  }

  @Test
  void testArchivedOnlyWarehouseIsFlaggedInactiveButRowIsStillReturned() {
    when(fulfilmentAssignmentStore.listByStore(STORE_ID)).thenReturn(List.of(assignment("MWH.ARCHIVED")));
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.ARCHIVED")).thenReturn(null);

    List<FulfilmentAssignment> result = useCase.listForStore(STORE_ID);

    assertEquals(1, result.size());
    assertFalse(result.get(0).warehouseActive);
  }

  @Test
  void testWarehouseActiveLookupIsCachedPerDistinctCode() {
    FulfilmentAssignment first = assignment("MWH.001");
    FulfilmentAssignment second = assignment("MWH.001");
    second.productId = 200L;
    when(fulfilmentAssignmentStore.listByStore(STORE_ID)).thenReturn(List.of(first, second));
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.001")).thenReturn(new Warehouse());

    useCase.listForStore(STORE_ID);

    verify(warehouseStore, times(1)).findActiveByBusinessUnitCode("MWH.001");
  }
}
