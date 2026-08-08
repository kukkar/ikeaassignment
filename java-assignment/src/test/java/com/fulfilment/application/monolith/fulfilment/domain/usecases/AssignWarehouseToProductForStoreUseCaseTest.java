package com.fulfilment.application.monolith.fulfilment.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.fulfilment.domain.exceptions.DuplicateFulfilmentAssignmentException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.FulfilmentValidationException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.ProductWarehouseLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.StoreWarehouseLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.fulfilment.domain.exceptions.WarehouseProductTypeLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.domain.models.FulfilmentAssignment;
import com.fulfilment.application.monolith.fulfilment.domain.ports.CatalogGateway;
import com.fulfilment.application.monolith.fulfilment.domain.ports.FulfilmentAssignmentStore;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AssignWarehouseToProductForStoreUseCaseTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);
  private static final Long STORE_ID = 1L;
  private static final Long PRODUCT_ID = 100L;
  private static final String WAREHOUSE_CODE = "MWH.001";

  private FulfilmentAssignmentStore fulfilmentAssignmentStore;
  private CatalogGateway catalogGateway;
  private WarehouseStore warehouseStore;
  private AssignWarehouseToProductForStoreUseCase useCase;

  @BeforeEach
  void setUp() {
    fulfilmentAssignmentStore = mock(FulfilmentAssignmentStore.class);
    catalogGateway = mock(CatalogGateway.class);
    warehouseStore = mock(WarehouseStore.class);
    useCase =
        new AssignWarehouseToProductForStoreUseCase(
            fulfilmentAssignmentStore, catalogGateway, warehouseStore, FIXED_CLOCK);

    when(catalogGateway.storeExists(STORE_ID)).thenReturn(true);
    when(catalogGateway.productExists(PRODUCT_ID)).thenReturn(true);
    when(warehouseStore.findActiveByBusinessUnitCode(WAREHOUSE_CODE)).thenReturn(new Warehouse());
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of());
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID)).thenReturn(Set.of());
    when(fulfilmentAssignmentStore.distinctProductsForWarehouse(WAREHOUSE_CODE)).thenReturn(Set.of());
  }

  @Test
  void testValidAssignmentPersistsAndStampsCreatedAt() {
    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
    verify(fulfilmentAssignmentStore).acquireAssignmentLocks(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);
    assertEquals(STORE_ID, created.storeId);
    assertEquals(PRODUCT_ID, created.productId);
    assertEquals(WAREHOUSE_CODE, created.warehouseBusinessUnitCode);
    assertTrue(created.warehouseActive);
    assertEquals(FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(), created.createdAt);
  }

  @Test
  void testStoreNotFoundIsRejected() {
    when(catalogGateway.storeExists(STORE_ID)).thenReturn(false);

    assertThrows(
        StoreNotFoundException.class, () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
    verify(fulfilmentAssignmentStore, never()).acquireAssignmentLocks(any(), any(), any());
  }

  @Test
  void testProductNotFoundIsRejected() {
    when(catalogGateway.productExists(PRODUCT_ID)).thenReturn(false);

    assertThrows(
        ProductNotFoundException.class, () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testUnknownWarehouseCodeIsRejected() {
    when(warehouseStore.findActiveByBusinessUnitCode(WAREHOUSE_CODE)).thenReturn(null);

    assertThrows(
        WarehouseNotFoundException.class, () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testArchivedOnlyWarehouseCodeIsRejectedTheSameWayAsUnknown() {
    // WarehouseStore.findActiveByBusinessUnitCode already returns null once every row for a code
    // is archived - the use case doesn't need to (and can't) distinguish "never existed" from
    // "archived with no active replacement".
    when(warehouseStore.findActiveByBusinessUnitCode(WAREHOUSE_CODE)).thenReturn(null);

    assertThrows(
        WarehouseNotFoundException.class, () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
  }

  @Test
  void testExactDuplicateAssignmentIsRejected() {
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of(WAREHOUSE_CODE));

    assertThrows(
        DuplicateFulfilmentAssignmentException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testNullOrNonPositiveIdsAndBlankCodeAreRejected() {
    FulfilmentValidationException exception =
        assertThrows(
            FulfilmentValidationException.class, () -> useCase.assign(null, 0L, "  "));
    assertEquals(3, exception.violations().size());
    verify(catalogGateway, never()).storeExists(any());
  }

  @Test
  void testNegativeStoreOrProductIdIsRejected() {
    assertThrows(
        FulfilmentValidationException.class, () -> useCase.assign(-1L, PRODUCT_ID, WAREHOUSE_CODE));
  }

  // --- Rule 1: max 2 distinct warehouses per product per store ---

  @Test
  void testSecondDistinctWarehouseForSameProductIsAllowed() {
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of("MWH.OTHER"));

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }

  @Test
  void testThirdDistinctWarehouseForSameProductIsRejected() {
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B"));

    assertThrows(
        ProductWarehouseLimitExceededException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  // --- Rule 2: max 3 distinct warehouses per store, across all products ---

  @Test
  void testFourthDistinctWarehouseForStoreIsRejected() {
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B", "MWH.C"));

    assertThrows(
        StoreWarehouseLimitExceededException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testAssigningAnotherProductToAnExistingStoreWarehouseIsAllowedEvenAtTheLimit() {
    // The store already has 3 distinct warehouses, including WAREHOUSE_CODE itself - assigning a
    // *different* product to that same warehouse must not be blocked by rule 2.
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID))
        .thenReturn(Set.of(WAREHOUSE_CODE, "MWH.B", "MWH.C"));

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }

  // --- Rule 3: max 5 distinct product types per warehouse, across all stores ---

  @Test
  void testSixthDistinctProductForWarehouseIsRejected() {
    when(fulfilmentAssignmentStore.distinctProductsForWarehouse(WAREHOUSE_CODE))
        .thenReturn(Set.of(1L, 2L, 3L, 4L, 5L));

    assertThrows(
        WarehouseProductTypeLimitExceededException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testAssigningAnAlreadySupportedProductToAnotherStoreIsAllowedEvenAtTheLimit() {
    // The warehouse already stores 5 distinct product types, including PRODUCT_ID itself -
    // assigning that same product to a *different* store must not be blocked by rule 3.
    when(fulfilmentAssignmentStore.distinctProductsForWarehouse(WAREHOUSE_CODE))
        .thenReturn(Set.of(PRODUCT_ID, 2L, 3L, 4L, 5L));

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }
}
