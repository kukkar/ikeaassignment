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
    when(warehouseStore.lockActiveByBusinessUnitCode(WAREHOUSE_CODE)).thenReturn(new Warehouse());
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of());
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID)).thenReturn(Set.of());
    when(fulfilmentAssignmentStore.distinctProductsForWarehouse(WAREHOUSE_CODE)).thenReturn(Set.of());
  }

  /** By default, an unstubbed {@code findActiveByBusinessUnitCode} returns null (Mockito's
   * default), i.e. "archived" - so every warehouse code a test wants counted as operationally
   * active toward rules 1/2 must be stubbed active explicitly via this helper. */
  private void stubActive(String... warehouseBusinessUnitCodes) {
    for (String code : warehouseBusinessUnitCodes) {
      when(warehouseStore.findActiveByBusinessUnitCode(code)).thenReturn(new Warehouse());
    }
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
    when(warehouseStore.lockActiveByBusinessUnitCode(WAREHOUSE_CODE)).thenReturn(null);

    assertThrows(
        WarehouseNotFoundException.class, () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testArchivedOnlyWarehouseCodeIsRejectedTheSameWayAsUnknown() {
    // WarehouseStore.lockActiveByBusinessUnitCode already returns null once every row for a code
    // is archived - the use case doesn't need to (and can't) distinguish "never existed" from
    // "archived with no active replacement".
    when(warehouseStore.lockActiveByBusinessUnitCode(WAREHOUSE_CODE)).thenReturn(null);

    assertThrows(
        WarehouseNotFoundException.class, () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
  }

  @Test
  void testActiveWarehouseCheckUsesTheLockingReadNotThePlainOne() {
    // Regression test for the active-warehouse race: a plain read (WarehouseStore.
    // findActiveByBusinessUnitCode) holds no lock, so nothing would stop a concurrent archive/
    // replace from committing between this check and the eventual insert. The locking variant
    // (SELECT ... FOR UPDATE) is what ReplaceWarehouseUseCase already uses to protect itself from
    // the same kind of race against a concurrent archive of the same warehouse row - this use case
    // must use it too.
    useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(warehouseStore).lockActiveByBusinessUnitCode(WAREHOUSE_CODE);
  }

  @Test
  void testActiveOnlyFilteringTakesTheActivationLockBeforeReadingEachHistoricalCode() {
    // Regression test for the warehouse reactivation race: a plain read of "is MWH.A active" holds
    // no lock, so nothing would stop a concurrent CreateWarehouseUseCase from reactivating MWH.A
    // (inserting a brand new active row for a code that currently has none) between this read and
    // this transaction's commit - which would let the limit be exceeded once both commit. See
    // WarehouseStore#lockForActivation and the use case's class Javadoc.
    stubActive("MWH.B");
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B")); // MWH.A left unstubbed -> archived

    useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(warehouseStore).lockForActivation("MWH.A");
    verify(warehouseStore).lockForActivation("MWH.B");
  }

  @Test
  void testExactDuplicateAssignmentIsRejected() {
    when(fulfilmentAssignmentStore.existsExact(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE)).thenReturn(true);

    assertThrows(
        DuplicateFulfilmentAssignmentException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testDuplicateDetectionIgnoresWarehouseActiveStatus() {
    // existsExact must catch a repeat of the exact triple regardless of the referenced warehouse's
    // current status - the unique constraint doesn't care either, so this check must match it, or
    // a duplicate against a historical (now-archived-code) row would hit a raw constraint
    // violation instead of a clean 409. Simulated here simply by existsExact returning true
    // without any corresponding "active" stub - proving the duplicate check never even consults
    // warehouse activeness.
    when(fulfilmentAssignmentStore.existsExact(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE)).thenReturn(true);

    assertThrows(
        DuplicateFulfilmentAssignmentException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
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

  // --- Rule 1: max 2 distinct *active* warehouses per product per store ---

  @Test
  void testSecondDistinctWarehouseForSameProductIsAllowed() {
    stubActive("MWH.OTHER");
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of("MWH.OTHER"));

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }

  @Test
  void testThirdDistinctActiveWarehouseForSameProductIsRejected() {
    stubActive("MWH.A", "MWH.B");
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B"));

    assertThrows(
        ProductWarehouseLimitExceededException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testArchivedWarehouseInTheSetDoesNotCountTowardTheProductLimit() {
    // Problem 3 fix: MWH.A is referenced by a preserved historical assignment row but its
    // warehouse is now archived-only (findActiveByBusinessUnitCode returns null, the default for
    // an unstubbed code here) - it must not occupy one of product 100's 2 warehouse slots at
    // store 1, so a second genuinely active warehouse must still be accepted.
    stubActive("MWH.B");
    when(fulfilmentAssignmentStore.distinctWarehousesForStoreAndProduct(STORE_ID, PRODUCT_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B")); // MWH.A left unstubbed -> archived

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }

  // --- Rule 2: max 3 distinct *active* warehouses per store, across all products ---

  @Test
  void testFourthDistinctActiveWarehouseForStoreIsRejected() {
    stubActive("MWH.A", "MWH.B", "MWH.C");
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B", "MWH.C"));

    assertThrows(
        StoreWarehouseLimitExceededException.class,
        () -> useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE));
    verify(fulfilmentAssignmentStore, never()).create(any());
  }

  @Test
  void testAssigningAnotherProductToAnExistingStoreWarehouseIsAllowedEvenAtTheLimit() {
    // The store already has 3 distinct *active* warehouses, including WAREHOUSE_CODE itself -
    // assigning a *different* product to that same warehouse must not be blocked by rule 2.
    stubActive("MWH.B", "MWH.C");
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID))
        .thenReturn(Set.of(WAREHOUSE_CODE, "MWH.B", "MWH.C"));

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }

  @Test
  void testArchivedWarehouseInTheSetDoesNotCountTowardTheStoreLimit() {
    // Problem 3 fix: the store has 3 rows referencing distinct codes, but one (MWH.C) is now
    // archived-only - only 2 are operationally active, so a genuinely new 3rd active warehouse
    // (WAREHOUSE_CODE) must be accepted, not rejected as if the store were already at its limit.
    stubActive("MWH.A", "MWH.B");
    when(fulfilmentAssignmentStore.distinctWarehousesForStore(STORE_ID))
        .thenReturn(Set.of("MWH.A", "MWH.B", "MWH.C")); // MWH.C left unstubbed -> archived

    FulfilmentAssignment created = useCase.assign(STORE_ID, PRODUCT_ID, WAREHOUSE_CODE);

    verify(fulfilmentAssignmentStore).create(created);
  }

  // --- Rule 3: max 5 distinct product types per warehouse, across all stores - scoped by
  // business-unit-code identity, deliberately NOT filtered by current warehouse active status
  // (see the use case's class Javadoc) ---

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
