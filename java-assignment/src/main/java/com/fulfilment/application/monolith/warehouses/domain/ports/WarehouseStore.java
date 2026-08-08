package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence port for warehouses. Implementations must never expose archived rows through the "active" methods. */
public interface WarehouseStore {

  /** Every currently active (non-archived) warehouse. */
  List<Warehouse> getAll();

  /** The active warehouse for the given business unit code, or {@code null} if none is active. */
  Warehouse findActiveByBusinessUnitCode(String businessUnitCode);

  /**
   * Same as {@link #findActiveByBusinessUnitCode}, but takes a write lock on the row (if any) for
   * the rest of the current transaction. Use this - not the plain read - whenever the result will
   * drive a later archive/replace of that same row: it guarantees that if another transaction
   * concurrently archives/replaces the same business unit code first, this call observes that
   * outcome (returns {@code null} once the other transaction commits) instead of operating on a
   * row that is no longer the active one.
   */
  Warehouse lockActiveByBusinessUnitCode(String businessUnitCode);

  /**
   * Active warehouse count and aggregate active capacity at the given location, taken under a
   * transaction-scoped advisory lock keyed by the location identifier so concurrent create/replace
   * operations targeting the same location serialize instead of racing past each other's
   * count/capacity checks - including when the location currently has zero active warehouses,
   * where there would otherwise be no row to take a row-level lock on.
   */
  LocationUsage lockActiveUsageByLocation(String locationIdentifier);

  /** Inserts a brand new warehouse row. */
  void create(Warehouse warehouse);

  /**
   * Archives the active row for the given business unit code by setting its {@code archivedAt}.
   *
   * @return {@code true} if an active row was found and archived, {@code false} if none was active
   *     (e.g. a concurrent request archived it first).
   */
  boolean archiveActive(String businessUnitCode, LocalDateTime archivedAt);
}
