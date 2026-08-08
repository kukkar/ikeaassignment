package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Archiving is idempotent-by-outcome, not idempotent-by-response: the first call to archive an
 * active warehouse archives it (204). Because archiving looks up the row by "active for this
 * code" and there is at most one, a second call finds nothing active and returns 404 - the same
 * response as archiving a business unit code that never existed. This keeps "no active warehouse
 * for this code" a single, unambiguous outcome rather than requiring a caller to distinguish
 * "already archived" from "never existed".
 */
@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final Clock clock;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore, Clock clock) {
    this.warehouseStore = warehouseStore;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void archive(String businessUnitCode) {
    boolean archived = warehouseStore.archiveActive(businessUnitCode, LocalDateTime.now(clock));
    if (!archived) {
      throw new WarehouseNotFoundException(businessUnitCode);
    }
  }
}
