package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.InsufficientCapacityException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationCapacityExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.LocationWarehouseLimitExceededException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.StockMismatchException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.LocationUsage;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Replacement is one atomic business operation - archive the old row, insert the new one - not an
 * in-place overwrite. Both persistence calls happen inside this method's single transaction, so a
 * failure at any step (including the insert itself, e.g. a partial-unique-index violation from a
 * concurrent replacement) rolls back the archive too.
 */
@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;
  private final Clock clock;

  public ReplaceWarehouseUseCase(
      WarehouseStore warehouseStore, LocationResolver locationResolver, Clock clock) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Warehouse replace(String businessUnitCode, Warehouse newWarehouseData) {
    // Locked (not a plain read): holds a row lock for the rest of this transaction so a
    // concurrent replace/archive of the same business unit code can't silently operate on a row
    // that is no longer the one this call validated against - see
    // WarehouseRepository.lockActiveByBusinessUnitCode.
    Warehouse currentlyActive = warehouseStore.lockActiveByBusinessUnitCode(businessUnitCode);
    if (currentlyActive == null) {
      throw new WarehouseNotFoundException(businessUnitCode);
    }

    Warehouse candidate = buildCandidate(businessUnitCode, newWarehouseData);
    WarehouseValidator.validateShape(candidate);

    Location location = locationResolver.resolveByIdentifier(candidate.location);

    if (!candidate.stock.equals(currentlyActive.stock)) {
      throw new StockMismatchException(currentlyActive.stock, candidate.stock);
    }

    if (candidate.capacity < currentlyActive.stock) {
      throw new InsufficientCapacityException(candidate.capacity, currentlyActive.stock);
    }

    LocationUsage usage = warehouseStore.lockActiveUsageByLocation(location.identification);
    boolean replacingInSameLocation = location.identification.equals(currentlyActive.location);
    long activeCountExcludingCurrent =
        usage.activeWarehouseCount() - (replacingInSameLocation ? 1 : 0);
    int activeCapacityExcludingCurrent =
        usage.activeCapacity() - (replacingInSameLocation ? currentlyActive.capacity : 0);

    if (activeCountExcludingCurrent + 1 > location.maxNumberOfWarehouses) {
      throw new LocationWarehouseLimitExceededException(
          location.identification, location.maxNumberOfWarehouses);
    }

    if (activeCapacityExcludingCurrent + candidate.capacity > location.maxCapacity) {
      throw new LocationCapacityExceededException(location.identification, location.maxCapacity);
    }

    LocalDateTime now = LocalDateTime.now(clock);

    boolean archived = warehouseStore.archiveActive(businessUnitCode, now);
    if (!archived) {
      // Lost a race with another request archiving/replacing the same warehouse concurrently.
      throw new WarehouseNotFoundException(businessUnitCode);
    }

    candidate.createdAt = now;
    candidate.archivedAt = null;
    warehouseStore.create(candidate);

    return candidate;
  }

  private static Warehouse buildCandidate(String businessUnitCode, Warehouse requested) {
    if (requested.businessUnitCode != null
        && !requested.businessUnitCode.isBlank()
        && !requested.businessUnitCode.equals(businessUnitCode)) {
      throw new WarehouseValidationException(
          "A replacement warehouse must reuse the business unit code '"
              + businessUnitCode
              + "' of the warehouse it replaces");
    }

    Warehouse candidate = new Warehouse();
    candidate.businessUnitCode = businessUnitCode;
    candidate.location = requested.location;
    candidate.capacity = requested.capacity;
    candidate.stock = requested.stock;
    return candidate;
  }
}
