package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

public class ArchiveWarehouseUseCaseTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void testArchivingAnActiveWarehouseSucceeds() {
    WarehouseStore warehouseStore = mock(WarehouseStore.class);
    when(warehouseStore.archiveActive(eq("MWH.001"), any())).thenReturn(true);
    ArchiveWarehouseUseCase useCase = new ArchiveWarehouseUseCase(warehouseStore, FIXED_CLOCK);

    useCase.archive("MWH.001");

    verify(warehouseStore, times(1)).archiveActive(eq("MWH.001"), any());
  }

  @Test
  void testArchivingAnUnknownWarehouseThrowsNotFound() {
    WarehouseStore warehouseStore = mock(WarehouseStore.class);
    when(warehouseStore.archiveActive(eq("UNKNOWN"), any())).thenReturn(false);
    ArchiveWarehouseUseCase useCase = new ArchiveWarehouseUseCase(warehouseStore, FIXED_CLOCK);

    assertThrows(WarehouseNotFoundException.class, () -> useCase.archive("UNKNOWN"));
  }

  @Test
  void testRepeatedArchiveRequestOnAlreadyArchivedWarehouseThrowsNotFound() {
    WarehouseStore warehouseStore = mock(WarehouseStore.class);
    // First call archives it (returns true), the row is no longer active afterwards.
    when(warehouseStore.archiveActive(eq("MWH.001"), any())).thenReturn(true).thenReturn(false);
    ArchiveWarehouseUseCase useCase = new ArchiveWarehouseUseCase(warehouseStore, FIXED_CLOCK);

    useCase.archive("MWH.001");
    assertThrows(WarehouseNotFoundException.class, () -> useCase.archive("MWH.001"));
  }
}
