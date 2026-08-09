package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.DuplicateBusinessUnitCodeException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationCapacityExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationWarehouseLimitExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
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
import org.mockito.InOrder;

public class CreateWarehouseUseCaseTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);

  private WarehouseStore warehouseStore;
  private LocationResolver locationResolver;
  private CreateWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    warehouseStore = mock(WarehouseStore.class);
    locationResolver = mock(LocationResolver.class);
    useCase = new CreateWarehouseUseCase(warehouseStore, locationResolver, FIXED_CLOCK);
  }

  private Warehouse validWarehouse() {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.100";
    warehouse.location = "ZWOLLE-001";
    warehouse.capacity = 20;
    warehouse.stock = 10;
    return warehouse;
  }

  @Test
  void testValidCreationPersistsAndStampsTimestamps() {
    Warehouse warehouse = validWarehouse();
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.100")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 2, 40));
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(0, 0));

    useCase.create(warehouse);

    verify(warehouseStore).create(warehouse);
    assertEquals(FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(), warehouse.createdAt);
    assertNull(warehouse.archivedAt);
  }

  /**
   * Reusing an archived-only code reactivates it, which can race fulfilment's active-only limit
   * counting for that same code (see AssignWarehouseToProductForStoreUseCase's Javadoc,
   * "Concurrency: warehouse reactivation race") - closed by taking this lock before the duplicate
   * check and insert, not after.
   */
  @Test
  void testActivationLockIsTakenBeforeTheDuplicateCheckAndInsert() {
    Warehouse warehouse = validWarehouse();
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.100")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 2, 40));
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(0, 0));

    useCase.create(warehouse);

    InOrder order = inOrder(warehouseStore);
    order.verify(warehouseStore).lockForActivation("MWH.100");
    order.verify(warehouseStore).findActiveByBusinessUnitCode("MWH.100");
    order.verify(warehouseStore).create(warehouse);
  }

  @Test
  void testDuplicateActiveBusinessUnitCodeIsRejected() {
    Warehouse warehouse = validWarehouse();
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.100")).thenReturn(validWarehouse());

    assertThrows(DuplicateBusinessUnitCodeException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testInvalidLocationIsRejected() {
    Warehouse warehouse = validWarehouse();
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.100")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001"))
        .thenThrow(new LocationNotFoundException("ZWOLLE-001"));

    assertThrows(LocationNotFoundException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testWarehouseCountLimitAtLocationIsEnforced() {
    Warehouse warehouse = validWarehouse();
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.100")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 1, 40));
    // one active warehouse already, and max is 1
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(1, 10));

    assertThrows(LocationWarehouseLimitExceededException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testAggregateCapacityLimitAtLocationIsEnforced() {
    Warehouse warehouse = validWarehouse(); // capacity = 20
    when(warehouseStore.findActiveByBusinessUnitCode("MWH.100")).thenReturn(null);
    when(locationResolver.resolveByIdentifier("ZWOLLE-001")).thenReturn(new Location("ZWOLLE-001", 5, 30));
    // 25 used already; 25 + 20 = 45 > 30
    when(warehouseStore.lockActiveUsageByLocation("ZWOLLE-001")).thenReturn(new LocationUsage(1, 25));

    assertThrows(LocationCapacityExceededException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testStockGreaterThanCapacityIsRejected() {
    Warehouse warehouse = validWarehouse();
    warehouse.stock = 999;

    assertThrows(WarehouseValidationException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testNullBusinessUnitCodeLocationCapacityStockAreRejected() {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = null;
    warehouse.location = null;
    warehouse.capacity = null;
    warehouse.stock = null;

    WarehouseValidationException exception =
        assertThrows(WarehouseValidationException.class, () -> useCase.create(warehouse));
    assertEquals(4, exception.violations().size());
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testZeroCapacityIsRejected() {
    Warehouse warehouse = validWarehouse();
    warehouse.capacity = 0;

    assertThrows(WarehouseValidationException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testNegativeCapacityAndStockAreRejected() {
    Warehouse warehouse = validWarehouse();
    warehouse.capacity = -5;
    warehouse.stock = -1;

    assertThrows(WarehouseValidationException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }

  @Test
  void testBlankBusinessUnitCodeAndLocationAreRejected() {
    Warehouse warehouse = validWarehouse();
    warehouse.businessUnitCode = "   ";
    warehouse.location = "";

    assertThrows(WarehouseValidationException.class, () -> useCase.create(warehouse));
    verify(warehouseStore, never()).create(any());
  }
}
