package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.InsufficientCapacityException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationCapacityExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationWarehouseLimitExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.StockMismatchException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReplaceWarehouseUseCaseTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);

  private WarehouseStore warehouseStore;
  private LocationResolver locationResolver;
  private ReplaceWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    locationResolver = mock(LocationResolver.class);
    useCase = new ReplaceWarehouseUseCase(warehouseStore, locationResolver, FIXED_CLOCK);
  }

  private Warehouse currentlyActive() {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.001";
    warehouse.location = "ZWOLLE-001";
    warehouse.capacity = 40;
    warehouse.stock = 10;
    return warehouse;
  }

  private Warehouse replacementRequest() {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.001";
    warehouse.location = "ZWOLLE-001";
    warehouse.capacity = 40;
    warehouse.stock = 10;
    return warehouse;
  }

  @Test
  void testValidReplacementArchivesOldAndCreatesNewInSameLocation() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(1, 40));
    when(warehouseStore.archiveActive(eq("MWH.001"), any())).thenReturn(true);

    Warehouse replaced = useCase.replace("MWH.001", replacementRequest());

    verify(warehouseStore).archiveActive(eq("MWH.001"), any());
    verify(warehouseStore).create(replaced);
    assertEquals("MWH.001", replaced.businessUnitCode);
    assertEquals(FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(), replaced.createdAt);
    assertEquals(null, replaced.archivedAt);
  }

  @Test
  void testStockMismatchIsRejected() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));

    Warehouse request = replacementRequest();
    request.stock = 999;

    assertThrows(StockMismatchException.class, () -> useCase.replace("MWH.001", request));
    verify(warehouseStore, never()).create(any());
    verify(warehouseStore, never()).archiveActive(any(), any());
  }

  @Test
  void testInsufficientNewCapacityIsRejected() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));

    Warehouse request = replacementRequest();
    request.capacity = 5; // old stock is 10, cannot fit

    assertThrows(InsufficientCapacityException.class, () -> useCase.replace("MWH.001", request));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testInvalidLocationIsRejected() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("NOWHERE")).thenThrow(new LocationNotFoundException("NOWHERE"));

    Warehouse request = replacementRequest();
    request.location = "NOWHERE";

    assertThrows(LocationNotFoundException.class, () -> useCase.replace("MWH.001", request));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testLocationWarehouseCountLimitExcludesTheWarehouseBeingReplaced() {
    // Same location, count limit is 1, and the only active warehouse there IS the one being
    // replaced - so after excluding it the count is 0 and replacement should be allowed.
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(1, 40));
    when(warehouseStore.archiveActive(eq("MWH.001"), any())).thenReturn(true);

    Warehouse replaced = useCase.replace("MWH.001", replacementRequest());

    assertEquals("MWH.001", replaced.businessUnitCode);
  }

  @Test
  void testLocationCapacityLimitIsEnforcedAfterExcludingReplacedWarehouse() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 5, 40));
    // Another active warehouse (not the one being replaced) already uses 20 capacity.
    // Total including current (40) is 60; excluding current is 20. New capacity 40 => 20+40=60 > 40.
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(2, 60));

    assertThrows(
        LocationCapacityExceededException.class, () -> useCase.replace("MWH.001", replacementRequest()));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testLocationWarehouseCountLimitIsEnforcedWhenMovingToADifferentLocation() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("TILBURG-001")).thenReturn(new Location("TILBURG-001", 1, 40));
    // TILBURG-001 already has 1 active warehouse (not the one being replaced, different location).
    when(warehouseStore.lockActiveUsageByLocation("TILBURG-001")).thenReturn(new LocationUsage(1, 30));

    Warehouse request = replacementRequest();
    request.location = "TILBURG-001";

    assertThrows(
        LocationWarehouseLimitExceededException.class, () -> useCase.replace("MWH.001", request));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testMissingActiveWarehouseIsRejected() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.999")).thenReturn(null);

    assertThrows(
        WarehouseNotFoundException.class, () -> useCase.replace("MWH.999", replacementRequest()));
    verify(warehouseStore, never()).create(any());
    verify(warehouseStore, never()).archiveActive(any(), any());
  }

  @Test
  void testReplacementMustReuseTheOldBusinessUnitCode() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());

    Warehouse request = replacementRequest();
    request.businessUnitCode = "MWH.999";

    assertThrows(
        com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException.class,
        () -> useCase.replace("MWH.001", request));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testExceptionFromCreatePropagatesSoTheTransactionRollsBack() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(1, 40));
    when(warehouseStore.archiveActive(eq("MWH.001"), any())).thenReturn(true);
    org.mockito.Mockito.doThrow(new RuntimeException("insert failed"))
        .when(warehouseStore)
        .create(any());

    assertThrows(RuntimeException.class, () -> useCase.replace("MWH.001", replacementRequest()));
    // archive was still attempted - since both calls run inside one @Transactional method, a
    // failure here rolls back the archive too; that guarantee is exercised end-to-end by
    // WarehouseEndpointIT, this test only proves the failure is not swallowed here.
    verify(warehouseStore).archiveActive(eq("MWH.001"), any());
  }

  @Test
  void testLostRaceOnArchiveDuringReplacementIsReportedAsNotFound() {
    when(warehouseStore.lockActiveByBusinessUnitCode("MWH.001")).thenReturn(currentlyActive());
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(1, 40));
    when(warehouseStore.archiveActive(eq("MWH.001"), any())).thenReturn(false);

    assertThrows(WarehouseNotFoundException.class, () -> useCase.replace("MWH.001", replacementRequest()));
    verify(warehouseStore, never()).create(any());
  }
}
